#!/usr/bin/env python3
"""Run Java-only EVDial regressions with stubbed Android types.

Compiles only isolated pure logic (no live Android, phone access, network or secrets).
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/ru/autonatoke/evdial"
TESTS = ROOT / "tests"

def main() -> None:
    stubs = sorted((TESTS / "profile-stubs").rglob("*.java"))
    sources = [
        JAVA / "UiProfiles.java", JAVA / "SpeakerRequest.java", JAVA / "UpdateRules.java",
        TESTS / "UiProfilesTest.java", TESTS / "SpeakerRequestTest.java",
        TESTS / "UpdateRulesTest.java",
    ]
    assert all(f.is_file() for f in sources), "Missing test or production sources"
    assert len(stubs) >= 5, "Missing Android model stubs"

    with tempfile.TemporaryDirectory(prefix="evdial-jvm-") as temp:
        directory = Path(temp)
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(directory), *map(str, stubs + sources)],
            cwd=ROOT, check=True, timeout=40,
        )
        for name in ("UiProfilesTest", "SpeakerRequestTest", "UpdateRulesTest"):
            subprocess.run(
                ["java", "-cp", str(directory), "ru.autonatoke.evdial." + name],
                cwd=ROOT, check=True, timeout=25,
            )
    print("PASS: three JVM behavior suites; no Android or signing required.")


if __name__ == "__main__":
    main()
