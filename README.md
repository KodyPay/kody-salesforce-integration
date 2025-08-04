# Kody-Salesforce Integration

Java integration between Salesforce Pub/Sub API and Kody Payment API for real-time payment processing through event-driven architecture.

**Features:**
- Real-time payment processing via Salesforce Platform Events
- Complete Kody Payment API integration (InitiatePayment, PaymentDetails, GetPayments, Refund)
- Pure proxy architecture with enum-based method registry
- Comprehensive test suite with automated testing
- Docker deployment ready

## 🚀 Quick Start

### Prerequisites
- Java 11+ and Maven
- Salesforce org with Pub/Sub API access
- Kody Payment API credentials

### Setup
1. Clone and build: `mvn clean install`
2. Configure credentials in `config/arguments-sandbox.yaml`
3. Test: `./run-test.sh sandbox`

## 🔧 Salesforce Setup

Create Platform Event `KodyPayment__e` with these fields:

| Field Label | API Name | Data Type | Length |
|-------------|----------|-----------|---------|
| `correlation_id` | `correlation_id__c` | Text | 255 |
| `method` | `method__c` | Text | 255 |
| `payload` | `payload__c` | Long Text Area | 131072 |
| `api_key` | `api_key__c` | Text | 255 |

**Permissions needed:**
- View All Data, Modify All Data, API Enabled
- Access to KodyPayment Platform Event

## 🧪 Testing

### Automated Testing
```bash
./run-test.sh sandbox                    # Test all APIs
```

### Manual Testing
**Terminal 1 - Start Service:**
```bash
./run.sh kody.integration.KodyPaymentService sandbox
```

**Terminal 2 - Send Requests:**
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
}' 'your-api-key'

# PaymentDetails (use payment ID from above)
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ"
}' 'your-api-key'

# GetPayments
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 10}
}' 'your-api-key'

# Refund
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.Refund '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ",
  "amount": "10.00",
  "reason": "Customer request"
}' 'your-api-key'
```

## 📋 Supported APIs

| API | Purpose | Required Fields |
|-----|---------|----------------|
| **InitiatePayment** | Creates new payment requests | `storeId`, `paymentReference`, `amountMinorUnits`, `currency` |
| **PaymentDetails** | Retrieves payment information by ID | `storeId`, `paymentId` |
| **GetPayments** | Lists payments with pagination | `storeId`, `pageCursor` |
| **Refund** | Processes payment refunds | `storeId`, `paymentId`, `amount` |

## 🏗️ Architecture

### Core Components

| Component | Purpose | File |
|-----------|---------|------|
| **KodyPaymentService** | Real-time payment processor with enum-based method registry | `KodyPaymentService.java` |
| **KodyPaymentPublisher** | Testing & integration tool | `samples/KodyPaymentPublisher.java` |
| **ApplicationConfig** | Configuration manager | `utility/ApplicationConfig.java` |
| **Test Suite** | Quality assurance (Manual, Quick, Integration tests) | `test/kody/integration/*.java` |

### Pure Proxy Architecture

**Flow**: `Salesforce Platform Event → KodyPaymentService → Kody Payment API → Response Event`

**Key Principles:**
- 🔐 **Security First**: API keys in each Platform Event (`api_key__c`)
- 🏪 **Multi-tenant Ready**: Store IDs in every JSON payload
- ⚡ **Zero Configuration**: No runtime credential injection
- 🧪 **Test-Friendly**: Config only for automated testing

### Functional Method Registry

Uses **PaymentMethod enum** to eliminate duplicate code:

```java
public enum PaymentMethod {
    INITIATE_PAYMENT("request.ecom.v1.InitiatePayment", "response.ecom.v1.InitiatePayment"),
    PAYMENT_DETAILS("request.ecom.v1.PaymentDetails", "response.ecom.v1.PaymentDetails"),
    GET_PAYMENTS("request.ecom.v1.GetPayments", "response.ecom.v1.GetPayments"),
    REFUND("request.ecom.v1.Refund", "response.ecom.v1.Refund");
}
```

**Benefits:**
- 🎯 **Single Point of Truth** - All methods defined in one enum
- 🔧 **Easy Extensions** - Add new APIs by updating enum only  
- 🛡️ **Type Safety** - Compile-time validation
- 📊 **Better Observability** - Runtime method listing
- 🐛 **Improved Debugging** - Clear error messages

## ⚙️ Configuration

Configure in `config/arguments-sandbox.yaml`:

```yaml
# Salesforce
LOGIN_URL: https://your-org.sandbox.my.salesforce.com
USERNAME: your-username@example.com
PASSWORD: your-password-plus-security-token

# Kody Payment
KODY_HOSTNAME: grpc-staging-ap.kodypay.com
KODY_API_KEY: your-api-key
KODY_STORE_ID: your-store-id  # Used for testing only
```

## 🐳 Docker Deployment

```bash
./docker-build.sh              # Build image
./docker-run.sh                # Start service
docker-compose logs -f         # View logs
docker-compose down            # Stop service
```

## 🔧 Project Structure

```
src/
├── main/java/
│   ├── kody/integration/KodyPaymentService.java      # Main service
│   ├── samples/KodyPaymentPublisher.java             # Sample publisher
│   └── utility/ApplicationConfig.java                # Configuration
└── test/java/kody/integration/
    ├── KodyPaymentManualTest.java                     # Full integration test
    ├── KodyPaymentQuickTest.java                      # Individual API test
    └── KodyPaymentIntegrationTest.java                # JUnit test
```

## 🔧 Troubleshooting

**Common Issues:**
- **gRPC errors**: Run `mvn clean install` to rebuild dependencies
- **API errors** (`PERMISSION_DENIED`, `INVALID_ARGUMENT`): Expected with demo credentials - confirms integration works
- **Service hangs**: Normal behavior - runs continuously listening for events

## 🤝 Contributing

1. Run tests before changes: `./run-test.sh sandbox`
2. Follow Maven conventions
3. Configuration externalized in YAML files