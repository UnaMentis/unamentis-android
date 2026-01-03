#!/bin/bash
# Auto-format UnaMentis Android code
cd "$(dirname "$0")/.."

echo "=========================================="
echo "Formatting Code"
echo "=========================================="

./gradlew ktlintFormat --console=plain

echo ""
echo "Formatting complete!"
