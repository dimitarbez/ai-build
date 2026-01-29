#!/bin/bash
set -e

echo "=== Running E2E Integration Tests ==="
echo ""

# Use Gradle to handle all dependencies automatically
./gradlew runE2ETest --console=plain

echo ""
