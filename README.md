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

Add these **three custom fields** to your KodyPayment Platform Event:

| Field Label | API Name | Data Type | Length |
|-------------|----------|-----------|---------|
| `correlation_id` | `correlation_id__c` | Text | 255 |
| `method` | `method__c` | Text | 255 |
| `payload` | `payload__c` | Long Text Area | 131072 |

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
    payload__c = '{"test": "data"}'
);

EventBus.publish(testEvent);
System.debug('Test event published');
```

### 7. Verify Setup

Your Platform Event setup is complete when:
- ✅ Platform Event shows **"Deployed"** status
- ✅ All three custom fields are created and active
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
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.InitiatePayment"

# Test PaymentDetails (requires payment ID)
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.PaymentDetails your-payment-id"

# Test GetPayments
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.GetPayments"

# Test Refund (requires payment ID)
mvn exec:java -Dexec.mainClass="genericpubsub.KodyPaymentQuickTest" -Dexec.classpathScope="test" -Dexec.args="sandbox request.ecom.v1.Refund your-payment-id"
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