"""Canonicalization, semantic validation and A2UI compilation."""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from copy import deepcopy
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple
from urllib.parse import urlparse

from jsonschema import Draft202012Validator

from .catalog import (
    ASSISTANT_CATALOG_ID,
    BASIC_CATALOG_ID,
    COMPONENT_BY_NAME,
    FUNCTIONS,
)
from .schema import build_blueprint_schema


BLUEPRINT_SCHEMA = build_blueprint_schema()
SCHEMA_VALIDATOR = Draft202012Validator(BLUEPRINT_SCHEMA)
MAX_COMPONENT_DEPTH = 8
ALLOWED_DATASET_URL_HOSTS = {
    "assets.example.invalid",
    "example.invalid",
    "commons.wikimedia.org",
    "upload.wikimedia.org",
}
FORBIDDEN_LITERAL = re.compile(
    r"(?:<\s*(?:script|iframe|object)\b|javascript:|\bandroid\.[A-Za-z]|\bjava\.[A-Za-z])",
    re.IGNORECASE,
)
UNKNOWN_TEMPLATE_CONTEXT = object()


@dataclass(frozen=True)
class Diagnostic:
    code: str
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.code} at {self.path}: {self.message}"


class BlueprintValidationError(ValueError):
    def __init__(self, diagnostics: Sequence[Diagnostic]):
        self.diagnostics = tuple(diagnostics)
        super().__init__("; ".join(str(item) for item in self.diagnostics))


def _json_path(parts: Iterable[Any]) -> str:
    rendered = "$"
    for part in parts:
        if isinstance(part, int):
            rendered += f"[{part}]"
        else:
            rendered += f".{part}"
    return rendered


def _canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def canonical_blueprint(blueprint: Mapping[str, Any]) -> Dict[str, Any]:
    """Return a stable, semantically equivalent blueprint representation."""

    result = deepcopy(dict(blueprint))
    result["actions"] = sorted(result.get("actions", []), key=lambda item: item["name"])
    result["components"] = sorted(result.get("components", []), key=lambda item: item["id"])
    result["acceptance"] = sorted(set(result.get("acceptance", [])))
    return result


def adapt_teacher_blueprint(teacher_blueprint: Mapping[str, Any]) -> Dict[str, Any]:
    """Convert the strict DeepSeek map transport into a canonical blueprint."""

    result = deepcopy(dict(teacher_blueprint))
    required_component_ids = {
        key.removeprefix("required_id_"): result.pop(key)
        for key in list(result)
        if key.startswith("required_id_")
    }
    if required_component_ids:
        components_by_id = {
            item.get("id"): item
            for item in result.get("components", [])
            if isinstance(item, dict)
        }
        diagnostics = []
        for component_name, component_id in required_component_ids.items():
            actual = components_by_id.get(component_id)
            if actual is None or actual.get("component") != component_name:
                diagnostics.append(
                    Diagnostic(
                        "required_component_mapping",
                        f"$.required_id_{component_name}",
                        str(component_id),
                    )
                )
        if diagnostics:
            raise BlueprintValidationError(diagnostics)

    def decode_data(value: Any) -> Any:
        if isinstance(value, dict) and set(value) == {"entries"}:
            decoded: Dict[str, Any] = {}
            for item in value["entries"]:
                key = item["key"]
                if key in decoded:
                    raise BlueprintValidationError(
                        [Diagnostic("duplicate_data_key", "$.data_model", key)]
                    )
                try:
                    decoded[key] = json.loads(item["value_json"])
                except (KeyError, TypeError, json.JSONDecodeError) as error:
                    raise BlueprintValidationError(
                        [Diagnostic("invalid_data_json", f"$.data_model.{key}", str(error))]
                    ) from error
            return decoded
        return value

    result["data_model"] = decode_data(result.get("data_model"))
    for component in result.get("components", []):
        for key in [name for name, value in component.items() if value is None]:
            del component[key]
        for tab in component.get("tabs", []):
            for key in [name for name, value in tab.items() if value is None]:
                del tab[key]
    for _, value in _walk_values(result.get("components", [])):
        if not isinstance(value, dict) or "event" not in value:
            continue
        context = value.get("context")
        if context is None:
            del value["context"]
            continue
        if not isinstance(context, dict) or set(context) != {"entries"}:
            continue
        decoded_context: Dict[str, Any] = {}
        for item in context["entries"]:
            key = item["key"]
            if key in decoded_context:
                raise BlueprintValidationError(
                    [Diagnostic("duplicate_action_parameter", "$.components", key)]
                )
            decoded_context[key] = item["value"]
        value["context"] = decoded_context
    return result


def blueprint_sha256(blueprint: Mapping[str, Any]) -> str:
    payload = _canonical_json(canonical_blueprint(blueprint)).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _child_references(component: Mapping[str, Any]) -> List[str]:
    refs: List[str] = []
    spec = COMPONENT_BY_NAME.get(str(component.get("component")))
    if spec is None:
        return refs
    for property_spec in spec.properties:
        value = component.get(property_spec.name)
        if property_spec.kind == "child" and isinstance(value, str):
            refs.append(value)
        elif property_spec.kind == "children" and isinstance(value, list):
            refs.extend(item for item in value if isinstance(item, str))
        elif property_spec.kind == "tabs" and isinstance(value, list):
            refs.extend(
                tab["child"]
                for tab in value
                if isinstance(tab, dict) and isinstance(tab.get("child"), str)
            )
    return refs


def _walk_values(value: Any, path: Tuple[Any, ...] = ()) -> Iterable[Tuple[Tuple[Any, ...], Any]]:
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            yield from _walk_values(child, path + (key,))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _walk_values(child, path + (index,))


def _resolve_path(data: Any, path: str) -> Tuple[bool, Any]:
    if data is UNKNOWN_TEMPLATE_CONTEXT:
        return True, UNKNOWN_TEMPLATE_CONTEXT
    if path == ".":
        return True, data
    current = data
    parts = [part for part in path.split("/") if part]
    for part in parts:
        if isinstance(current, dict) and part in current:
            current = current[part]
        elif isinstance(current, list) and part.isdigit() and int(part) < len(current):
            current = current[int(part)]
        else:
            return False, None
    return True, current


def _template_contexts(
    components: Mapping[str, Mapping[str, Any]],
    graph: Mapping[str, Sequence[str]],
    data_model: Mapping[str, Any],
    root_id: str,
) -> Dict[str, Any]:
    contexts: Dict[str, Any] = {}
    visited: Set[str] = set()

    def visit(component_id: str, context: Any) -> None:
        if component_id in visited or component_id not in components:
            return
        visited.add(component_id)
        if context is not None:
            contexts[component_id] = context
        component = components[component_id]
        template = component.get("template")
        items = component.get("items")
        template_context = None
        if isinstance(template, str) and isinstance(items, dict):
            path = items.get("binding")
            if isinstance(path, str):
                source = data_model if path.startswith("/") else context
                exists, values = _resolve_path(source, path)
                if exists and values is UNKNOWN_TEMPLATE_CONTEXT:
                    template_context = UNKNOWN_TEMPLATE_CONTEXT
                elif exists and isinstance(values, list):
                    template_context = values[0] if values else UNKNOWN_TEMPLATE_CONTEXT
        for child in graph.get(component_id, ()):
            visit(child, template_context if child == template else context)

    visit(root_id, None)
    return contexts


def _collection_action_context(
    component: Mapping[str, Any],
    property_name: str,
    data_model: Mapping[str, Any],
) -> Any:
    """Resolve one collection item for a collection-scoped action binding."""

    spec = COMPONENT_BY_NAME.get(str(component.get("component")))
    if spec is None:
        return None
    property_spec = next(
        (item for item in spec.properties if item.name == property_name),
        None,
    )
    if property_spec is None or property_spec.kind != "action":
        return None
    collection_bindings = []
    for candidate in spec.properties:
        if candidate.kind != "binding":
            continue
        value = component.get(candidate.name)
        if not isinstance(value, dict) or set(value) != {"binding"}:
            continue
        binding = value["binding"]
        if not isinstance(binding, str) or not binding.startswith("/"):
            continue
        exists, resolved = _resolve_path(data_model, binding)
        if exists and isinstance(resolved, list):
            collection_bindings.append(
                resolved[0] if resolved else UNKNOWN_TEMPLATE_CONTEXT
            )
    return collection_bindings[0] if len(collection_bindings) == 1 else None


def _validate_graph(
    blueprint: Mapping[str, Any],
    diagnostics: List[Diagnostic],
) -> Tuple[Dict[str, Mapping[str, Any]], Dict[str, List[str]]]:
    component_list = blueprint.get("components", [])
    components = {
        item["id"]: item
        for item in component_list
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }
    ids = [item.get("id") for item in component_list if isinstance(item, dict)]
    duplicates = sorted(name for name, count in Counter(ids).items() if count > 1)
    for name in duplicates:
        diagnostics.append(Diagnostic("duplicate_component_id", "$.components", name))

    root_id = blueprint.get("root_id")
    if root_id not in components:
        diagnostics.append(Diagnostic("missing_root", "$.root_id", str(root_id)))

    graph: Dict[str, List[str]] = {}
    parent_count: Counter[str] = Counter()
    for index, component in enumerate(component_list):
        if not isinstance(component, dict) or not isinstance(component.get("id"), str):
            continue
        refs = _child_references(component)
        graph[component["id"]] = refs
        for ref in refs:
            if ref not in components:
                diagnostics.append(
                    Diagnostic(
                        "missing_child_reference",
                        f"$.components[{index}]",
                        ref,
                    )
                )
            parent_count[ref] += 1

    for component_id, count in sorted(parent_count.items()):
        if component_id in components and count > 1:
            diagnostics.append(
                Diagnostic("multiple_parents", f"$.components.{component_id}", str(count))
            )

    visiting: Set[str] = set()
    visited: Set[str] = set()
    max_depth = 0

    def visit(node: str, depth: int) -> None:
        nonlocal max_depth
        if node in visiting:
            diagnostics.append(Diagnostic("component_cycle", f"$.components.{node}", node))
            return
        if node in visited or node not in components:
            return
        visiting.add(node)
        max_depth = max(max_depth, depth)
        for child in graph.get(node, []):
            visit(child, depth + 1)
        visiting.remove(node)
        visited.add(node)

    if isinstance(root_id, str):
        visit(root_id, 1)
    if max_depth > MAX_COMPONENT_DEPTH:
        diagnostics.append(Diagnostic("depth_budget", "$.components", str(max_depth)))
    unreachable = sorted(set(components) - visited)
    for component_id in unreachable:
        diagnostics.append(
            Diagnostic("unreachable_component", f"$.components.{component_id}", component_id)
        )
    return components, graph


def _validate_bindings_and_functions(
    blueprint: Mapping[str, Any],
    components: Mapping[str, Mapping[str, Any]],
    graph: Mapping[str, Sequence[str]],
    diagnostics: List[Diagnostic],
) -> None:
    data_model = blueprint.get("data_model", {})
    contexts = _template_contexts(
        components,
        graph,
        data_model,
        str(blueprint.get("root_id", "")),
    )

    def expected_type(component: Mapping[str, Any], property_name: str):
        spec = COMPONENT_BY_NAME[component["component"]]
        property_spec = next(
            (item for item in spec.properties if item.name == property_name),
            None,
        )
        if property_spec is None:
            return None
        if property_spec.kind == "enum":
            return ("enum", property_spec.enum)
        by_kind = {
            "text_value": "string",
            "numeric_value": "number",
            "boolean_value": "boolean",
            "display_value": "scalar",
        }
        if property_spec.kind in by_kind:
            return by_kind[property_spec.kind]
        return {
            ("List", "items"): "array",
            ("Timeline", "items"): "array",
            ("DataTable", "rows"): "array",
            ("DataTable", "sort"): "string",
            ("Chart", "series"): "array",
            ("ImageGallery", "items"): "array",
            ("SourceList", "sources"): "array",
            ("MapPreview", "markers"): "array",
            ("TextField", "value"): "string",
            ("CheckBox", "checked"): "boolean",
            ("ChoicePicker", "value"): "choice",
            ("Slider", "value"): "number",
            ("DateTimeInput", "value"): "string",
        }.get((component["component"], property_name))

    def matches(value: Any, kind: Any) -> bool:
        if value is UNKNOWN_TEMPLATE_CONTEXT:
            return True
        if kind is None:
            return True
        if isinstance(kind, tuple) and kind[0] == "enum":
            return isinstance(value, str) and value in kind[1]
        if kind == "string":
            return isinstance(value, str)
        if kind == "number":
            return isinstance(value, (int, float)) and not isinstance(value, bool)
        if kind == "boolean":
            return isinstance(value, bool)
        if kind == "array":
            return isinstance(value, list)
        if kind == "choice":
            return isinstance(value, (str, int, float, bool, list))
        if kind == "scalar":
            return value is None or isinstance(value, (str, int, float, bool))
        return True

    for component_id, component in components.items():
        for path, value in _walk_values(component):
            if isinstance(value, dict) and set(value) == {"binding"}:
                binding = value["binding"]
                if binding.startswith("/"):
                    exists, _ = _resolve_path(data_model, binding)
                    context = None
                else:
                    context = contexts.get(component_id)
                    if context is None and path:
                        context = _collection_action_context(
                            component,
                            str(path[0]),
                            data_model,
                        )
                    exists, _ = _resolve_path(context, binding) if context is not None else (False, None)
                if not exists:
                    diagnostics.append(
                        Diagnostic(
                            "missing_binding",
                            f"$.components.{component_id}.{'.'.join(map(str, path))}",
                            binding,
                        )
                    )
                elif path:
                    property_name = str(path[0])
                    expected = expected_type(component, property_name)
                    _, resolved = (
                        _resolve_path(data_model, binding)
                        if binding.startswith("/")
                        else _resolve_path(contexts.get(component_id), binding)
                    )
                    if not matches(resolved, expected):
                        diagnostics.append(
                            Diagnostic(
                                "binding_type_mismatch",
                                f"$.components.{component_id}.{property_name}",
                                f"{binding} must resolve to {expected}",
                            )
                        )
            if isinstance(value, dict) and "function" in value:
                function = value.get("function")
                if function not in FUNCTIONS:
                    diagnostics.append(
                        Diagnostic("unknown_function", f"$.components.{component_id}", str(function))
                    )


def _validate_actions(
    blueprint: Mapping[str, Any],
    components: Mapping[str, Mapping[str, Any]],
    diagnostics: List[Diagnostic],
) -> None:
    declarations = {
        action["name"]: action
        for action in blueprint.get("actions", [])
        if isinstance(action, dict) and isinstance(action.get("name"), str)
    }
    if len(declarations) != len(blueprint.get("actions", [])):
        diagnostics.append(Diagnostic("duplicate_action", "$.actions", "action names must be unique"))
    for component_id, component in components.items():
        for path, value in _walk_values(component):
            if not isinstance(value, dict) or set(value).difference({"event", "context"}):
                continue
            event = value.get("event")
            if not isinstance(event, str):
                continue
            declaration = declarations.get(event)
            if declaration is None:
                diagnostics.append(
                    Diagnostic("undeclared_action", f"$.components.{component_id}", event)
                )
                continue
            context = value.get("context", {})
            context_keys = set(context) if isinstance(context, dict) else set()
            parameters = {item["name"]: item for item in declaration["parameters"]}
            unknown = context_keys - set(parameters)
            missing = {
                name for name, item in parameters.items() if item["required"] and name not in context_keys
            }
            if unknown:
                diagnostics.append(
                    Diagnostic("unknown_action_parameter", f"$.components.{component_id}", ",".join(sorted(unknown)))
                )
            if missing:
                diagnostics.append(
                    Diagnostic("missing_action_parameter", f"$.components.{component_id}", ",".join(sorted(missing)))
                )


def _validate_accessibility_and_content(
    components: Mapping[str, Mapping[str, Any]],
    diagnostics: List[Diagnostic],
) -> None:
    description_required = {
        "Image",
        "Video",
        "AudioPlayer",
        "DataTable",
        "Chart",
        "Timeline",
        "ImageGallery",
        "MapPreview",
        "CodeBlock",
        "InteractiveSurface",
    }
    for component_id, component in components.items():
        kind = component.get("component")
        if kind in description_required and not component.get("description"):
            diagnostics.append(
                Diagnostic("missing_accessibility_description", f"$.components.{component_id}", str(kind))
            )
        if kind == "Button" and component.get("icon_only") and not component.get("accessibility_label"):
            diagnostics.append(
                Diagnostic("missing_accessibility_label", f"$.components.{component_id}", "icon-only button")
            )
        for path, value in _walk_values(component):
            if isinstance(value, dict) and set(value) == {"client_function", "args"}:
                argument = value["args"][0]
                if isinstance(argument, str):
                    parsed = urlparse(argument)
                    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_DATASET_URL_HOSTS:
                        diagnostics.append(
                            Diagnostic("unsafe_dataset_url", f"$.components.{component_id}", argument)
                        )
            if not isinstance(value, str):
                continue
            property_name = str(path[-1]) if path else ""
            if property_name.endswith("url"):
                parsed = urlparse(value)
                if parsed.scheme != "https" or parsed.hostname not in ALLOWED_DATASET_URL_HOSTS:
                    diagnostics.append(
                        Diagnostic("unsafe_dataset_url", f"$.components.{component_id}.{property_name}", value)
                    )
            if kind == "CodeBlock" and property_name == "content":
                continue
            if FORBIDDEN_LITERAL.search(value):
                diagnostics.append(
                    Diagnostic("forbidden_literal", f"$.components.{component_id}.{property_name}", "active or platform code")
                )


def _validate_dataset_value_content(
    data_model: Mapping[str, Any],
    diagnostics: List[Diagnostic],
) -> None:
    for path, value in _walk_values(data_model):
        if not isinstance(value, str):
            continue
        property_name = str(path[-1]) if path else ""
        if property_name == "url" or property_name.endswith("_url"):
            parsed = urlparse(value)
            if parsed.scheme != "https" or parsed.hostname not in ALLOWED_DATASET_URL_HOSTS:
                diagnostics.append(
                    Diagnostic("unsafe_dataset_url", f"$.data_model.{'.'.join(map(str, path))}", value)
                )
        if FORBIDDEN_LITERAL.search(value):
            diagnostics.append(
                Diagnostic("forbidden_literal", f"$.data_model.{'.'.join(map(str, path))}", "active or platform code")
            )


def validate_blueprint(blueprint: Mapping[str, Any]) -> None:
    """Validate JSON shape and cross-component semantics or raise one error."""

    diagnostics: List[Diagnostic] = []
    for error in sorted(SCHEMA_VALIDATOR.iter_errors(blueprint), key=lambda item: list(item.path)):
        diagnostics.append(
            Diagnostic("schema", _json_path(error.absolute_path), error.message)
        )
    if diagnostics:
        raise BlueprintValidationError(diagnostics)

    components, graph = _validate_graph(blueprint, diagnostics)
    _validate_bindings_and_functions(blueprint, components, graph, diagnostics)
    _validate_actions(blueprint, components, diagnostics)
    _validate_accessibility_and_content(components, diagnostics)
    _validate_dataset_value_content(blueprint["data_model"], diagnostics)

    for component_id, component in components.items():
        if component.get("component") == "Slider" and component["min"] >= component["max"]:
            diagnostics.append(
                Diagnostic("invalid_slider_range", f"$.components.{component_id}", "min must be below max")
            )
        if component.get("component") == "Grid" and len(component["children"]) < component["columns"]:
            diagnostics.append(
                Diagnostic("invalid_grid", f"$.components.{component_id}", "columns exceed child count")
            )
    if diagnostics:
        raise BlueprintValidationError(diagnostics)


def validate_task_alignment(
    blueprint: Mapping[str, Any],
    task: Mapping[str, Any],
) -> None:
    """Require a teacher response to satisfy, rather than rewrite, its task seed."""

    validate_blueprint(blueprint)
    diagnostics: List[Diagnostic] = []
    for field in ("request", "locale", "domain", "task_kind", "viewport", "theme", "state", "density"):
        if blueprint.get(field) != task.get(field):
            diagnostics.append(
                Diagnostic(
                    "task_mismatch",
                    f"$.{field}",
                    f"expected {task.get(field)!r}, got {blueprint.get(field)!r}",
                )
            )
    present = {component["component"] for component in blueprint["components"]}
    required = set(task.get("required_components", []))
    missing = sorted(required - present)
    if missing:
        diagnostics.append(
            Diagnostic("missing_required_component", "$.components", ",".join(missing))
        )
    focal = task.get("focal_component")
    if focal not in present:
        diagnostics.append(
            Diagnostic("missing_focal_component", "$.components", str(focal))
        )
    if diagnostics:
        raise BlueprintValidationError(diagnostics)


def _topological_components(blueprint: Mapping[str, Any]) -> List[Mapping[str, Any]]:
    components = {item["id"]: item for item in blueprint["components"]}
    ordered: List[Mapping[str, Any]] = []
    visited: Set[str] = set()

    def visit(component_id: str) -> None:
        if component_id in visited:
            return
        for child in _child_references(components[component_id]):
            visit(child)
        visited.add(component_id)
        ordered.append(components[component_id])

    visit(blueprint["root_id"])
    return ordered


def _express_identifier(component_id: str, root_id: str) -> str:
    return "root" if component_id == root_id else component_id


def _express_value(value: Any, root_id: str, *, child: bool = False) -> str:
    if child and isinstance(value, str):
        return _express_identifier(value, root_id)
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, (int, float)):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        return "[" + ", ".join(_express_value(item, root_id, child=child) for item in value) + "]"
    if isinstance(value, dict):
        if set(value) == {"binding"}:
            binding = value["binding"]
            if binding == ".":
                return "$"
            return f"${binding}"
        if set(value) == {"function", "args"}:
            args = ", ".join(_express_value(item, root_id) for item in value["args"])
            return f"{value['function']}({args})"
        if "event" in value and set(value).issubset({"event", "context"}):
            context = _express_value(value.get("context", {}), root_id)
            return f"Event({_express_value(value['event'], root_id)}, {context})"
        if set(value) == {"client_function", "args"}:
            args = ", ".join(_express_value(item, root_id) for item in value["args"])
            return f"{value['client_function']}({args})"
        pairs = ", ".join(
            f"{key}: {_express_value(item, root_id)}" for key, item in sorted(value.items())
        )
        return "{" + pairs + "}"
    raise TypeError(f"Unsupported A2UI Express value: {type(value)!r}")


def _express_property(name: str, value: Any, root_id: str, kind: str) -> str:
    if kind == "child":
        rendered = _express_value(value, root_id, child=True)
    elif kind == "children":
        rendered = _express_value(value, root_id, child=True)
    elif kind == "tabs":
        rendered_tabs = []
        for tab in value:
            entries = []
            for key, item in sorted(tab.items()):
                entries.append(
                    f"{key}: {_express_value(item, root_id, child=(key == 'child'))}"
                )
            rendered_tabs.append("{" + ", ".join(entries) + "}")
        rendered = "[" + ", ".join(rendered_tabs) + "]"
    else:
        rendered = _express_value(value, root_id)
    return f"{name}={rendered}"


def compile_a2ui_express(blueprint: Mapping[str, Any]) -> str:
    """Compile a valid blueprint into the pinned keyword/topological profile."""

    validate_blueprint(blueprint)
    root_id = blueprint["root_id"]
    lines = ["<a2ui>", f'surface("ui-{blueprint_sha256(blueprint)[:16]}", "{ASSISTANT_CATALOG_ID}")']
    for key, value in sorted(blueprint["data_model"].items()):
        lines.append(f"$/{key} = {_express_value(value, root_id)}")
    for component in _topological_components(blueprint):
        property_kinds = {
            item.name: item.kind
            for item in COMPONENT_BY_NAME[component["component"]].properties
        }
        arguments = [
            _express_property(name, value, root_id, property_kinds[name])
            for name, value in component.items()
            if name not in {"id", "component"}
        ]
        variable = _express_identifier(component["id"], root_id)
        lines.append(f"{variable} = {component['component']}({', '.join(arguments)})")
    lines.append("</a2ui>")
    return "\n".join(lines)


def _wire_value(value: Any, root_id: str, *, child: bool = False) -> Any:
    if child and isinstance(value, str):
        return _express_identifier(value, root_id)
    if isinstance(value, list):
        return [_wire_value(item, root_id, child=child) for item in value]
    if isinstance(value, dict):
        if set(value) == {"binding"}:
            return {"path": value["binding"]}
        if set(value) == {"function", "args"}:
            return {
                "call": value["function"],
                "args": [_wire_value(item, root_id) for item in value["args"]],
            }
        if "event" in value and set(value).issubset({"event", "context"}):
            return {
                "event": {
                    "name": value["event"],
                    "context": _wire_value(value.get("context", {}), root_id),
                }
            }
        if set(value) == {"client_function", "args"}:
            return {
                "call": value["client_function"],
                "args": [_wire_value(item, root_id) for item in value["args"]],
            }
        return {key: _wire_value(item, root_id) for key, item in value.items()}
    return value


def compile_a2ui_wire(blueprint: Mapping[str, Any]) -> Dict[str, Any]:
    """Compile a valid blueprint into a canonical A2UI v1-style envelope."""

    validate_blueprint(blueprint)
    root_id = blueprint["root_id"]
    output_components = []
    for component in _topological_components(blueprint):
        property_kinds = {
            item.name: item.kind
            for item in COMPONENT_BY_NAME[component["component"]].properties
        }
        output = {
            "id": _express_identifier(component["id"], root_id),
            "component": component["component"],
        }
        for name, value in component.items():
            if name in {"id", "component"}:
                continue
            if property_kinds[name] in {"child", "children"}:
                output[name] = _wire_value(value, root_id, child=True)
            elif property_kinds[name] == "tabs":
                output[name] = [
                    {
                        key: _wire_value(item, root_id, child=(key == "child"))
                        for key, item in tab.items()
                    }
                    for tab in value
                ]
            else:
                output[name] = _wire_value(value, root_id)
        output_components.append(output)
    return {
        "version": "v1.0",
        "createSurface": {
            "surfaceId": f"ui-{blueprint_sha256(blueprint)[:16]}",
            "catalogId": ASSISTANT_CATALOG_ID,
            "catalogs": [BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID],
            "components": output_components,
            "dataModel": deepcopy(blueprint["data_model"]),
            "surfaceParams": {
                "viewport": blueprint["viewport"],
                "theme": blueprint["theme"],
                "locale": blueprint["locale"],
            },
        },
    }


def structural_fingerprint(blueprint: Mapping[str, Any]) -> str:
    """Hash layout semantics independent of teacher-chosen names and literals."""

    validate_blueprint(blueprint)
    components = {item["id"]: item for item in blueprint["components"]}
    ordered = []

    def preorder(component_id: str) -> None:
        ordered.append(component_id)
        for child in _child_references(components[component_id]):
            preorder(child)

    preorder(blueprint["root_id"])
    component_names = {component_id: f"c{index}" for index, component_id in enumerate(ordered)}
    action_names = {
        item["name"]: f"a{index}"
        for index, item in enumerate(sorted(blueprint["actions"], key=lambda value: value["name"]))
    }
    parameter_names = {
        item["name"]: {
            parameter["name"]: f"p{index}"
            for index, parameter in enumerate(item["parameters"])
        }
        for item in blueprint["actions"]
    }
    binding_names: Dict[Tuple[str, str], str] = {}
    enum_keys = {
        "component", "variant", "tone", "align", "justify", "fit", "aspect",
        "size", "direction", "padding", "input_type", "mode", "state",
        "provider", "language", "input_mode", "type",
    }
    structural_numbers = {"columns", "min", "max", "step"}

    def normalize(value: Any, key: Optional[str] = None, current_action: Optional[str] = None) -> Any:
        if isinstance(value, dict):
            if set(value) == {"binding"}:
                binding = value["binding"]
                scope = "absolute" if binding.startswith("/") else "relative"
                token_key = (scope, binding)
                if token_key not in binding_names:
                    binding_names[token_key] = f"{scope}:{len(binding_names)}"
                return {"binding": binding_names[token_key]}
            if "event" in value and set(value).issubset({"event", "context"}):
                event_name = value["event"]
                params = parameter_names.get(event_name, {})
                return {
                    "event": action_names.get(event_name, "a?"),
                    "context": {
                        params.get(name, f"p?{index}"): normalize(child, name, event_name)
                        for index, (name, child) in enumerate(sorted(value.get("context", {}).items()))
                    },
                }
            return {
                name: normalize(child, name, current_action)
                for name, child in sorted(value.items())
                if name not in {"description"}
            }
        if isinstance(value, list):
            return [normalize(child, key, current_action) for child in value]
        if key == "event" and isinstance(value, str):
            return action_names.get(value, "a?")
        if isinstance(value, str):
            if key in enum_keys:
                return value
            return "<text>"
        if isinstance(value, bool):
            return value if key in {"required", "confirmation_required", "icon_only", "multiple"} else "<boolean>"
        if isinstance(value, (int, float)):
            return value if key in structural_numbers else "<number>"
        return value

    normalized_components = []
    for component_id in ordered:
        component = deepcopy(components[component_id])
        normalized = {
            "id": component_names[component_id],
            "component": component["component"],
        }
        property_specs = {
            item.name: item for item in COMPONENT_BY_NAME[component["component"]].properties
        }
        for name, value in component.items():
            if name in {"id", "component"}:
                continue
            kind = property_specs[name].kind
            if kind == "child":
                normalized[name] = component_names[value]
            elif kind == "children":
                normalized[name] = [component_names[item] for item in value]
            elif kind == "tabs":
                normalized[name] = [
                    {
                        key: component_names[item] if key == "child" else normalize(item, key)
                        for key, item in tab.items()
                    }
                    for tab in value
                ]
            else:
                normalized[name] = normalize(value, name)
        normalized_components.append(normalized)

    normalized_actions = []
    for declaration in sorted(blueprint["actions"], key=lambda value: value["name"]):
        normalized_actions.append(
            {
                "name": action_names[declaration["name"]],
                "confirmation_required": declaration["confirmation_required"],
                "parameters": [
                    {
                        "name": parameter_names[declaration["name"]][parameter["name"]],
                        "type": parameter["type"],
                        "required": parameter["required"],
                    }
                    for parameter in declaration["parameters"]
                ],
            }
        )
    structural = {
        "components": normalized_components,
        "root_id": component_names[blueprint["root_id"]],
        "actions": normalized_actions,
    }
    payload = _canonical_json(structural).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()
