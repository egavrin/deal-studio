#!/usr/bin/env python3
"""Explicit local debug bootstrap; never source .env or print credential values."""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def read_allowlisted_values(path):
    allowed = {"TYPESAFE_API_KEY", "DEEPSEEK_API_KEY"}
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        name, separator, value = line.strip().partition("=")
        if name.startswith("export "):
            name = name[7:].strip()
        if not separator or name.strip() not in allowed:
            continue
        name = name.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        # Literal parsing only: no shell expansion, escape decoding, eval, or source.
        if value and not any(character.isspace() for character in value):
            values[name] = value
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace-bootstrap", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        values = read_allowlisted_values(Path.home() / ".env")
        if not values:
            raise ValueError("No allowlisted bootstrap values")
        environment = os.environ.copy()
        # Environment, never command-line arguments or Gradle property files.
        environment["DEAL_STUDIO_BOOTSTRAP_DEEPSEEK_API_KEY"] = values.get("DEEPSEEK_API_KEY", "")
        environment["DEAL_STUDIO_BOOTSTRAP_JEV_API_KEY"] = values.get("TYPESAFE_API_KEY", "")
        environment["DEAL_STUDIO_BOOTSTRAP_CEREBRAS_API_KEY"] = ""
        subprocess.run([str(root / "gradlew"), "--no-daemon", "--no-configuration-cache", ":app:assembleDebug"],
                       cwd=root, env=environment, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["adb", "install", "-r", "--user", "0", str(root / "app/build/outputs/apk/debug/app-debug.apk")],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["adb", "shell", "am", "start", "--user", "0", "-n",
                        "com.dealstudio.app.debug/com.offlineassistant.app.settings.CredentialBootstrapActivity",
                        "-a", "com.dealstudio.app.debug.BOOTSTRAP_CREDENTIALS", "--ez", "replace_bootstrap",
                        str(args.replace_bootstrap).lower()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except (OSError, ValueError, subprocess.CalledProcessError):
        print("Local debug bootstrap failed; no credential values were printed.", file=sys.stderr)
        return 1
    print("Local debug build installed and explicit bootstrap requested; user-owned and cleared keys are preserved.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
