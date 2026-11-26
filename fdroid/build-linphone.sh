#!/usr/bin/env bash
# Candidate source recipe: no source fetch, no prebuilt Linphone AAR fallback.
# Usage:
#   bash fdroid/build-linphone.sh prepare /absolute/linphone-sdk /absolute/work-dir
#   LINPHONE_GRADLE=/installed/gradle-8.11.1/bin/gradle \
#   ANDROID_NDK_HOME=/installed/ndk/27.x ANDROID_HOME=/installed/android-sdk \
#   bash fdroid/build-linphone.sh build /absolute/linphone-sdk /absolute/work-dir
#
# prepare requires a clean, fully initialized checkout and records its patches.
# build requires that prepared tree, Python >=3.9, Java 17, CMake >=3.22,
# NDK27, SDK platform31 and installed Gradle8.11.1.
# LINPHONE_JOBS defaults to2 and is limited to1..8.
# Gradle --offline is on by default; all dependencies must already be cached.
# Set FDROID_GRADLE_OFFLINE=0 only for dependency resolution in the build
# runner's permitted stage. Network isolation is provided by that runner.
# Build output goes to WORK/build; successful builds write WORK/provenance.json.
# A fresh work directory is required after any failed native build.
set -euo pipefail
exec python3 - "${BASH_SOURCE[0]}" "$@" <<'PY'
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


HELPER = Path(sys.argv[1]).resolve()
SPEC_PATH = HELPER.with_name("SDK_SOURCE.json")
SPEC = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
WRAPPER = "cmake/Android/gradlew"
WRAPPER_TEXT = '''#!/bin/sh
# EVDial source-build adapter; never downloads a Gradle distribution or SDK AAR.
set -eu
: "${LINPHONE_GRADLE:?Set LINPHONE_GRADLE to installed Gradle 8.11.1}"
: "${LINPHONE_JOBS:?Set LINPHONE_JOBS to the validated worker limit}"
case "${FDROID_GRADLE_OFFLINE:-1}" in
  1) set -- --offline "$@" ;;
  0) ;;
  *) echo "FDROID_GRADLE_OFFLINE must be 0 or 1" >&2; exit 2 ;;
esac
exec "$LINPHONE_GRADLE" --no-daemon --max-workers="$LINPHONE_JOBS" "$@"
'''


def fail(message):
    raise ValueError(message)


def run(command, cwd=None, capture=True, env=None):
    result = subprocess.run(command, cwd=cwd, env=env, check=True,
                            stdout=subprocess.PIPE if capture else sys.stderr,
                            stderr=subprocess.STDOUT if capture else sys.stderr,
                            text=True)
    return result.stdout.strip() if capture else None


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def git(sdk, *args):
    return run(["git", "-C", str(sdk), *args])


def validate_source(sdk):
    if Path(git(sdk, "rev-parse", "--show-toplevel")).resolve() != sdk:
        fail("SDK path must be the checkout root.")
    if git(sdk, "rev-parse", "HEAD") != SPEC["sdk"]["commit"]:
        fail("SDK must be at the exact commit recorded in SDK_SOURCE.json.")
    # Preserve the first status character: '-' means missing, '+' wrong revision.
    status = subprocess.check_output(["git", "-C", str(sdk), "submodule", "status", "--recursive"], text=True)
    modules = []
    for line in status.splitlines():
        if not line.startswith(" "):
            fail("All recursive submodules must already be initialized at their pinned revisions: " + line)
        match = re.fullmatch(r" ([0-9a-f]{40}) (\S+)(?: \(.*\))?", line)
        if not match:
            fail("Unexpected submodule status: " + line)
        modules.append({"path": match[2], "commit": match[1]})
    by_path = {module["path"]: module["commit"] for module in modules}
    if by_path.get("liblinphone") != SPEC["sdk"]["liblinphoneCommit"]:
        fail("Pinned liblinphone submodule is missing or mismatched.")
    return modules


def source_state(sdk, modules):
    # Include every repository, not only the superproject's dirty-submodule flag.
    result = {}
    for relative in ["."] + [module["path"] for module in modules]:
        repo = sdk / relative
        status = git(repo, "status", "--porcelain=v1", "--untracked-files=all")
        diff = git(repo, "diff", "HEAD", "--binary", "--no-ext-diff")
        result[relative] = {"status": status, "diffSha256": sha(diff.encode())}
    return result


def prepare(sdk, work, modules):
    state_path = work / "prepared-source.json"
    if state_path.exists():
        check_prepared(sdk, work, modules)
        print("SDK source preparation already matches the recorded patches.", file=sys.stderr)
        return
    if work.exists() and any(work.iterdir()):
        fail("Preparation requires an empty work directory.")
    before = source_state(sdk, modules)
    if any(item["status"] for item in before.values()):
        fail("SDK and all submodules must be clean, including untracked files. Use a disposable checkout.")
    removal = SPEC["firebaseRemoval"]
    java_path = sdk / removal["javaFile"]
    gradle_path = sdk / removal["gradleFile"]
    firebase_dir = sdk / removal["directory"]
    if not firebase_dir.is_dir() or firebase_dir.is_symlink():
        fail("Expected Firebase source directory is absent or is a symlink.")
    firebase_files = sorted(path for path in firebase_dir.rglob("*") if path.is_file())
    if not firebase_files:
        fail("Expected Firebase source files are absent.")
    changes = {}
    for path, remove_lines in ((java_path, removal["removeJavaLines"]),
                               (gradle_path, [removal["removeGradleLine"]])):
        original = path.read_bytes()
        lines = original.decode("utf-8").splitlines(keepends=True)
        for expected in remove_lines:
            if sum(line.strip() == expected for line in lines) != 1:
                fail("Expected exactly one upstream line in " + str(path) + ": " + expected)
        updated = "".join(line for line in lines if line.strip() not in remove_lines).encode()
        changes[path] = (original, updated)
    if b"com.google.firebase" in changes[gradle_path][1]:
        fail("Unexpected additional Firebase Gradle dependency; review required.")
    wrapper_path = sdk / WRAPPER
    changes[wrapper_path] = (wrapper_path.read_bytes(), WRAPPER_TEXT.encode())
    removed = {str(path.relative_to(sdk)): file_sha(path) for path in firebase_files}
    # Validation completes before any mutation; never reset/clean caller files.
    work.mkdir(parents=True, exist_ok=True)
    for path, (_, updated) in changes.items():
        path.write_bytes(updated)
    wrapper_path.chmod(0o755)
    shutil.rmtree(firebase_dir)
    state = {
        "status": "prepared-not-built",
        "sdkDirectory": str(sdk),
        "sdkCommit": SPEC["sdk"]["commit"],
        "submodules": modules,
        "helperSha256": file_sha(HELPER),
        "specSha256": file_sha(SPEC_PATH),
        "changes": {str(path.relative_to(sdk)): {"beforeSha256": sha(old), "afterSha256": sha(new)}
                    for path, (old, new) in changes.items()},
        "removedFiles": removed,
        "preparedGitState": source_state(sdk, modules),
    }
    write_json(state_path, state)
    print("Prepared pinned SDK source; Firebase sources/dependency removed. No build has run.", file=sys.stderr)


def check_prepared(sdk, work, modules):
    state = json.loads((work / "prepared-source.json").read_text(encoding="utf-8"))
    if (state["sdkDirectory"] != str(sdk) or state["sdkCommit"] != SPEC["sdk"]["commit"]
            or state["submodules"] != modules or state["helperSha256"] != file_sha(HELPER)
            or state["specSha256"] != file_sha(SPEC_PATH)):
        fail("Preparation provenance no longer matches this helper/source; use a fresh checkout/work directory.")
    if source_state(sdk, modules) != state["preparedGitState"]:
        fail("SDK source changed after preparation; refusing to build unrecorded changes.")
    for relative, hashes in state["changes"].items():
        if file_sha(sdk / relative) != hashes["afterSha256"]:
            fail("Prepared file hash mismatch: " + relative)
    if any((sdk / relative).exists() for relative in state["removedFiles"]):
        fail("A removed Firebase source file reappeared.")
    return state


def require_command(name):
    resolved = shutil.which(name)
    if not resolved:
        fail("Required host tool is missing: " + name)
    return str(Path(resolved).resolve())


def build(sdk, work, modules):
    prepared = check_prepared(sdk, work, modules)
    build_dir = work / "build"
    if build_dir.exists() or (work / "provenance.json").exists():
        fail("Build output already exists. Use a fresh checkout/work directory; no stale AAR will be reused.")
    jobs_text = os.environ.get("LINPHONE_JOBS", "2")
    if not re.fullmatch(r"[1-8]", jobs_text):
        fail("LINPHONE_JOBS must be an integer from 1 to 8.")
    offline = os.environ.get("FDROID_GRADLE_OFFLINE", "1")
    if offline not in ("0", "1"):
        fail("FDROID_GRADLE_OFFLINE must be 0 or 1.")
    tools = {name: require_command(name) for name in SPEC["toolchain"]["hostCommands"]}
    for module in SPEC["toolchain"]["pythonModules"]:
        if importlib.util.find_spec(module) is None:
            fail("Required Python module is missing: " + module)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else require_command("java")
    javac = str(Path(java_home) / "bin/javac") if java_home else require_command("javac")
    java_version = run([java, "-version"])
    javac_version = run([javac, "-version"])
    if not re.search(r'version "17(?:\.|\")', java_version) or not re.match(r"javac 17(?:\.|$)", javac_version):
        fail("The SDK build requires Java/Javac 17.")
    cmake_version = run([tools["cmake"], "--version"])
    cmake_match = re.search(r"cmake version (\d+)\.(\d+)", cmake_version)
    if not cmake_match or tuple(map(int, cmake_match.groups())) < (3, 22):
        fail("CMake 3.22 or newer is required.")
    gradle_arg = os.environ.get("LINPHONE_GRADLE")
    if not gradle_arg:
        fail("Set LINPHONE_GRADLE to an installed Gradle 8.11.1 executable; wrapper downloads are disabled.")
    gradle = require_command(gradle_arg)
    if Path(gradle).is_relative_to(sdk):
        fail("LINPHONE_GRADLE must be an installed distribution outside the SDK checkout, not a wrapper.")
    gradle_version = run([gradle, "--offline", "--no-daemon", "--version"])
    if not re.search(r"^Gradle 8\.11\.1$", gradle_version, re.MULTILINE):
        fail("The supplied SDK Gradle executable must be version 8.11.1 (the app uses Gradle 8.13 separately).")
    ndk_arg = os.environ.get("ANDROID_NDK_HOME")
    sdk_arg = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not ndk_arg or not sdk_arg:
        fail("Set ANDROID_NDK_HOME to NDK27 and ANDROID_HOME (or ANDROID_SDK_ROOT) to the installed Android SDK.")
    ndk = Path(ndk_arg).resolve()
    android_sdk = Path(sdk_arg).resolve()
    ndk_properties = (ndk / "source.properties").read_text(encoding="utf-8")
    ndk_revision = re.search(r"^Pkg.Revision\s*=\s*(27\.\S+)", ndk_properties, re.MULTILINE)
    if not ndk_revision or not (ndk / "ndk-build").is_file():
        fail("ANDROID_NDK_HOME is not an installed NDK27.")
    if not (android_sdk / "platforms/android-31/android.jar").is_file():
        fail("SDK platform android-31 is required for the Linphone SDK (app compilation additionally needs android-35).")
    env = dict(os.environ, LINPHONE_GRADLE=gradle, LINPHONE_JOBS=jobs_text,
               FDROID_GRADLE_OFFLINE=offline, ANDROID_NDK_HOME=str(ndk),
               ANDROID_HOME=str(android_sdk), ANDROID_SDK_ROOT=str(android_sdk),
               CMAKE_BUILD_PARALLEL_LEVEL=jobs_text)
    flags = ["-DCMAKE_BUILD_TYPE=" + SPEC["buildType"],
             "-DLINPHONESDK_ANDROID_ARCHS=" + ", ".join(SPEC["architectures"]),
             "-DLINPHONESDK_VERSION=" + SPEC["sdk"]["version"],
             "-DCMAKE_BUILD_PARALLEL_LEVEL=" + jobs_text,
             "-DPython3_EXECUTABLE=" + sys.executable]
    flags += SPEC["minimalFeatureFlags"] + SPEC["additionalFeatureFlags"]
    configure = [tools["cmake"], "--preset=" + SPEC["preset"], "-B", str(build_dir), *flags]
    # Only one architecture at a time; its native build may use LINPHONE_JOBS.
    compile_command = [tools["cmake"], "--build", str(build_dir), "--parallel", "1"]
    write_json(work / "build-request.json", {
        "status": "requested-not-completed", "configure": configure, "build": compile_command,
        "gradleOffline": offline == "1", "jobsPerArchitecture": int(jobs_text),
    })
    run(configure, cwd=sdk, capture=False, env=env)
    run(compile_command, cwd=sdk, capture=False, env=env)
    aar = build_dir / SPEC["aarRelativePath"]
    if not aar.is_file():
        fail("Source build did not produce the expected AAR; no binary fallback is available.")
    with zipfile.ZipFile(aar) as archive:
        names = archive.namelist()
        if "classes.jar" not in names:
            fail("Built AAR has no classes.jar.")
        for abi in SPEC["androidAbis"]:
            if not any(name.startswith("jni/" + abi + "/") and name.endswith(".so") for name in names):
                fail("Built AAR is missing native libraries for " + abi)
        corrupt = archive.testzip()
        if corrupt:
            fail("Built AAR failed its ZIP checksum: " + corrupt)
    provenance = {
        "status": "source-build-completed-not-yet-approved-by-fdroid",
        "sdkCommit": SPEC["sdk"]["commit"], "submodules": modules,
        "helperSha256": file_sha(HELPER), "specSha256": file_sha(SPEC_PATH),
        "preparationSha256": file_sha(work / "prepared-source.json"),
        "sourceChanges": prepared["changes"], "removedFiles": prepared["removedFiles"],
        "configure": configure, "build": compile_command, "gradleOffline": offline == "1",
        "tools": {"java": java_version, "javac": javac_version, "cmake": cmake_version,
                  "gradle": gradle_version, "ndk": ndk_revision[1], "python": sys.version},
        "aar": {"path": str(aar), "bytes": aar.stat().st_size, "sha256": file_sha(aar)},
        "limitations": ["This records one SDK build, not app build, scanner, asset licensing, reproducibility or publication approval."],
    }
    write_json(work / "provenance.json", provenance)
    print(str(aar))
    print("Source-build provenance: " + str(work / "provenance.json"), file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description="Prepare/build the pinned Linphone SDK from caller-supplied source.")
    parser.add_argument("action", choices=("prepare", "build"))
    parser.add_argument("sdk", type=Path)
    parser.add_argument("work", type=Path)
    args = parser.parse_args(sys.argv[2:])
    if not args.sdk.is_absolute() or not args.work.is_absolute():
        fail("SDK and work paths must be absolute.")
    sdk, work = args.sdk.resolve(), args.work.resolve()
    if work == sdk or work.is_relative_to(sdk) or sdk.is_relative_to(work):
        fail("Use a separate work directory outside the SDK source tree, not its parent.")
    modules = validate_source(sdk)
    if args.action == "prepare":
        prepare(sdk, work, modules)
    else:
        build(sdk, work, modules)


try:
    main()
except (OSError, ValueError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
    print("build-linphone: " + str(exc), file=sys.stderr)
    if isinstance(exc, subprocess.CalledProcessError) and exc.stdout:
        print(exc.stdout, file=sys.stderr)
    raise SystemExit(1)
PY
