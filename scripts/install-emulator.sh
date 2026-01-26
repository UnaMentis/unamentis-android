#!/bin/bash
# Install APK on running emulator
set -e
cd "$(dirname "$0")/.."

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"

echo "=========================================="
echo "Installing on Emulator"
echo "=========================================="

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found: $APK_PATH"
    echo ""
    echo "Run ./scripts/build.sh first to build the APK."
    exit 1
fi

# Check if emulator is running
if ! adb devices | grep -q "emulator"; then
    echo "ERROR: No emulator running."
    echo ""
    echo "Start one with: ./scripts/launch-emulator.sh"
    exit 1
fi

echo "Installing: $APK_PATH"
adb install -r "$APK_PATH"

echo ""
echo "Installation complete!"
echo ""
echo "To launch the app (debug build):"
echo "  adb shell monkey -p com.unamentis.debug -c android.intent.category.LAUNCHER 1"
echo ""
echo "Or use MCP: mobile_launch_app(package_name: \"com.unamentis.debug\")"
