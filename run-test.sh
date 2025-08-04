#!/bin/bash

# Kody Payment Integration Test Runner
# This script runs the comprehensive test for all Kody Payment APIs

echo "🚀 Kody Payment Integration Test Runner"
echo "========================================"

# Check if environment is provided
if [ $# -eq 0 ]; then
    echo "❌ Usage: ./run-test.sh <environment>"
    echo "   Example: ./run-test.sh sandbox"
    exit 1
fi

ENVIRONMENT=$1
echo "🔧 Environment: $ENVIRONMENT"

# Check if configuration file exists
CONFIG_FILE="config/arguments-${ENVIRONMENT}.yaml"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ Configuration file not found: $CONFIG_FILE"
    echo "   Please make sure the configuration file exists and contains:"
    echo "   - KODY_HOSTNAME"
    echo "   - KODY_API_KEY"
    echo "   - Other required Salesforce Pub/Sub settings"
    exit 1
fi

echo "✅ Configuration file found: $CONFIG_FILE"

# Build the project first
echo ""
echo "🔨 Building project..."
mvn test-compile -q
if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check compilation errors."
    exit 1
fi
echo "✅ Build successful"

# Run the test
echo ""
echo "🧪 Starting Kody Payment Integration Test..."
echo "   This will test all APIs: InitiatePayment, PaymentDetails, GetPayments, Refund"
echo "   🚨 FAIL-FAST mode: Test execution stops immediately on first failure"
echo "   Please wait, this may take up to 2-3 minutes..."
echo ""

mvn exec:java -Dexec.mainClass="kody.integration.KodyPaymentManualTest" -Dexec.classpathScope="test" -Dexec.args="$ENVIRONMENT" -q

TEST_EXIT_CODE=$?
echo ""

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "🏁 Test execution completed successfully! ✅"
    echo "🎉 ALL INTEGRATION TESTS PASSED!"
else
    echo "🏁 Test execution failed! ❌"
    echo "💥 AT LEAST ONE TEST FAILED - Check logs above for details"
    echo "Exit code: $TEST_EXIT_CODE"
fi
echo ""
echo "📋 Test Coverage:"
echo "  • InitiatePayment API - Creates payment requests"
echo "  • PaymentDetails API - Retrieves payment information"
echo "  • GetPayments API - Lists payments with pagination"
echo "  • Refund API - Processes payment refunds"
echo "  • Error Handling - Tests invalid requests"
echo "  • Concurrent Requests - Tests multiple simultaneous requests"
echo ""
echo "💡 Note: API errors like 'PERMISSION_DENIED' or 'INVALID_ARGUMENT'"
echo "   are expected with demo credentials and confirm the integration is working."

# Exit with the same code as the test
exit $TEST_EXIT_CODE