#!/bin/bash
# Run lint checks on UnaMentis Android
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "Running Lint Checks"
echo "=========================================="

echo ""
echo "Step 1/2: ktlint"
echo "----------------"
./gradlew ktlintCheck --console=plain

echo ""
echo "Step 2/2: detekt"
echo "----------------"
./gradlew detekt --console=plain

echo ""
echo "Lint checks passed!"
