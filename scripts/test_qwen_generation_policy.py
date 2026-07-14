#!/usr/bin/env python3
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scripts" / "qwen_generation_policy_test.cpp"
INCLUDE_DIR = ROOT / "app" / "src" / "main" / "cpp"


def test_native_qwen_generation_policy():
    compiler = shutil.which("c++")
    assert compiler, "A C++17 compiler is required for the native Qwen policy host test"
    with tempfile.TemporaryDirectory(prefix="offline-assistant-qwen-policy-") as directory:
        binary = Path(directory) / "qwen-generation-policy-test"
        subprocess.run(
            [
                compiler,
                "-std=c++17",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-I",
                str(INCLUDE_DIR),
                str(SOURCE),
                "-o",
                str(binary),
            ],
            check=True,
        )
        subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    test_native_qwen_generation_policy()
