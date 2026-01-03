#!/bin/bash
# Run UnaMentis Android unit tests
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "Running Unit Tests"
echo "=========================================="

./gradlew test --console=plain

echo ""
echo "Unit tests passed!"
