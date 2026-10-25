#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build-core.sh
./gradlew :app:assembleDebug :app:lintDebug
mkdir -p dist
python3 - <<'PYTHON'
import json, pathlib, shutil
output = pathlib.Path("app/build/outputs/apk/debug")
element = json.loads((output / "output-metadata.json").read_text())["elements"][0]
shutil.copy2(output / element["outputFile"], pathlib.Path("dist") / f"Clash-Android-{element['versionName']}-arm64.apk")
PYTHON
