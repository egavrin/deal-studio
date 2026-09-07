"""Deterministic coverage scheduler for generated UI teacher requests."""

from __future__ import annotations

import random
from collections import Counter
from dataclasses import asdict, dataclass
from typing import Any, Dict, Iterable, List, Sequence, Tuple

from .catalog import COMPONENTS


@dataclass(frozen=True)
class Scenario:
    name: str
    domain: str
    task_kind: str
    prompts: Tuple[str, str, str]
    required_components: Tuple[str, ...]
    interaction: str


@dataclass(frozen=True)
class TaskSeed:
    seed_id: str
    request: str
    locale: str
    domain: str
    task_kind: str
    viewport: str
    theme: str
    state: str
    density: str
    interaction: str
    focal_component: str
    required_components: Tuple[str, ...]
    target_nodes_min: int
    target_nodes_max: int

    def to_dict(self) -> Dict[str, Any]:
        value = asdict(self)
        value["required_components"] = list(self.required_components)
        return value


SCENARIOS: Tuple[Scenario, ...] = (
    Scenario(
        "morning_agenda",
        "productivity",
        "timeline",
        (
            "Show my three most important events tomorrow morning with a calendar action.",
            "Покажи три самых важных события на завтрашнее утро и кнопку календаря.",
            "Show three важные встречи tomorrow morning with a calendar action.",
        ),
        ("Column", "Row", "Text", "Badge", "Timeline", "KeyValue", "Button", "Card"),
        "action",
    ),
    Scenario(
        "research_answer",
        "research",
        "cited_answer",
        (
            "Summarize the evidence on urban heat islands with sources and image results.",
            "Кратко покажи данные о городских островах тепла, источники и изображения.",
            "Summarize данные about urban heat islands with источники and images.",
        ),
        ("Column", "Text", "Badge", "SourceList", "ImageGallery", "Progress", "Button"),
        "action",
    ),
    Scenario(
        "analytics_dashboard",
        "data_display",
        "dashboard",
        (
            "Build a compact sales dashboard with metrics, a trend chart and a sortable table.",
            "Собери компактный дашборд продаж с метриками, графиком и таблицей.",
            "Build a компактный sales dashboard with metrics, график and table.",
        ),
        ("Column", "Text", "Grid", "Metric", "Chart", "DataTable", "Progress", "KeyValue"),
        "read_only",
    ),
    Scenario(
        "travel_planner",
        "travel",
        "map_itinerary",
        (
            "Show a two-day city itinerary with a map preview, times and a route action.",
            "Покажи маршрут по городу на два дня с картой, временем и кнопкой маршрута.",
            "Show a two-day маршрут with a map preview, время and route action.",
        ),
        ("Column", "Text", "MapPreview", "Timeline", "Badge", "Button", "Card"),
        "action",
    ),
    Scenario(
        "profile_form",
        "forms",
        "validated_form",
        (
            "Create a profile form with name, email, preferences, range, consent and appointment time.",
            "Создай форму профиля с именем, почтой, настройками, диапазоном, согласием и временем встречи.",
            "Create a профиль form with email, preferences, диапазон and appointment time.",
        ),
        ("Card", "Column", "Text", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput", "Divider", "Button"),
        "form",
    ),
    Scenario(
        "media_collection",
        "media",
        "media_tabs",
        (
            "Present a museum collection with image, video and audio tabs plus accessible descriptions.",
            "Покажи музейную коллекцию с вкладками изображения, видео и аудио и описаниями.",
            "Present a museum коллекция with image, video and audio вкладки.",
        ),
        ("Column", "Row", "Icon", "Text", "Tabs", "Image", "Video", "AudioPlayer"),
        "media",
    ),
    Scenario(
        "task_list",
        "productivity",
        "dynamic_list",
        (
            "Show a dynamic task list with completion controls and a useful empty state.",
            "Покажи динамический список задач с отметкой выполнения и пустым состоянием.",
            "Show a dynamic список задач with completion controls and empty state.",
        ),
        ("List", "Column", "Text", "CheckBox", "Badge"),
        "form",
    ),
    Scenario(
        "code_explanation",
        "education",
        "code_lesson",
        (
            "Explain a short sorting example with a safe code block, copy action and details modal.",
            "Объясни короткий пример сортировки с блоком кода, копированием и окном деталей.",
            "Explain a sorting пример with safe code, copy action and детали modal.",
        ),
        ("Column", "Text", "CodeBlock", "Modal", "Card", "Button"),
        "action",
    ),
    Scenario(
        "interactive_canvas",
        "generated_app",
        "interactive_surface",
        (
            "Create a bounded interactive physics playground with a reset action and status.",
            "Создай ограниченную интерактивную физическую сцену со сбросом и статусом.",
            "Create an interactive физическая сцена with reset and status.",
        ),
        ("Column", "Text", "Badge", "InteractiveSurface", "Button"),
        "interactive",
    ),
    Scenario(
        "permission_state",
        "device_control",
        "permission_modal",
        (
            "Explain a notification permission with allow, not now and a non-blocking modal detail.",
            "Объясни разрешение уведомлений с действиями разрешить, позже и подробностями.",
            "Explain notification permission with разрешить, later and details modal.",
        ),
        ("Card", "Column", "Row", "Icon", "Text", "Badge", "Modal", "Button"),
        "action",
    ),
    Scenario(
        "weather_comparison",
        "weather",
        "forecast_comparison",
        (
            "Compare today's hourly weather with tomorrow using metrics, a chart and source status.",
            "Сравни почасовую погоду сегодня и завтра с метриками, графиком и источником.",
            "Compare today's погода with tomorrow using metrics and график.",
        ),
        ("Column", "Row", "Text", "Metric", "Chart", "Badge", "KeyValue"),
        "read_only",
    ),
    Scenario(
        "unsupported_fallback",
        "unsupported",
        "safe_fallback",
        (
            "Request an unsupported capability and show a useful explanation with safe alternatives.",
            "Покажи понятный ответ для неподдерживаемой возможности и безопасные альтернативы.",
            "Show a useful fallback for an неподдерживаемый request with safe alternatives.",
        ),
        ("Card", "Column", "Icon", "Text", "Badge", "Button"),
        "action",
    ),
)


LOCALES = ("en-US",) * 6 + ("ru-RU",) * 3 + ("mixed",)
VIEWPORTS = ("phone_compact", "phone_compact", "foldable", "tablet", "large_font")
THEMES = ("light", "dark", "dynamic", "high_contrast")
STATES = ("populated", "loading", "partial", "empty", "stale", "offline", "denied", "error")
DENSITIES = ("compact", "comfortable", "dense")
NODE_BANDS = (
    (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8),
    (9, 20), (9, 20), (9, 20), (9, 20), (9, 20), (9, 20), (9, 20), (9, 20), (9, 20),
    (21, 48), (21, 48), (21, 48), (21, 48),
    (49, 64),
)


def build_task_seeds(count: int, seed: int = 20260825) -> List[TaskSeed]:
    if count < 1:
        raise ValueError("count must be positive")
    rng = random.Random(seed)
    component_names = [item.name for item in COMPONENTS]
    scenarios_by_component = {
        name: [scenario for scenario in SCENARIOS if name in scenario.required_components]
        for name in component_names
    }
    tasks = []
    for index in range(count):
        focal = component_names[index % len(component_names)]
        candidates = scenarios_by_component[focal]
        scenario = candidates[(index // len(component_names)) % len(candidates)]
        locale = LOCALES[index % len(LOCALES)]
        locale_index = {"en-US": 0, "ru-RU": 1, "mixed": 2}[locale]
        node_min, node_max = NODE_BANDS[index % len(NODE_BANDS)]
        required = list(scenario.required_components)
        if focal not in required:
            required.append(focal)
        rng.shuffle(required)
        minimum = max(node_min, len(required))
        maximum = max(node_max, min(64, len(required) + 4))
        for band_maximum in (8, 20, 48, 64):
            if maximum >= minimum:
                break
            maximum = band_maximum
        tasks.append(
            TaskSeed(
                seed_id=f"ui-{seed}-{index:06d}",
                request=scenario.prompts[locale_index],
                locale=locale,
                domain=scenario.domain,
                task_kind=scenario.task_kind,
                viewport=VIEWPORTS[(index // 2) % len(VIEWPORTS)],
                theme=THEMES[(index // 3) % len(THEMES)],
                state=STATES[(index // 5) % len(STATES)],
                density=DENSITIES[(index // 7) % len(DENSITIES)],
                interaction=scenario.interaction,
                focal_component=focal,
                required_components=tuple(required),
                target_nodes_min=minimum,
                target_nodes_max=maximum,
            )
        )
    return tasks


def coverage_report(tasks: Sequence[TaskSeed]) -> Dict[str, Any]:
    focal = Counter(task.focal_component for task in tasks)
    required = Counter(
        component for task in tasks for component in set(task.required_components)
    )
    pairs = Counter()
    for task in tasks:
        names = sorted(set(task.required_components))
        for index, left in enumerate(names):
            for right in names[index + 1 :]:
                pairs[f"{left}+{right}"] += 1
    return {
        "task_count": len(tasks),
        "focal_components": dict(sorted(focal.items())),
        "required_components": dict(sorted(required.items())),
        "component_pairs": dict(sorted(pairs.items())),
        "locales": dict(sorted(Counter(task.locale for task in tasks).items())),
        "domains": dict(sorted(Counter(task.domain for task in tasks).items())),
        "viewports": dict(sorted(Counter(task.viewport for task in tasks).items())),
        "themes": dict(sorted(Counter(task.theme for task in tasks).items())),
        "states": dict(sorted(Counter(task.state for task in tasks).items())),
    }
