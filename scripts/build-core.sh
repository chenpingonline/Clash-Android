#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/prepare-shared.py
python3 scripts/prepare-core.py
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/29.0.14206865}"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/bin"
mkdir -p app/src/main/jniLibs/arm64-v8a
cd core
CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$TOOLCHAIN/aarch64-linux-android29-clang" \
  go build -modfile ../.cache/android-core/mobile.mod -tags with_gvisor -buildmode=c-shared -trimpath -ldflags='-s -w -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' \
  -o ../app/src/main/jniLibs/arm64-v8a/libclashcore.so .
rm -f ../app/src/main/jniLibs/arm64-v8a/libclashcore.h
