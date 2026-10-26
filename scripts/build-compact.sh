#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build-core.sh
# Local testing artifact using the same development certificate as debug builds.
./gradlew :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest -PlocalReleaseSigning=true
mkdir -p dist
python3 - <<'PYTHON'
import json, pathlib, shutil
output = pathlib.Path("app/build/outputs/apk/release")
element = json.loads((output / "output-metadata.json").read_text())["elements"][0]
target = pathlib.Path("dist") / f"Clash-Android-{element['versionName']}-compact-local-arm64.apk"
shutil.copy2(output / element["outputFile"], target)
shutil.copy2(pathlib.Path("app/build/outputs/mapping/release/mapping.txt"), target.with_suffix(".mapping.txt"))
print(f"{target}: {target.stat().st_size / 1024**2:.2f} MiB")
PYTHON
