"""Pinned component and function catalog used by UI dataset generation.

Property order is intentional. A2UI Express positional signatures are derived from
this file, although the dataset compiler emits keyword arguments for readability
and forward-compatible validation.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional, Tuple


BASIC_CATALOG_ID = "https://offline-assistant.local/catalogs/a2ui-basic-derived/v1"
ASSISTANT_CATALOG_ID = "https://offline-assistant.local/catalogs/assistant/v1"
UPSTREAM_BASIC_CATALOG_ID = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"
UPSTREAM_COMMIT = "7541f953050cd58b80f0bf5d85fe2d63192af305"
UPSTREAM_BASIC_CATALOG_SHA256 = (
    "29e01ac2cf69dc5860ad060f5a60c67fa5cdaa8a78ecab1018f531b178fa5c00"
)


@dataclass(frozen=True)
class PropertySpec:
    name: str
    kind: str
    required: bool = False
    enum: Tuple[str, ...] = ()
    description: str = ""


@dataclass(frozen=True)
class ComponentSpec:
    name: str
    catalog: str
    properties: Tuple[PropertySpec, ...]
    description: str


def prop(
    name: str,
    kind: str,
    *,
    required: bool = False,
    enum: Tuple[str, ...] = (),
    description: str = "",
) -> PropertySpec:
    return PropertySpec(name, kind, required, enum, description)


LAYOUT_PROPS = (
    prop("gap", "enum", enum=("none", "xs", "sm", "md", "lg")),
    prop("align", "enum", enum=("start", "center", "end", "stretch")),
    prop(
        "justify",
        "enum",
        enum=("start", "center", "end", "spaceBetween", "spaceAround"),
    ),
)


COMPONENTS: Tuple[ComponentSpec, ...] = (
    ComponentSpec(
        "Text",
        "basic",
        (
            prop("text", "display_value", required=True),
            prop(
                "variant",
                "enum",
                enum=("display", "h1", "h2", "h3", "title", "body", "caption", "label"),
            ),
            prop("tone", "enum", enum=("default", "muted", "primary", "positive", "warning", "critical", "inverse")),
            prop("align", "enum", enum=("start", "center", "end")),
        ),
        "Text content with renderer-owned typography.",
    ),
    ComponentSpec(
        "Image",
        "basic",
        (
            prop("url", "text_value", required=True),
            prop("description", "text_value", required=True),
            prop("fit", "enum", enum=("contain", "cover", "fill")),
            prop("aspect", "enum", enum=("square", "portrait", "landscape", "wide")),
        ),
        "Attributed image or a controlled unavailable state.",
    ),
    ComponentSpec(
        "Icon",
        "basic",
        (
            prop("name", "text_value", required=True),
            prop("description", "text_value"),
            prop("size", "enum", enum=("sm", "md", "lg")),
            prop("tone", "enum", enum=("default", "muted", "primary", "positive", "warning", "critical", "inverse")),
        ),
        "Catalog icon. Icon-only actions require an accessible description.",
    ),
    ComponentSpec(
        "Video",
        "basic",
        (
            prop("url", "text_value", required=True),
            prop("description", "text_value", required=True),
            prop("poster_url", "text_value"),
            prop("controls", "boolean_value"),
        ),
        "Video result with explicit description and controls.",
    ),
    ComponentSpec(
        "AudioPlayer",
        "basic",
        (
            prop("url", "text_value", required=True),
            prop("title", "text_value", required=True),
            prop("description", "text_value", required=True),
        ),
        "Audio result with accessible title and description.",
    ),
    ComponentSpec("Row", "basic", (prop("children", "children", required=True),) + LAYOUT_PROPS, "Horizontal layout."),
    ComponentSpec("Column", "basic", (prop("children", "children", required=True),) + LAYOUT_PROPS, "Vertical layout."),
    ComponentSpec(
        "List",
        "basic",
        (
            prop("items", "binding", required=True),
            prop("template", "child", required=True),
            prop("empty_state", "child"),
            prop("direction", "enum", enum=("vertical", "horizontal")),
        ),
        "Dynamic bound list with a relative-binding template.",
    ),
    ComponentSpec(
        "Card",
        "basic",
        (
            prop("child", "child", required=True),
            prop("tone", "enum", enum=("plain", "soft", "accent", "dark", "critical")),
            prop("padding", "enum", enum=("none", "sm", "md", "lg")),
        ),
        "Bounded visual group.",
    ),
    ComponentSpec(
        "Tabs",
        "basic",
        (
            prop("tabs", "tabs", required=True),
            prop("selected", "text_value"),
        ),
        "State-preserving tab navigation.",
    ),
    ComponentSpec(
        "Modal",
        "basic",
        (
            prop("trigger", "child", required=True),
            prop("content", "child", required=True),
        ),
        "Modal whose trigger and content are explicit child references.",
    ),
    ComponentSpec("Divider", "basic", (prop("tone", "enum", enum=("soft", "strong")),), "Visual separator."),
    ComponentSpec(
        "Button",
        "basic",
        (
            prop("child", "child", required=True),
            prop("action", "action", required=True),
            prop("variant", "enum", enum=("filled", "tonal", "outline", "text", "critical")),
            prop("enabled", "boolean_value"),
            prop("icon_only", "boolean"),
            prop("accessibility_label", "text_value"),
        ),
        "User action. Privileged effects remain host-confirmed.",
    ),
    ComponentSpec(
        "TextField",
        "basic",
        (
            prop("label", "text_value", required=True),
            prop("value", "binding", required=True),
            prop("placeholder", "text_value"),
            prop("input_type", "enum", enum=("text", "email", "number", "url", "password", "search")),
            prop("validation", "validation_rules"),
        ),
        "Bound text input with optional client validation.",
    ),
    ComponentSpec(
        "CheckBox",
        "basic",
        (
            prop("label", "text_value", required=True),
            prop("checked", "binding", required=True),
            prop("enabled", "boolean_value"),
        ),
        "Bound boolean input.",
    ),
    ComponentSpec(
        "ChoicePicker",
        "basic",
        (
            prop("label", "text_value", required=True),
            prop("value", "binding", required=True),
            prop("options", "options", required=True),
            prop("multiple", "boolean"),
        ),
        "Single or multiple choice input.",
    ),
    ComponentSpec(
        "Slider",
        "basic",
        (
            prop("label", "text_value", required=True),
            prop("value", "binding", required=True),
            prop("min", "number", required=True),
            prop("max", "number", required=True),
            prop("step", "number"),
        ),
        "Bound numeric input.",
    ),
    ComponentSpec(
        "DateTimeInput",
        "basic",
        (
            prop("label", "text_value", required=True),
            prop("value", "binding", required=True),
            prop("mode", "enum", required=True, enum=("date", "time", "datetime")),
            prop("min", "text_value"),
            prop("max", "text_value"),
        ),
        "Bound date/time input.",
    ),
    ComponentSpec(
        "Badge",
        "assistant",
        (
            prop("text", "text_value", required=True),
            prop("tone", "enum", enum=("neutral", "info", "positive", "warning", "critical")),
            prop("icon", "text_value"),
        ),
        "Compact status or source label.",
    ),
    ComponentSpec(
        "Progress",
        "assistant",
        (
            prop("value", "numeric_value"),
            prop("max", "numeric_value"),
            prop("label", "text_value"),
            prop("state", "enum", enum=("determinate", "indeterminate", "paused", "complete", "error")),
        ),
        "Progress indicator.",
    ),
    ComponentSpec(
        "Metric",
        "assistant",
        (
            prop("label", "text_value", required=True),
            prop("value", "display_value", required=True),
            prop("unit", "text_value"),
            prop("trend", "display_value"),
            prop("tone", "enum", enum=("neutral", "positive", "warning", "critical")),
        ),
        "Prominent metric value.",
    ),
    ComponentSpec(
        "KeyValue",
        "assistant",
        (
            prop("label", "text_value", required=True),
            prop("value", "display_value", required=True),
            prop("icon", "text_value"),
        ),
        "Compact fact row.",
    ),
    ComponentSpec(
        "Grid",
        "assistant",
        (
            prop("children", "children", required=True),
            prop("columns", "integer", required=True),
            prop("gap", "enum", enum=("none", "xs", "sm", "md", "lg")),
        ),
        "Responsive static grid.",
    ),
    ComponentSpec(
        "DataTable",
        "assistant",
        (
            prop("columns", "table_columns", required=True),
            prop("rows", "binding", required=True),
            prop("sort", "binding"),
            prop("description", "text_value", required=True),
        ),
        "Dense bound records with renderer-owned sorting UI.",
    ),
    ComponentSpec(
        "Chart",
        "assistant",
        (
            prop("series", "binding", required=True),
            prop("variant", "enum", required=True, enum=("line", "bar", "area")),
            prop("title", "text_value", required=True),
            prop("description", "text_value", required=True),
            prop("x_label", "text_value"),
            prop("y_label", "text_value"),
        ),
        "Bound bounded chart with a required textual description.",
    ),
    ComponentSpec(
        "Timeline",
        "assistant",
        (
            prop("items", "binding", required=True),
            prop("template", "child", required=True),
            prop("empty_state", "child"),
            prop("description", "text_value", required=True),
        ),
        "Ordered event or schedule collection.",
    ),
    ComponentSpec(
        "ImageGallery",
        "assistant",
        (
            prop("items", "binding", required=True),
            prop("columns", "integer"),
            prop("description", "text_value", required=True),
            prop("select_action", "action"),
        ),
        "Attributed image collection.",
    ),
    ComponentSpec(
        "SourceList",
        "assistant",
        (
            prop("sources", "binding", required=True),
            prop("title", "text_value"),
            prop("open_action", "action"),
        ),
        "Citation and source previews.",
    ),
    ComponentSpec(
        "MapPreview",
        "assistant",
        (
            prop("markers", "binding", required=True),
            prop("provider", "enum", required=True, enum=("mock", "system", "online")),
            prop("description", "text_value", required=True),
            prop("open_action", "action"),
        ),
        "Delegated or noninteractive map result.",
    ),
    ComponentSpec(
        "CodeBlock",
        "assistant",
        (
            prop("language", "text", required=True),
            prop("content", "text_value", required=True),
            prop("copy_action", "action"),
            prop("description", "text_value", required=True),
        ),
        "Renderer-owned code presentation; content is never executed.",
    ),
    ComponentSpec(
        "InteractiveSurface",
        "assistant",
        (
            prop("module_id", "text", required=True),
            prop("aspect", "enum", required=True, enum=("scene", "square", "wide")),
            prop("input_mode", "enum", required=True, enum=("tap", "grid", "pointer", "realtime")),
            prop("description", "text_value", required=True),
        ),
        "Validated bridge to an isolated DEAL module.",
    ),
)


FUNCTIONS: Tuple[str, ...] = (
    "and",
    "email",
    "formatCurrency",
    "formatDate",
    "formatNumber",
    "formatString",
    "length",
    "not",
    "numeric",
    "openUrl",
    "or",
    "pluralize",
    "regex",
    "required",
)


DOMAINS: Tuple[str, ...] = (
    "assistant_answer",
    "search",
    "research",
    "productivity",
    "communication",
    "device_control",
    "weather",
    "finance",
    "travel",
    "navigation",
    "data_display",
    "forms",
    "media",
    "education",
    "interactive_tool",
    "generated_app",
    "product_state",
    "unsupported",
)


COMPONENT_BY_NAME: Dict[str, ComponentSpec] = {item.name: item for item in COMPONENTS}


def catalog_prompt_signatures() -> str:
    """Return stable catalog signatures for the teacher system prompt."""

    lines = []
    for component in COMPONENTS:
        args = []
        for item in component.properties:
            suffix = "" if item.required else "?"
            enum_hint = "|".join(item.enum) if item.enum else item.kind
            args.append(f"{item.name}:{enum_hint}{suffix}")
        lines.append(f"{component.name}({', '.join(args)})")
    return "\n".join(lines)


def catalog_teacher_reference() -> str:
    """Return stable signatures plus short component semantics for prompt caching."""

    signatures = catalog_prompt_signatures().splitlines()
    return "\n".join(
        f"{signature} // {component.description}"
        for signature, component in zip(signatures, COMPONENTS)
    )


def component_catalog() -> Dict[str, Any]:
    return {
        "protocol_version": "1.0-internal",
        "catalog_ids": [BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID],
        "upstream_basic_catalog_id": UPSTREAM_BASIC_CATALOG_ID,
        "components": [
            {
                "name": component.name,
                "catalog": component.catalog,
                "description": component.description,
                "properties": [
                    {
                        "name": item.name,
                        "kind": item.kind,
                        "required": item.required,
                        **({"enum": list(item.enum)} if item.enum else {}),
                    }
                    for item in component.properties
                ],
            }
            for component in COMPONENTS
        ],
        "functions": list(FUNCTIONS),
    }
