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
1. Java 21+ and Maven installed
2. Salesforce org with Pub/Sub API access
3. Kody Payment API credentials
4. Platform Event configured in Salesforce (see setup guide below)

### Setup
1. Clone the repository
2. Run `mvn clean install` to build the project
3. Configure your credentials in `config/arguments-sandbox.yaml`:

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

## 🔧 Salesforce Setup Guide

### 1. Create Platform Event in Salesforce

Navigate to **Setup** → **Platform Events** and create a new Platform Event with these specifications:

**Platform Event Definition:**
- **Label:** `KodyPayment`
- **Plural Label:** `KodyPayment` 
- **Object Name:** `KodyPayment`
- **API Name:** `KodyPayment__e`
- **Event Type:** `High Volume`
- **Publish Behavior:** `Publish After Commit`

### 2. Add Custom Fields

Add these **four custom fields** to your KodyPayment Platform Event:

| Field Label | API Name | Data Type | Length |
|-------------|----------|-----------|---------|
| `correlation_id` | `correlation_id__c` | Text | 255 |
| `method` | `method__c` | Text | 255 |
| `payload` | `payload__c` | Long Text Area | 131072 |
| `api_key` | `api_key__c` | Text | 255 |

**Step-by-step field creation:**
1. Click **New** in the Custom Fields & Relationships section
2. Select the appropriate data type
3. Enter the field label and API name exactly as shown above
4. Set the length as specified
5. Complete the field setup with default security settings

### 3. Deploy the Platform Event

1. Click **Deploy** on the Platform Event detail page
2. Verify the deployment status shows **"Deployed"**
3. Note the topic name will be `/event/KodyPayment__e`

### 4. Configure User Permissions

Ensure your integration user has:
- **"View All Data"** permission (for Pub/Sub API access)
- **"Modify All Data"** permission (for publishing events)
- **"API Enabled"** permission
- Access to the **KodyPayment** Platform Event

### 5. Get Integration Details

Collect these details for your configuration:

```yaml
# From your Salesforce org
LOGIN_URL: https://your-org.sandbox.my.salesforce.com  # Your org URL
USERNAME: your-integration-user@example.com            # Integration user
PASSWORD: password+security_token                      # Password + Security Token
USER_ID: 018XXXXXXXXXXXXXXX                           # User ID (15 or 18 chars)

# Platform Event topic (automatically generated)
TOPIC: /event/KodyPayment__e
```

### 6. Test Platform Event

You can test the Platform Event in Salesforce using Anonymous Apex:

```apex
// Test publishing a KodyPayment event
KodyPayment__e testEvent = new KodyPayment__e(
    correlation_id__c = 'test-123',
    method__c = 'request.ecom.v1.InitiatePayment',
    payload__c = '{"storeId": "your-store-id", "test": "data"}',
    api_key__c = 'your-kody-api-key'
);

EventBus.publish(testEvent);
System.debug('Test event published');
```

### 7. Verify Setup

Your Platform Event setup is complete when:
- ✅ Platform Event shows **"Deployed"** status
- ✅ All four custom fields are created and active
- ✅ Integration user has proper permissions
- ✅ Topic `/event/KodyPayment__e` is accessible
- ✅ Test event publishing works in Anonymous Apex

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
mvn exec:java -Dexec.mainClass="kody.integration.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.InitiatePayment"

# Test PaymentDetails (requires payment ID)
mvn exec:java -Dexec.mainClass="kody.integration.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.PaymentDetails your-payment-id"

# Test GetPayments
mvn exec:java -Dexec.mainClass="kody.integration.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.GetPayments"

# Test Refund (requires payment ID)
mvn exec:java -Dexec.mainClass="kody.integration.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.Refund your-payment-id"
```

### Manual Testing
For manual integration testing:

**Terminal 1 - Start Subscriber:**
```bash
./run.sh kody.integration.KodyPaymentService sandbox
```

**Terminal 2 - Send Payment Requests:**
```bash
# InitiatePayment
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "your-store-id",
  "paymentReference": "pay_123456",
  "amountMinorUnits": 1000,
  "currency": "GBP",
  "orderId": "order_123456",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}' 'your-custom-api-key'

# GetPayments
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 10}
}' 'your-custom-api-key'
```

## 📋 Supported Kody APIs

1. **InitiatePayment** - Creates new payment requests
2. **PaymentDetails** - Retrieves payment information by ID
3. **GetPayments** - Lists payments with pagination
4. **Refund** - Processes payment refunds

## 🏗️ Architecture

### Core Components

- **KodyPaymentService** - Real-time payment service that acts as a stateless proxy to Kody API
- **KodyPaymentPublisher** (Sample) - Generic command-line publisher that accepts any method and JSON payload
- **ApplicationConfig** - Configuration management for external settings
- **Test Suite** - Comprehensive testing utilities

### Proxy Architecture

The subscriber implements a **pure proxy pattern** where:
- All API keys must be provided in the `api_key__c` field of each Platform Event
- All store IDs must be included in the JSON payload
- No automatic injection or fallback to configuration values occurs at runtime
- Configuration values are used only for automation testing

### Request-Response Mapping

The system uses an **enum-based method registry** for easy method management and visibility. All supported methods are clearly defined in the `PaymentMethod` enum:

```java
public enum PaymentMethod {
    INITIATE_PAYMENT("request.ecom.v1.InitiatePayment", "response.ecom.v1.InitiatePayment"),
    PAYMENT_DETAILS("request.ecom.v1.PaymentDetails", "response.ecom.v1.PaymentDetails"),
    GET_PAYMENTS("request.ecom.v1.GetPayments", "response.ecom.v1.GetPayments"),
    REFUND("request.ecom.v1.Refund", "response.ecom.v1.Refund");
    // Add new methods here...
}
```

**Centralized Method Processing:**
```java
// Look up method from registry
PaymentMethod paymentMethod = PaymentMethod.fromRequestMethod(method);

if (paymentMethod == null) {
    logger.error("❌ Unsupported method: {} (Available: {})", method, 
        String.join(", ", PaymentMethod.getAllRequestMethods()));
    return;
}

// Process using enum-based dispatcher
String responseJson = processPaymentMethod(paymentMethod, payloadJson, apiKey);
publishResponse(correlationId, paymentMethod.getResponseMethod(), responseJson);
```

**Adding New Methods (5 Easy Steps):**
1. Add the new method to the `PaymentMethod` enum with request/response names
2. Add a case in the `processPaymentMethod()` switch statement  
3. Implement the `handleNewMethod()` method
4. Implement the `callKodyNewMethod()` gRPC call
5. Update test cases as needed

**Benefits of This Approach:**
- ✅ **Clear Visibility**: All methods visible at a glance in the enum
- ✅ **Type Safety**: Compile-time checking of method names
- ✅ **Easy Maintenance**: Single place to manage method mappings
- ✅ **Runtime Introspection**: Can list all available methods programmatically
- ✅ **Better Error Messages**: Shows available methods when invalid method is used

**Method Naming Convention:**
- Request methods: `request.ecom.v1.MethodName`
- Response methods: `response.ecom.v1.MethodName`
- Error responses: `response.error`

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
KODY_STORE_ID: your-store-id  # Used for automation testing only
```

## 🔧 Project Structure

```
src/
├── main/java/
│   ├── kody/integration/
│   │   └── KodyPaymentService.java      # Real-time payment service (main service)
│   ├── samples/
│   │   ├── KodyPaymentPublisher.java    # Sample command-line publisher
│   │   ├── GetSchema.java               # Utility for schema operations
│   │   └── GetTopic.java                # Utility for topic operations
│   └── utility/
│       ├── ApplicationConfig.java       # Configuration management
│       └── CommonContext.java           # Shared Salesforce context
└── test/java/kody/integration/
    ├── KodyPaymentManualTest.java       # Comprehensive integration test
    ├── KodyPaymentQuickTest.java        # Individual API testing
    └── KodyPaymentIntegrationTest.java  # JUnit integration test
```

## 🐛 Expected Behavior

When testing with demo credentials, you may see API errors like:
- `PERMISSION_DENIED: Invalid API Key`
- `INVALID_ARGUMENT: ValidationError`

These errors are **expected** and confirm the integration is working - you're successfully reaching the Kody API with test data.

## 🔧 Troubleshooting

### Common Issues

**Q: Getting `NoClassDefFoundError: io.grpc.internal.NoopClientStream`**
- **Solution**: This has been fixed in the latest version. Run `mvn clean install` to rebuild with updated dependencies.

**Q: Getting `ClassNotFoundException: ch.qos.logback.classic.spi.ThrowableProxy`**
- **Solution**: This has been resolved by upgrading logback to 1.4.14. Rebuild the project to apply the fix.

**Q: Subscriber crashes with dependency errors**
- **Solution**: The project now includes all required gRPC dependencies. Clean rebuild should resolve any lingering issues:
  ```bash
  mvn clean install
  ./run.sh kody.integration.KodyPaymentService sandbox
  ```

**Q: Multiple versions of the same library causing conflicts**
- **Solution**: Dependencies have been cleaned up and unified. All protobuf libraries now use the same version (4.31.0).

## 📝 Comprehensive Usage Examples

### 1. Running the Main Service

Start the payment subscriber service:
```bash
# Start in sandbox environment
./run.sh kody.integration.KodyPaymentService sandbox

# Start in production environment  
./run.sh genericpubsub.KodyPaymentSubscriber production
```

### 2. Sample Publisher Commands

All examples use the sample publisher in the `samples` package:

#### InitiatePayment Examples
```bash
# Basic payment initiation
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "your-store-id",
  "paymentReference": "pay_$(date +%s)",
  "amountMinorUnits": 2500,
  "currency": "GBP", 
  "orderId": "order_$(date +%s)",
  "returnUrl": "https://yoursite.com/payment/return",
  "payerEmailAddress": "customer@example.com"
}' 'your-api-key'

# Payment with customer details
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "your-store-id",
  "paymentReference": "pay_detailed_$(date +%s)",
  "amountMinorUnits": 5000,
  "currency": "EUR",
  "orderId": "order_detailed_$(date +%s)", 
  "returnUrl": "https://yoursite.com/success",
  "payerEmailAddress": "premium@customer.com",
  "payerName": "John Smith",
  "description": "Premium subscription payment"
}' 'your-api-key'
```

#### PaymentDetails Examples
```bash
# Get payment details by ID
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ"
}' 'your-api-key'

# Multiple payment details (if supported)
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails '{
  "storeId": "your-store-id",
  "paymentIds": ["P._pay.ABC123", "P._pay.DEF456"]
}' 'your-api-key'
```

#### GetPayments Examples
```bash
# Basic payment listing
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 10}
}' 'your-api-key'

# Filtered payment listing by date
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 20},
  "dateFrom": "2024-01-01T00:00:00Z",
  "dateTo": "2024-12-31T23:59:59Z"
}' 'your-api-key'

# Get payments by status
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id", 
  "pageCursor": {"page": 1, "pageSize": 50},
  "status": "COMPLETED"
}' 'your-api-key'
```

#### Refund Examples
```bash
# Full refund
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.Refund '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ",
  "amount": "25.00",
  "reason": "Customer request"
}' 'your-api-key'

# Partial refund
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.Refund '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ", 
  "amount": "10.50",
  "reason": "Partial cancellation"
}' 'your-api-key'
```

### 3. Using Different API Keys

Each request can use a different API key for multi-tenant scenarios:

```bash
# Tenant A payment
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "tenant-a-store-id",
  "paymentReference": "tenant_a_$(date +%s)",
  "amountMinorUnits": 1000,
  "currency": "GBP"
}' 'tenant-a-api-key'

# Tenant B payment  
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "tenant-b-store-id", 
  "paymentReference": "tenant_b_$(date +%s)",
  "amountMinorUnits": 2000,
  "currency": "USD"
}' 'tenant-b-api-key'
```

### 4. Batch Operations

Process multiple payments in sequence:

```bash
#!/bin/bash
# batch-payments.sh - Process multiple payments

API_KEY="your-api-key"
STORE_ID="your-store-id"

for i in {1..5}; do
  echo "Processing payment $i"
  ./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment "{
    \"storeId\": \"$STORE_ID\",
    \"paymentReference\": \"batch_pay_$i\",
    \"amountMinorUnits\": $((1000 * i)),
    \"currency\": \"GBP\",
    \"orderId\": \"batch_order_$i\",
    \"returnUrl\": \"https://example.com/return\",
    \"payerEmailAddress\": \"batch$i@example.com\"
  }" "$API_KEY"
  
  sleep 2  # Wait between requests
done
```

### 5. Error Handling Examples

Test error scenarios:

```bash
# Invalid method
./run.sh samples.KodyPaymentPublisher sandbox request.invalid.method '{}' 'your-api-key'

# Missing required fields
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "paymentReference": "incomplete_payment"
}' 'your-api-key'

# Invalid API key
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id"
}' 'invalid-api-key'
```

### 6. Development Utilities

Use utility classes for schema and topic inspection:

```bash
# Get topic information
./run.sh samples.GetTopic sandbox

# Get schema details
./run.sh samples.GetSchema sandbox

# Inspect platform event structure
./run.sh samples.GetSchema sandbox /event/KodyPayment__e
```

### 7. Integration with External Systems

#### Webhook Integration
```bash
# Example: Process webhook payload
WEBHOOK_PAYLOAD='{"paymentId": "P._pay.WEBHOOK123", "storeId": "webhook-store"}'

./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails "$WEBHOOK_PAYLOAD" 'webhook-api-key'
```

#### Scheduled Operations
```bash
#!/bin/bash
# daily-report.sh - Get daily payment report

TODAY=$(date +%Y-%m-%d)
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments "{
  \"storeId\": \"report-store-id\",
  \"pageCursor\": {\"page\": 1, \"pageSize\": 100},
  \"dateFrom\": \"${TODAY}T00:00:00Z\",
  \"dateTo\": \"${TODAY}T23:59:59Z\"
}" 'report-api-key' > "payments_$TODAY.json"
```

### 8. Advanced Usage Examples

### Passing API Keys
The publisher supports two modes for API key handling:

**Using Custom API Key (Required):**
```bash
# API key is now mandatory as 5th parameter
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{
  "storeId": "your-store-id",
  "paymentReference": "pay_123456",
  "amountMinorUnits": 1000,
  "currency": "GBP",
  "orderId": "order_123456",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}' 'your-custom-api-key'
```

**How It Works:**
- The API key is sent in the `api_key__c` field of the Platform Event
- The subscriber extracts this API key and uses it for the Kody API call
- This enables true proxy mode where each request can use different API keys

### JSON Payload Requirements
**Important:** The `storeId` field is required in all payment request payloads.

## 🐳 Docker Deployment

### Quick Start with Docker
```bash
# 1. Build the Docker image
./docker-build.sh

# 2. Configure your credentials (config files are already in config/ directory)
# Edit config/arguments-sandbox.yaml with your credentials

# 3. Start the service
./docker-run.sh
```

### Docker Commands
```bash
# View live logs
docker-compose logs -f

# Check service status
docker-compose ps

# Stop the service
docker-compose down

# Restart the service
docker-compose restart
```

### Production Deployment
For production deployment on any Docker-compatible platform:

1. **Build and push to registry:**
```bash
docker build -t your-registry/kody-salesforce-integration:v1.0 .
docker push your-registry/kody-salesforce-integration:v1.0
```

2. **Deploy with your orchestrator:**
- **Docker Swarm:** `docker stack deploy -c docker-compose.yml kody-stack`
- **Kubernetes:** Convert using Kompose or create K8s manifests
- **AWS ECS/Fargate:** Use the Docker image with ECS task definitions
- **Google Cloud Run:** Deploy directly from container registry

3. **Environment Variables:**
- `JAVA_OPTS`: JVM tuning options
- `KODY_ENV`: Environment name (sandbox/production)

### Container Features
- ✅ **Multi-stage build** for smaller production images
- ✅ **Non-root user** for security
- ✅ **Health checks** built-in
- ✅ **Log rotation** configured
- ✅ **Graceful shutdown** with proper signal handling
- ✅ **Resource limits** and memory optimization

## 🤝 Contributing

1. All test files are properly organized in `src/test/java/`
2. Configuration is externalized in YAML files
3. Follow Maven conventions for project structure
4. Run tests before submitting changes: `./run-test.sh sandbox`