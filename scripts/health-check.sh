#!/bin/bash
# Health check: lint + quick tests
# Run this before every commit
set -e
cd "$(dirname "$0")/.."

echo "=========================================="
echo "UnaMentis Android Health Check"
echo "=========================================="
echo ""

echo "Step 1/2: Lint Checks"
echo "---------------------"
./scripts/lint.sh

echo ""
echo "Step 2/2: Unit Tests"
echo "--------------------"
./scripts/test-quick.sh

echo ""
echo "=========================================="
echo "Health check PASSED!"
echo "=========================================="
echo ""
echo "You may proceed with your commit."
