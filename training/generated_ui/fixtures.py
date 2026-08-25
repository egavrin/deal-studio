"""Hand-authored catalog fixtures used as immutable compiler tests."""

from __future__ import annotations

from typing import Any, Dict, List


def binding(path: str) -> Dict[str, str]:
    return {"binding": path}


def event(name: str, **context: Any) -> Dict[str, Any]:
    return {"event": name, "context": context}


def action(name: str, parameters: List[Dict[str, Any]] = None, confirmation: bool = False) -> Dict[str, Any]:
    return {
        "name": name,
        "description": name.replace("_", " ").capitalize(),
        "confirmation_required": confirmation,
        "parameters": parameters or [],
    }


def parameter(name: str, kind: str = "string", required: bool = True) -> Dict[str, Any]:
    return {"name": name, "type": kind, "required": required}


def base(
    request: str,
    domain: str,
    task_kind: str,
    data_model: Dict[str, Any],
    components: List[Dict[str, Any]],
    root_id: str,
    *,
    actions: List[Dict[str, Any]] = None,
    locale: str = "en-US",
) -> Dict[str, Any]:
    return {
        "schema_version": 1,
        "request": request,
        "locale": locale,
        "domain": domain,
        "task_kind": task_kind,
        "viewport": "phone_compact",
        "theme": "light",
        "state": "populated",
        "density": "comfortable",
        "data_model": data_model,
        "actions": actions or [],
        "components": components,
        "root_id": root_id,
        "acceptance": ["The surface is readable and every declared action resolves."],
    }


def morning_agenda() -> Dict[str, Any]:
    return base(
        "Show my three most important events tomorrow morning.",
        "productivity",
        "timeline",
        {
            "title": "Tomorrow morning",
            "date": "2026-08-26",
            "events": [
                {"time": "08:30", "title": "Design review"},
                {"time": "10:00", "title": "Model benchmark"},
                {"time": "11:30", "title": "Project sync"},
            ],
        },
        [
            {"id": "title", "component": "Text", "text": binding("/title"), "variant": "h2"},
            {"id": "status", "component": "Badge", "text": "3 events", "tone": "info"},
            {"id": "header", "component": "Row", "children": ["title", "status"], "justify": "spaceBetween", "align": "center"},
            {"id": "event_row", "component": "KeyValue", "label": binding("time"), "value": binding("title"), "icon": "calendar"},
            {"id": "empty", "component": "Text", "text": "No morning events", "tone": "muted"},
            {"id": "timeline", "component": "Timeline", "items": binding("/events"), "template": "event_row", "empty_state": "empty", "description": "Three calendar events ordered by start time"},
            {"id": "rule", "component": "Divider", "tone": "soft"},
            {"id": "calendar_label", "component": "Text", "text": "Open calendar", "variant": "label"},
            {"id": "calendar_button", "component": "Button", "child": "calendar_label", "action": event("open_calendar", date=binding("/date")), "variant": "outline"},
            {"id": "agenda_root", "component": "Column", "children": ["header", "timeline", "rule", "calendar_button"], "gap": "sm"},
        ],
        "agenda_root",
        actions=[action("open_calendar", [parameter("date", "datetime")])],
    )


def research_result() -> Dict[str, Any]:
    return base(
        "Summarize urban heat island evidence with sources and images.",
        "research",
        "cited_answer",
        {
            "answer": "Tree cover and reflective surfaces consistently reduce local heat exposure.",
            "progress": 1.0,
            "selected_image_id": "image-1",
            "selected_source_url": "https://example.invalid/research/source-1",
            "images": [
                {
                    "id": "image-1",
                    "url": "https://assets.example.invalid/heat-map.jpg",
                    "description": "Illustrative city heat map",
                }
            ],
            "sources": [
                {
                    "title": "Urban cooling review",
                    "url": "https://example.invalid/research/source-1",
                    "publisher": "Example Research",
                }
            ],
        },
        [
            {"id": "research_title", "component": "Text", "text": "Evidence brief", "variant": "h2"},
            {"id": "research_status", "component": "Badge", "text": "Sources checked", "tone": "positive"},
            {"id": "research_answer", "component": "Text", "text": binding("/answer"), "variant": "body"},
            {"id": "research_progress", "component": "Progress", "value": binding("/progress"), "max": 1, "label": "Research complete", "state": "complete"},
            {"id": "gallery", "component": "ImageGallery", "items": binding("/images"), "columns": 2, "description": "Attributed image results about city heat", "select_action": event("select_image", image_id=binding("/selected_image_id"))},
            {"id": "sources", "component": "SourceList", "sources": binding("/sources"), "title": "Sources", "open_action": {"client_function": "openUrl", "args": [binding("/selected_source_url")]}},
            {"id": "research_root", "component": "Column", "children": ["research_title", "research_status", "research_answer", "research_progress", "gallery", "sources"], "gap": "md"},
        ],
        "research_root",
        actions=[
            action("select_image", [parameter("image_id")]),
        ],
    )


def analytics_dashboard() -> Dict[str, Any]:
    return base(
        "Build a sales dashboard with metrics, a trend chart and a table.",
        "data_display",
        "dashboard",
        {
            "revenue": "$128k",
            "orders": 842,
            "conversion": "4.8%",
            "completion": 0.84,
            "series": [{"x": "Mon", "y": 18}, {"x": "Tue", "y": 27}],
            "rows": [{"region": "North", "revenue": 48000}, {"region": "West", "revenue": 39000}],
            "sort": "revenue_desc",
        },
        [
            {"id": "dashboard_title", "component": "Text", "text": "Sales overview", "variant": "h2"},
            {"id": "revenue_metric", "component": "Metric", "label": "Revenue", "value": binding("/revenue"), "trend": "+12%", "tone": "positive"},
            {"id": "orders_metric", "component": "Metric", "label": "Orders", "value": binding("/orders")},
            {"id": "conversion_metric", "component": "Metric", "label": "Conversion", "value": binding("/conversion")},
            {"id": "metric_grid", "component": "Grid", "children": ["revenue_metric", "orders_metric", "conversion_metric"], "columns": 3, "gap": "sm"},
            {"id": "trend_chart", "component": "Chart", "series": binding("/series"), "variant": "line", "title": "Seven-day revenue", "description": "Line chart showing revenue rising across the week", "x_label": "Day", "y_label": "Revenue"},
            {"id": "region_table", "component": "DataTable", "columns": [{"key": "region", "label": "Region"}, {"key": "revenue", "label": "Revenue", "align": "end"}], "rows": binding("/rows"), "sort": binding("/sort"), "description": "Revenue grouped by region"},
            {"id": "target_progress", "component": "Progress", "value": binding("/completion"), "max": 1, "label": "Monthly target", "state": "determinate"},
            {"id": "updated_fact", "component": "KeyValue", "label": "Updated", "value": "Just now", "icon": "sync"},
            {"id": "dashboard_root", "component": "Column", "children": ["dashboard_title", "metric_grid", "trend_chart", "region_table", "target_progress", "updated_fact"], "gap": "md"},
        ],
        "dashboard_root",
    )


def validated_form() -> Dict[str, Any]:
    return base(
        "Create a complete profile form with validation and appointment time.",
        "forms",
        "validated_form",
        {
            "user_id": "user-42",
            "name": "",
            "email": "",
            "consent": False,
            "plan": "standard",
            "notifications": 3,
            "appointment": "2026-08-27T10:00:00+03:00",
        },
        [
            {"id": "form_title", "component": "Text", "text": "Profile", "variant": "h2"},
            {"id": "name_field", "component": "TextField", "label": "Name", "value": binding("/name"), "placeholder": "Your name", "input_type": "text", "validation": [{"function": "required", "args": []}]},
            {"id": "email_field", "component": "TextField", "label": "Email", "value": binding("/email"), "placeholder": "name@example.com", "input_type": "email", "validation": [{"function": "required", "args": []}, {"function": "email", "args": []}]},
            {"id": "consent_field", "component": "CheckBox", "label": "Allow notifications", "checked": binding("/consent")},
            {"id": "plan_field", "component": "ChoicePicker", "label": "Plan", "value": binding("/plan"), "options": [{"label": "Standard", "value": "standard"}, {"label": "Pro", "value": "pro"}]},
            {"id": "notification_field", "component": "Slider", "label": "Daily reminders", "value": binding("/notifications"), "min": 0, "max": 8, "step": 1},
            {"id": "appointment_field", "component": "DateTimeInput", "label": "Appointment", "value": binding("/appointment"), "mode": "datetime"},
            {"id": "form_rule", "component": "Divider", "tone": "soft"},
            {"id": "submit_label", "component": "Text", "text": "Save profile", "variant": "label"},
            {"id": "submit_button", "component": "Button", "child": "submit_label", "action": event("save_profile", user_id=binding("/user_id")), "variant": "filled"},
            {"id": "form_column", "component": "Column", "children": ["form_title", "name_field", "email_field", "consent_field", "plan_field", "notification_field", "appointment_field", "form_rule", "submit_button"], "gap": "sm"},
            {"id": "form_root", "component": "Card", "child": "form_column", "tone": "plain", "padding": "md"},
        ],
        "form_root",
        actions=[action("save_profile", [parameter("user_id")], confirmation=True)],
    )


def media_collection() -> Dict[str, Any]:
    return base(
        "Present a museum collection with accessible image, video and audio tabs.",
        "media",
        "media_tabs",
        {
            "selected_tab": "image",
            "destination": "museum-entrance",
            "markers": [{"lat": 55.75, "lon": 37.61, "label": "Museum"}],
        },
        [
            {"id": "museum_icon", "component": "Icon", "name": "museum", "description": "Museum collection", "size": "lg"},
            {"id": "museum_title", "component": "Text", "text": "Collection highlights", "variant": "h2"},
            {"id": "museum_header", "component": "Row", "children": ["museum_icon", "museum_title"], "gap": "sm", "align": "center"},
            {"id": "museum_image", "component": "Image", "url": "https://assets.example.invalid/artifact.jpg", "description": "Bronze artifact on a neutral background", "fit": "contain", "aspect": "landscape"},
            {"id": "museum_video", "component": "Video", "url": "https://assets.example.invalid/exhibit.mp4", "description": "Short curator walkthrough", "poster_url": "https://assets.example.invalid/exhibit-poster.jpg", "controls": True},
            {"id": "museum_audio", "component": "AudioPlayer", "url": "https://assets.example.invalid/audio-guide.mp3", "title": "Curator audio guide", "description": "Five-minute spoken introduction"},
            {"id": "museum_tabs", "component": "Tabs", "tabs": [{"label": "Image", "child": "museum_image", "value": "image"}, {"label": "Video", "child": "museum_video", "value": "video"}, {"label": "Audio", "child": "museum_audio", "value": "audio"}], "selected": binding("/selected_tab")},
            {"id": "museum_map", "component": "MapPreview", "markers": binding("/markers"), "provider": "mock", "description": "Map showing the museum entrance", "open_action": event("open_route", destination=binding("/destination"))},
            {"id": "media_root", "component": "Column", "children": ["museum_header", "museum_tabs", "museum_map"], "gap": "md"},
        ],
        "media_root",
        actions=[action("open_route", [parameter("destination")])],
    )


def code_lesson() -> Dict[str, Any]:
    return base(
        "Explain a safe sorting example with code and a details modal.",
        "education",
        "code_lesson",
        {},
        [
            {"id": "lesson_title", "component": "Text", "text": "Stable sorting", "variant": "h2"},
            {"id": "code", "component": "CodeBlock", "language": "kotlin", "content": "items.sortedBy { it.rank }", "copy_action": event("copy_code"), "description": "Kotlin expression sorting items by rank"},
            {"id": "details_label", "component": "Text", "text": "Why it works", "variant": "label"},
            {"id": "details_button", "component": "Button", "child": "details_label", "action": event("show_details"), "variant": "outline"},
            {"id": "details_text", "component": "Text", "text": "A stable sort preserves the order of equal elements.", "variant": "body"},
            {"id": "details_card", "component": "Card", "child": "details_text", "tone": "soft", "padding": "md"},
            {"id": "details_modal", "component": "Modal", "trigger": "details_button", "content": "details_card"},
            {"id": "lesson_root", "component": "Column", "children": ["lesson_title", "code", "details_modal"], "gap": "sm"},
        ],
        "lesson_root",
        actions=[action("copy_code"), action("show_details")],
    )


def dynamic_task_list() -> Dict[str, Any]:
    return base(
        "Show a dynamic task list with completion state and an empty state.",
        "productivity",
        "dynamic_list",
        {"tasks": [{"title": "Review screenshots", "done": False}, {"title": "Run eval", "done": True}]},
        [
            {"id": "task_title", "component": "Text", "text": binding("title"), "variant": "body"},
            {"id": "task_done", "component": "CheckBox", "label": "Completed", "checked": binding("done")},
            {"id": "task_row", "component": "Column", "children": ["task_title", "task_done"], "gap": "xs"},
            {"id": "task_empty", "component": "Badge", "text": "Nothing pending", "tone": "positive"},
            {"id": "task_root", "component": "List", "items": binding("/tasks"), "template": "task_row", "empty_state": "task_empty", "direction": "vertical"},
        ],
        "task_root",
    )


def interactive_surface() -> Dict[str, Any]:
    return base(
        "Create a bounded interactive physics playground with reset.",
        "generated_app",
        "interactive_surface",
        {"module_id": "sha256:fixture-module"},
        [
            {"id": "play_title", "component": "Text", "text": "Physics playground", "variant": "h2"},
            {"id": "play_status", "component": "Badge", "text": "Local sandbox", "tone": "info"},
            {"id": "play_surface", "component": "InteractiveSurface", "module_id": "fixture-module", "aspect": "wide", "input_mode": "realtime", "description": "Interactive bounded scene controlled by pointer input"},
            {"id": "reset_label", "component": "Text", "text": "Reset", "variant": "label"},
            {"id": "reset_button", "component": "Button", "child": "reset_label", "action": event("reset_module", module_id=binding("/module_id")), "variant": "outline"},
            {"id": "play_column", "component": "Column", "children": ["play_title", "play_status", "play_surface", "reset_button"], "gap": "sm"},
            {"id": "play_root", "component": "Card", "child": "play_column", "tone": "dark", "padding": "md"},
        ],
        "play_root",
        actions=[action("reset_module", [parameter("module_id")])],
    )


def all_blueprints() -> List[Dict[str, Any]]:
    return [
        morning_agenda(),
        research_result(),
        analytics_dashboard(),
        validated_form(),
        media_collection(),
        code_lesson(),
        dynamic_task_list(),
        interactive_surface(),
    ]
