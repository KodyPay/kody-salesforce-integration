# Kody-Salesforce Integration

A Java integration between Salesforce Pub/Sub API and Kody Payment API that provides real-time payment processing through event-driven architecture.

✅ **Features:**
- Real-time payment event processing via Salesforce Pub/Sub
- Complete Kody Payment API integration (InitiatePayment, PaymentDetails, GetPayments, Refund)
- Configurable store ID and API credentials
- Comprehensive test suite with automated testing
- Request-response correlation for reliable processing

## 🚀 Quick Start

### Prerequisites
1. Java 11+ and Maven installed
2. Salesforce org with Pub/Sub API access
3. Kody Payment API credentials

### Setup
1. Clone the repository
2. Run `mvn clean install` to build the project
3. Configure your credentials in `src/main/resources/arguments-sandbox.yaml`:

```yaml
# Salesforce Configuration
LOGIN_URL: https://your-org.sandbox.my.salesforce.com
USERNAME: your-username@example.com
PASSWORD: your-password-plus-security-token

# Kody Payment Configuration
KODY_HOSTNAME: grpc-staging-ap.kodypay.com
KODY_API_KEY: your-kody-api-key
KODY_STORE_ID: your-store-id
```

### Run the Integration Test
```bash
./run-test.sh sandbox
```

This will test all APIs and confirm your integration is working properly.

## 🧪 Testing

### Comprehensive Test
Tests all APIs automatically:
```bash
./run-test.sh sandbox
```

### Individual API Tests
Test specific APIs:
```bash
# Test InitiatePayment
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox InitiatePayment"

# Test PaymentDetails (requires payment ID)
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox PaymentDetails your-payment-id"

# Test GetPayments
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox GetPayments"

# Test Refund (requires payment ID)
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox Refund your-payment-id"
```

### Manual Testing
For manual integration testing:

**Terminal 1 - Start Subscriber:**
```bash
./run.sh genericpubsub.KodyPaymentSubscriber sandbox
```

**Terminal 2 - Send Payment Requests:**
```bash
# InitiatePayment
./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "your-store-id",
  "paymentReference": "pay_123456",
  "amountMinorUnits": 1000,
  "currency": "GBP",
  "orderId": "order_123456",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}'

# GetPayments
./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 10}
}'
```

## 📋 Supported Kody APIs

1. **InitiatePayment** - Creates new payment requests
2. **PaymentDetails** - Retrieves payment information by ID
3. **GetPayments** - Lists payments with pagination
4. **Refund** - Processes payment refunds

## 🏗️ Architecture

### Core Components

- **KodyPaymentPublisher** - Generic command-line publisher that accepts any method and JSON payload
- **KodyPaymentSubscriber** - Real-time event subscriber that routes requests to Kody API
- **ApplicationConfig** - Configuration management for external settings
- **Test Suite** - Comprehensive testing utilities

### Event Flow

1. Publisher sends payment request to Salesforce Pub/Sub topic `/event/KodyPayment__e`
2. Subscriber receives event in real-time
3. Subscriber routes request to appropriate Kody API endpoint
4. API response is published back to Salesforce
5. Publisher receives correlated response

## ⚙️ Configuration

All configuration is externalized in `arguments-sandbox.yaml`:

```yaml
# Salesforce Pub/Sub Settings
PUBSUB_HOST: api.pubsub.salesforce.com
PUBSUB_PORT: 7443
TOPIC: /event/KodyPayment__e

# Authentication (use either username/password OR accessToken/tenantId)
USERNAME: your-username
PASSWORD: your-password-plus-security-token
# OR
ACCESS_TOKEN: your-session-token
TENANT_ID: your-tenant-id

# Kody Payment Integration
KODY_HOSTNAME: grpc-staging-ap.kodypay.com  # Use -ap for Asia, -eu for Europe
KODY_API_KEY: your-api-key
KODY_STORE_ID: your-store-id
```

## 🔧 Project Structure

```
src/
├── main/java/
│   ├── genericpubsub/
│   │   ├── KodyPaymentPublisher.java    # Generic command-line publisher
│   │   ├── KodyPaymentSubscriber.java   # Real-time event subscriber
│   │   ├── GetSchema.java               # Utility for schema operations
│   │   └── GetTopic.java                # Utility for topic operations
│   └── utility/
│       ├── ApplicationConfig.java       # Configuration management
│       └── CommonContext.java           # Shared Salesforce context
└── test/java/genericpubsub/
    ├── KodyPaymentManualTest.java       # Comprehensive integration test
    ├── KodyPaymentQuickTest.java        # Individual API testing
    └── KodyPaymentIntegrationTest.java  # JUnit integration test
```

## 🐛 Expected Behavior

When testing with demo credentials, you may see API errors like:
- `PERMISSION_DENIED: Invalid API Key`
- `INVALID_ARGUMENT: ValidationError`

These errors are **expected** and confirm the integration is working - you're successfully reaching the Kody API with test data.

## 📝 Usage Examples

### Using Configuration-Based Store ID
The store ID is now configurable, so you can use your own credentials:

```bash
# Your store ID is automatically loaded from configuration
./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "paymentReference": "pay_123456",
  "amountMinorUnits": 1000,
  "currency": "GBP",
  "orderId": "order_123456",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}'
```

Note: You don't need to specify `storeId` in the JSON payload - it's automatically loaded from your configuration.

## 🤝 Contributing

1. All test files are properly organized in `src/test/java/`
2. Configuration is externalized in YAML files
3. Follow Maven conventions for project structure
4. Run tests before submitting changes: `./run-test.sh sandbox`