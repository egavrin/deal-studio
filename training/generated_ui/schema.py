"""JSON Schema compiler for the canonical generated-UI blueprint."""

from __future__ import annotations

from copy import deepcopy
from typing import Any, Dict, Optional, Sequence

from .catalog import COMPONENTS, DOMAINS, FUNCTIONS, PropertySpec


def _ref(name: str) -> Dict[str, str]:
    return {"$ref": f"#/$defs/{name}"}


def _property_schema(item: PropertySpec) -> Dict[str, Any]:
    simple = {
        "text": {"type": "string", "minLength": 1, "maxLength": 4000},
        "number": {"type": "number"},
        "integer": {"type": "integer", "minimum": 1, "maximum": 64},
        "boolean": {"type": "boolean"},
        "binding": _ref("binding"),
        "display_value": _ref("displayValue"),
        "text_value": _ref("textValue"),
        "numeric_value": _ref("numericValue"),
        "boolean_value": _ref("booleanValue"),
        "child": _ref("componentId"),
        "children": {
            "type": "array",
            "items": _ref("componentId"),
            "minItems": 1,
            "maxItems": 24,
            "uniqueItems": True,
        },
        "action": _ref("actionValue"),
        "options": {
            "type": "array",
            "minItems": 1,
            "maxItems": 24,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["label", "value"],
                "properties": {
                    "label": _ref("textValue"),
                    "value": {"type": ["string", "number", "boolean"]},
                },
            },
        },
        "tabs": {
            "type": "array",
            "minItems": 1,
            "maxItems": 8,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["label", "child"],
                "properties": {
                    "label": _ref("textValue"),
                    "child": _ref("componentId"),
                    "value": {"type": "string", "minLength": 1, "maxLength": 64},
                },
            },
        },
        "validation_rules": {
            "type": "array",
            "minItems": 1,
            "maxItems": 8,
            "items": _ref("functionCall"),
        },
        "table_columns": {
            "type": "array",
            "minItems": 1,
            "maxItems": 12,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["key", "label"],
                "properties": {
                    "key": {"type": "string", "pattern": "^[A-Za-z_][A-Za-z0-9_]{0,63}$"},
                    "label": _ref("textValue"),
                    "align": {"type": "string", "enum": ["start", "center", "end"]},
                },
            },
        },
    }
    if item.kind == "enum":
        return {
            "anyOf": [
                {"type": "string", "enum": list(item.enum)},
                _ref("binding"),
                _ref("functionCall"),
            ]
        }
    return deepcopy(simple[item.kind])


def _component_schema() -> Dict[str, Any]:
    variants = []
    for component in COMPONENTS:
        properties = {
            "id": _ref("componentId"),
            "component": {"type": "string", "const": component.name},
        }
        properties.update(
            {item.name: _property_schema(item) for item in component.properties}
        )
        required = ["id", "component"] + [
            item.name for item in component.properties if item.required
        ]
        variants.append(
            {
                "title": component.name,
                "type": "object",
                "additionalProperties": False,
                "required": required,
                "properties": properties,
            }
        )
    return {"anyOf": variants}


def build_blueprint_schema() -> Dict[str, Any]:
    """Build the closed teacher response schema used by DeepSeek and CI."""

    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "https://offline-assistant.local/schemas/ui-blueprint-v1.schema.json",
        "title": "UiBlueprintV1",
        "type": "object",
        "additionalProperties": False,
        "required": [
            "schema_version",
            "request",
            "locale",
            "domain",
            "task_kind",
            "viewport",
            "theme",
            "state",
            "density",
            "data_model",
            "actions",
            "components",
            "root_id",
            "acceptance",
        ],
        "properties": {
            "schema_version": {"type": "integer", "const": 1},
            "request": {"type": "string", "minLength": 3, "maxLength": 1200},
            "locale": {"type": "string", "enum": ["en-US", "ru-RU", "mixed"]},
            "domain": {"type": "string", "enum": list(DOMAINS)},
            "task_kind": {
                "type": "string",
                "pattern": "^[a-z][a-z0-9_]{1,63}$",
            },
            "viewport": {"type": "string", "enum": ["phone_compact", "foldable", "tablet", "large_font"]},
            "theme": {"type": "string", "enum": ["light", "dark", "high_contrast", "dynamic"]},
            "state": {"type": "string", "enum": ["populated", "loading", "partial", "empty", "stale", "offline", "denied", "error"]},
            "density": {"type": "string", "enum": ["compact", "comfortable", "dense"]},
            "data_model": {"type": "object"},
            "actions": {
                "type": "array",
                "maxItems": 16,
                "items": _ref("actionDeclaration"),
            },
            "components": {
                "type": "array",
                "minItems": 1,
                "maxItems": 64,
                "items": _ref("component"),
            },
            "root_id": _ref("componentId"),
            "acceptance": {
                "type": "array",
                "minItems": 1,
                "maxItems": 16,
                "items": {"type": "string", "minLength": 3, "maxLength": 240},
            },
            "fallback_reason": {
                "type": ["string", "null"],
                "minLength": 3,
                "maxLength": 500,
            },
        },
        "$defs": {
            "componentId": {
                "type": "string",
                "pattern": "^[A-Za-z_][A-Za-z0-9_]{0,63}$",
            },
            "binding": {
                "type": "object",
                "additionalProperties": False,
                "required": ["binding"],
                "properties": {
                    "binding": {
                        "type": "string",
                        "pattern": "^(?:\.|/(?:[A-Za-z_][A-Za-z0-9_]*(?:/(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*)?|[A-Za-z_][A-Za-z0-9_]*(?:/(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*)$",
                    }
                },
            },
            "functionCall": {
                "type": "object",
                "additionalProperties": False,
                "required": ["function", "args"],
                "properties": {
                    "function": {"type": "string", "enum": [name for name in FUNCTIONS if name != "openUrl"]},
                    "args": {
                        "type": "array",
                        "maxItems": 8,
                        "items": _ref("displayValue"),
                    },
                },
            },
            "displayValue": {
                "anyOf": [
                    {"type": ["string", "number", "boolean", "null"]},
                    _ref("binding"),
                    _ref("functionCall"),
                ]
            },
            "textValue": {
                "anyOf": [
                    {"type": "string", "maxLength": 8000},
                    _ref("binding"),
                    _ref("functionCall"),
                ]
            },
            "numericValue": {
                "anyOf": [
                    {"type": "number"},
                    _ref("binding"),
                    _ref("functionCall"),
                ]
            },
            "booleanValue": {
                "anyOf": [
                    {"type": "boolean"},
                    _ref("binding"),
                    _ref("functionCall"),
                ]
            },
            "eventAction": {
                "type": "object",
                "additionalProperties": False,
                "required": ["event"],
                "properties": {
                    "event": {
                        "type": "string",
                        "pattern": "^[a-z][a-z0-9_]{1,63}$",
                    },
                    "context": {
                        "type": "object",
                        "maxProperties": 12,
                        "additionalProperties": _ref("displayValue"),
                    },
                },
            },
            "clientAction": {
                "type": "object",
                "additionalProperties": False,
                "required": ["client_function", "args"],
                "properties": {
                    "client_function": {"type": "string", "const": "openUrl"},
                    "args": {
                        "type": "array",
                        "minItems": 1,
                        "maxItems": 1,
                        "items": _ref("textValue"),
                    },
                },
            },
            "actionValue": {
                "anyOf": [_ref("eventAction"), _ref("clientAction")]
            },
            "actionDeclaration": {
                "type": "object",
                "additionalProperties": False,
                "required": ["name", "description", "confirmation_required", "parameters"],
                "properties": {
                    "name": {
                        "type": "string",
                        "pattern": "^[a-z][a-z0-9_]{1,63}$",
                    },
                    "description": {"type": "string", "minLength": 3, "maxLength": 240},
                    "confirmation_required": {"type": "boolean"},
                    "parameters": {
                        "type": "array",
                        "maxItems": 12,
                        "items": {
                            "type": "object",
                            "additionalProperties": False,
                            "required": ["name", "type", "required"],
                            "properties": {
                                "name": {
                                    "type": "string",
                                    "pattern": "^[a-z][a-z0-9_]{0,63}$",
                                },
                                "type": {
                                    "type": "string",
                                    "enum": ["string", "number", "integer", "boolean", "datetime", "url", "object", "array"]
                                },
                                "required": {"type": "boolean"},
                            },
                        },
                    },
                },
            },
            "component": _component_schema(),
        },
    }


def build_teacher_response_schema(
    required_components: Optional[Sequence[str]] = None,
) -> Dict[str, Any]:
    """Build DeepSeek-compatible transport schema without dynamic object keys.

    The canonical blueprint intentionally uses ordinary JSON objects for its data
    model and event context. DeepSeek structured output rejects schemas for objects
    with arbitrary properties, so the transport represents maps as explicit entry
    arrays. `adapt_teacher_blueprint` converts the response before canonical
    validation; this transport is never a student target.
    """

    schema = deepcopy(build_blueprint_schema())
    schema["$id"] = "https://offline-assistant.local/schemas/ui-blueprint-teacher-v1.schema.json"
    schema["title"] = "UiBlueprintTeacherTransportV1"
    schema["properties"]["data_model"] = _ref("teacherDataObject")
    schema["$defs"]["eventAction"]["properties"]["context"] = _ref("teacherContext")
    if required_components:
        for name in sorted(set(required_components)):
            field = f"required_id_{name}"
            schema["properties"][field] = _ref("componentId")
            schema["required"].append(field)
    schema["$defs"].update(
        {
            "teacherDataValue": {
                "type": "string",
                "minLength": 1,
                "maxLength": 32000,
            },
            "teacherDataEntry": {
                "type": "object",
                "additionalProperties": False,
                "required": ["key", "value_json"],
                "properties": {
                    "key": {
                        "type": "string",
                        "pattern": "^[A-Za-z_][A-Za-z0-9_]{0,63}$",
                    },
                    "value_json": _ref("teacherDataValue"),
                },
            },
            "teacherDataObject": {
                "type": "object",
                "additionalProperties": False,
                "required": ["entries"],
                "properties": {
                    "entries": {
                        "type": "array",
                        "maxItems": 128,
                        "items": _ref("teacherDataEntry"),
                    }
                },
            },
            "teacherContextEntry": {
                "type": "object",
                "additionalProperties": False,
                "required": ["key", "value"],
                "properties": {
                    "key": {
                        "type": "string",
                        "pattern": "^[a-z][a-z0-9_]{0,63}$",
                    },
                    "value": _ref("displayValue"),
                },
            },
            "teacherContext": {
                "type": "object",
                "additionalProperties": False,
                "required": ["entries"],
                "properties": {
                    "entries": {
                        "type": "array",
                        "maxItems": 12,
                        "items": _ref("teacherContextEntry"),
                    }
                },
            },
        }
    )

    def nullable(value: Dict[str, Any]) -> Dict[str, Any]:
        if value.get("type") == "null":
            return value
        value_type = value.get("type")
        if isinstance(value_type, list) and "null" in value_type:
            return value
        if isinstance(value_type, str):
            result = deepcopy(value)
            result["type"] = [value_type, "null"]
            return result
        variants = list(value.get("anyOf", []))
        if any(variant.get("type") == "null" for variant in variants if isinstance(variant, dict)):
            return value
        return {"anyOf": [value, {"type": "null"}]}

    def strictify(value: Any) -> None:
        if isinstance(value, list):
            for item in value:
                strictify(item)
            return
        if not isinstance(value, dict):
            return
        properties = value.get("properties")
        if isinstance(properties, dict):
            originally_required = set(value.get("required", []))
            for name, child in list(properties.items()):
                strictify(child)
                if name not in originally_required:
                    properties[name] = nullable(child)
            value["required"] = list(properties)
            value["additionalProperties"] = False
        for key in ("anyOf", "oneOf", "allOf"):
            strictify(value.get(key))
        strictify(value.get("items"))
        definitions = value.get("$defs")
        if isinstance(definitions, dict):
            for definition in definitions.values():
                strictify(definition)

    strictify(schema)
    return schema
