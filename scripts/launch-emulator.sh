#!/bin/bash
# Launch Android emulator
set -e

EMULATOR_NAME="${1:-Pixel_8_Pro_API_34}"

echo "=========================================="
echo "Launching Android Emulator"
echo "=========================================="
echo ""
echo "Emulator: $EMULATOR_NAME"

# Check if emulator exists
if ! $ANDROID_HOME/emulator/emulator -list-avds | grep -q "^${EMULATOR_NAME}$"; then
    echo ""
    echo "ERROR: Emulator '$EMULATOR_NAME' not found."
    echo ""
    echo "Available emulators:"
    $ANDROID_HOME/emulator/emulator -list-avds
    echo ""
    echo "Create one in Android Studio: Tools > Device Manager > Create Device"
    exit 1
fi

echo "Starting emulator (this may take a minute)..."
$ANDROID_HOME/emulator/emulator -avd "$EMULATOR_NAME" -no-snapshot-load &

echo "Waiting for emulator to boot..."
adb wait-for-device

# Wait for boot to complete
echo "Waiting for system to be ready..."
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
    sleep 2
done

echo ""
echo "Emulator is ready!"
echo ""
echo "To install the app: ./scripts/install-emulator.sh"
echo "To view logs: open http://localhost:8765/"
