#!/bin/bash
# Build UnaMentis Android debug APK
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "Building UnaMentis Android (Debug)"
echo "=========================================="

./gradlew assembleDebug --console=plain

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "Build successful!"
    echo "APK: $APK_PATH"
    echo "Size: $(du -h "$APK_PATH" | cut -f1)"
else
    echo ""
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi
