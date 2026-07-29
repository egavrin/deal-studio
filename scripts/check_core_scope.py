#!/usr/bin/env python3
"""Fails when removed assistant subsystems leak back into the core product."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_INTENTS = {
    "GET_CURRENT_TIME",
    "GET_WEATHER",
    "SET_TIMER",
    "SET_ALARM",
    "CREATE_REMINDER",
    "CREATE_NOTE",
    "CALCULATE",
    "OPEN_APP",
    "DIAL_PHONE",
    "COMPOSE_MESSAGE",
    "COMPOSE_EMAIL",
    "START_NAVIGATION",
    "CREATE_CALENDAR_EVENT",
    "CONTROL_MEDIA",
    "SET_VOLUME",
    "OPEN_SETTING",
    "OPEN_URL",
    "HELP",
    "WEB_SEARCH",
    "WEB_RESEARCH",
    "UNKNOWN",
}
EXPECTED_WIDGETS = {
    "WEATHER_CARD",
    "TIMER_CARD",
    "ALARM_CARD",
    "REMINDER_CARD",
    "NOTE_CARD",
    "CALCULATOR_CARD",
    "OPEN_APP_CARD",
    "HELP_CARD",
    "CLARIFICATION_CARD",
    "PERMISSION_CARD",
    "ERROR_CARD",
    "GENERIC_ANSWER_CARD",
    "RESEARCH_CARD",
    "ACTION_CONFIRMATION_CARD",
}
EXPECTED_MODULES = {
    ":app",
    ":benchmark",
    ":core",
    ":deepseek-connector",
}
EXPECTED_PERMISSIONS = {
    "android.permission.RECORD_AUDIO",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "com.android.alarm.permission.SET_ALARM",
    "android.permission.READ_ASSIST_STRUCTURE_SCREEN_CONTENT",
}
FORBIDDEN_PATHS = (
    "appfunctions-experiment",
    "cloud-widget-connector",
    "app/src/main/cpp",
    "third_party/whisper.cpp",
    "training/widget_planner",
    "core/src/main/kotlin/com/offlineassistant/core/widgets",
    "app/src/main/java/com/offlineassistant/app/shell",
    "app/src/main/java/com/offlineassistant/app/widgets/planning",
)
FORBIDDEN_PRODUCTION_TERMS = re.compile(
    r"\b(qwen|llama|whisper|gemma|compose_widget|generatedWidget|appFunctions|"
    r"brainDump|personalMemory|routine)\b",
    re.IGNORECASE,
)
FORBIDDEN_BUILD_TERMS = re.compile(
    r"androidx\.appfunctions|androidx\.room|com\.google\.devtools\.ksp|"
    r"\b(qwen|llama|whisper|gemma)\b",
    re.IGNORECASE,
)


def constants(path: Path, prefix: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    return set(re.findall(rf"const val ({prefix}[A-Z0-9_]*)\s*=", text))


def main() -> int:
    failures: list[str] = []
    for relative in FORBIDDEN_PATHS:
        if (ROOT / relative).exists():
            failures.append(f"forbidden path exists: {relative}")

    android = "{http://schemas.android.com/apk/res/android}"
    manifest = ElementTree.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    actual_permissions = {
        element.attrib[f"{android}name"]
        for element in manifest.findall("uses-permission")
    }
    if actual_permissions != EXPECTED_PERMISSIONS:
        failures.append(
            f"manifest permission allowlist mismatch: expected={sorted(EXPECTED_PERMISSIONS)} "
            f"actual={sorted(actual_permissions)}"
        )
    services = {
        element.attrib[f"{android}name"]: element
        for element in manifest.find("application").findall("service")
    }
    expected_service_guards = {
        ".assistant.OfflineAssistantVoiceInteractionService": {
            "exported": "true",
            "permission": "android.permission.BIND_VOICE_INTERACTION",
            "process": ":assistant_entry",
        },
        ".assistant.OfflineAssistantSessionService": {
            "exported": "false",
            "permission": "android.permission.BIND_VOICE_INTERACTION",
        },
        ".assistant.OfflineAssistantRecognitionService": {
            "exported": "true",
            "permission": "android.permission.BIND_SPEECH_RECOGNITION_SERVICE",
        },
    }
    for service_name, expected_attributes in expected_service_guards.items():
        service = services.get(service_name)
        if service is None:
            failures.append(f"required assistant service missing: {service_name}")
            continue
        for attribute, expected_value in expected_attributes.items():
            actual_value = service.attrib.get(f"{android}{attribute}")
            if actual_value != expected_value:
                failures.append(
                    f"assistant service {service_name} has {attribute}={actual_value!r}; "
                    f"expected {expected_value!r}"
                )

    production_roots = (
        ROOT / "app/src/main/java",
        ROOT / "core/src/main/kotlin",
        ROOT / "deepseek-connector/src/main",
        ROOT / "training/rubert",
    )
    for source_root in production_roots:
        for path in source_root.rglob("*"):
            if path.suffix not in {".kt", ".kts", ".xml"}:
                continue
            match = FORBIDDEN_PRODUCTION_TERMS.search(path.read_text(encoding="utf-8"))
            if match:
                failures.append(
                    f"forbidden production term {match.group(0)!r}: {path.relative_to(ROOT)}"
                )
    for path in (ROOT / "app/proguard-rules.pro",):
        match = FORBIDDEN_PRODUCTION_TERMS.search(path.read_text(encoding="utf-8"))
        if match:
            failures.append(
                f"forbidden production term {match.group(0)!r}: {path.relative_to(ROOT)}"
            )

    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    actual_modules = set(re.findall(r'include\("([^"]+)"\)', settings))
    if actual_modules != EXPECTED_MODULES:
        failures.append(
            f"Gradle module allowlist mismatch: expected={sorted(EXPECTED_MODULES)} "
            f"actual={sorted(actual_modules)}"
        )

    build_files = (
        ROOT / "settings.gradle.kts",
        ROOT / "build.gradle.kts",
        ROOT / "app/build.gradle.kts",
        ROOT / "core/build.gradle.kts",
        ROOT / "deepseek-connector/build.gradle.kts",
        ROOT / "gradle/libs.versions.toml",
        ROOT / "app/proguard-rules.pro",
    )
    for path in build_files:
        match = FORBIDDEN_BUILD_TERMS.search(path.read_text(encoding="utf-8"))
        if match:
            failures.append(
                f"forbidden build term {match.group(0)!r}: {path.relative_to(ROOT)}"
            )

    model_assets = ROOT / "app/src/main/assets/models"
    actual_model_directories = {path.name for path in model_assets.iterdir() if path.is_dir()}
    if actual_model_directories != {"tone_ru"}:
        failures.append(
            f"packaged ASR assets mismatch: expected=['tone_ru'] "
            f"actual={sorted(actual_model_directories)}"
        )

    intent_file = ROOT / "core/src/main/kotlin/com/offlineassistant/core/nlu/NluModels.kt"
    actual_intents = constants(intent_file, "")
    actual_intents = {
        value
        for value in actual_intents
        if value not in {"RUBERT_TINY2", "STUB", "UNAVAILABLE"}
    }
    if actual_intents != EXPECTED_INTENTS:
        failures.append(
            f"intent allowlist mismatch: expected={sorted(EXPECTED_INTENTS)} "
            f"actual={sorted(actual_intents)}"
        )

    widget_file = ROOT / "core/src/main/kotlin/com/offlineassistant/core/contracts/WidgetTypes.kt"
    actual_widgets = constants(widget_file, "")
    if actual_widgets != EXPECTED_WIDGETS:
        failures.append(
            f"widget allowlist mismatch: expected={sorted(EXPECTED_WIDGETS)} "
            f"actual={sorted(actual_widgets)}"
        )

    labels = ROOT / "models/generated/rubert/intent_labels.txt"
    if labels.exists():
        actual_labels = set(labels.read_text(encoding="utf-8").splitlines())
        expected_labels = {
            "get_current_time",
            "get_weather",
            "set_timer",
            "set_alarm",
            "create_reminder",
            "create_note",
            "calculate",
            "open_app",
            "dial_phone",
            "compose_message",
            "compose_email",
            "start_navigation",
            "create_calendar_event",
            "control_media",
            "set_volume",
            "open_setting",
            "open_url",
            "help",
            "web_search",
            "web_research",
            "unknown",
        }
        if actual_labels != expected_labels:
            failures.append("generated RuBERT bundle contains labels outside the core allowlist")

    if failures:
        print("Core scope check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("Core scope check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
