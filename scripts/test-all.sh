#!/bin/bash
# Run all UnaMentis Android tests (unit + instrumented)
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "Running All Tests"
echo "=========================================="
echo ""
echo "NOTE: Instrumented tests require a running emulator."
echo "Start one with: ./scripts/launch-emulator.sh"
echo ""

echo "Step 1/2: Unit Tests"
echo "--------------------"
./gradlew test --console=plain

echo ""
echo "Step 2/2: Instrumented Tests"
echo "----------------------------"
./gradlew connectedAndroidTest --console=plain

echo ""
echo "All tests passed!"
