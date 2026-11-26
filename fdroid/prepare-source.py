#!/usr/bin/env python3
"""Remove the website-only Linphone Maven declaration before F-Droid scanning.

Run in a disposable packaging checkout. This does not certify asset licences,
download dependencies, or turn a disabled candidate into an approved recipe.
"""

import argparse
from pathlib import Path
import sys


MARKER = "EVDIAL_WEBSITE_LINPHONE_MAVEN"
URL = "https://download.linphone.org/maven_repository"
EXPECTED = "maven { url '" + URL + "' } // " + MARKER
SELECTOR = "def fdroidBuild = providers.gradleProperty('fdroid').orElse('false').get() == 'true'"


def prepare(root: Path) -> bool:
    settings = root / "settings.gradle"
    text = settings.read_text(encoding="utf-8")
    if SELECTOR not in text or "if (!fdroidBuild) {" not in text:
        raise ValueError("Expected EVDial F-Droid settings selector is absent; refusing to rewrite.")
    if "rootProject.name = 'EVDial'" not in text:
        raise ValueError("This is not the expected EVDial settings.gradle.")
    if "google()" not in text or "mavenCentral()" not in text:
        raise ValueError("Expected trusted repositories are absent.")
    lines = text.splitlines(keepends=True)
    marked = [i for i, line in enumerate(lines) if MARKER in line]
    if not marked:
        if "download.linphone.org" in text:
            raise ValueError("Unmarked Linphone repository remains; manual review is required.")
        return False
    if len(marked) != 1 or lines[marked[0]].strip() != EXPECTED:
        raise ValueError("Linphone repository declaration changed; refusing a broad substitution.")
    del lines[marked[0]]
    cleaned = "".join(lines)
    if "download.linphone.org" in cleaned or MARKER in cleaned:
        raise ValueError("Another Linphone repository reference remains; nothing was written.")
    settings.write_text(cleaned, encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    try:
        changed = prepare(args.project_root.resolve())
    except (OSError, ValueError) as exc:
        print(f"prepare-source: {exc}", file=sys.stderr)
        return 1
    print("Removed website Linphone Maven declaration." if changed else "Website Linphone Maven declaration is already absent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
