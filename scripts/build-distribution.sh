#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CLASH_RELEASE_SIGNING_FILE="${CLASH_RELEASE_SIGNING_FILE:-$HOME/.config/clash-android/signing/signing.properties}"
if [[ ! -f "$CLASH_RELEASE_SIGNING_FILE" ]]; then
  echo "Missing private release signing configuration. Set CLASH_RELEASE_SIGNING_FILE." >&2
  exit 1
fi
./scripts/build-core.sh
./gradlew :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest -PdistributionRelease=true
mkdir -p dist
python3 - <<'PYTHON'
import hashlib, json, pathlib, shutil
output = pathlib.Path("app/build/outputs/apk/release")
metadata = json.loads((output / "output-metadata.json").read_text())
assert metadata["applicationId"] == "cn.chenpingonline.clash", "Incorrect distribution package"
element = metadata["elements"][0]
assert "-" not in element["versionName"], "Distribution builds must use a stable version"
target = pathlib.Path("dist") / f"Clash-Android-{element['versionName']}-arm64.apk"
shutil.copy2(output / element["outputFile"], target)
shutil.copy2(pathlib.Path("app/build/outputs/mapping/release/mapping.txt"), target.with_suffix(".mapping.txt"))
target.with_suffix(".apk.sha256").write_text(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.name}\n")
print(f"{target}: {target.stat().st_size / 1024**2:.2f} MiB")
PYTHON
