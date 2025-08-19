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
- Java 21+ and Maven
- Salesforce org with Pub/Sub API access
- Kody Payment API credentials

### Setup
1. Clone and build: `mvn clean install`
2. Configure credentials: `cp .env.sandbox.example .env.sandbox`
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

# Expected Response:
{
  "response": {
    "paymentId": "P._pay.ABC123XYZ",
    "paymentData": {
      "paymentWallet": {
        "paymentLinkId": "https://p-staging.kody.com/P._pay.ABC123XYZ"
      }
    }
  }
}

# InitiatePaymentStream (Real-time updates - Ignyto preferred method)
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePaymentStream '{
  "storeId": "your-store-id",
  "paymentReference": "stream_123456",
  "amountMinorUnits": 1500,
  "currency": "GBP",
  "orderId": "order_stream_123",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}' 'your-api-key'

# Expected Response:
# Stream Event 1 - Payment Initiated:
{
  "response": {
    "paymentId": "P._pay.STREAM789",
    "paymentData": {
      "paymentWallet": {
        "paymentLinkId": "https://p-staging.kody.com/P._pay.STREAM789"
      }
    }
  }
}

# Stream Event 2 - Status Update (when customer completes payment):
{
  "response": {
    "paymentId": "P._pay.STREAM789",
    "paymentReference": "stream_123456",
    "orderId": "order_stream_123",
    "orderMetadata": "",
    "status": "SUCCESS",
    "dateCreated": "2025-08-19T14:30:14.313259Z",
    "datePaid": "2025-08-19T14:32:55Z",
    "pspReference": "DUMMY123REFERENCE",
    "paymentData": {
      "pspReference": "DUMMY123REFERENCE",
      "paymentMethod": "MASTERCARD",
      "paymentMethodVariant": "Mastercard Corporate Credit",
      "authStatus": "AUTHORISED",
      "authStatusDate": "2025-08-19T14:32:56.475653Z",
      "paymentWallet": {
        "cardLast4Digits": "1234",
        "paymentLinkId": "P._pay.STREAM789"
      }
    },
    "saleData": {
      "amountMinorUnits": "1500",
      "currency": "GBP",
      "orderId": "order_stream_123",
      "paymentReference": "stream_123456",
      "orderMetadata": ""
    }
  }
}

# PaymentDetails (use payment ID from above)
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ"
}' 'your-api-key'
# Expected Response:
{
  "response": {
    "paymentId": "P._pay.ABC123XYZ",
    "paymentReference": "pay_123456",
    "orderId": "order_123456",
    "orderMetadata": "",
    "status": "SUCCESS",
    "dateCreated": "2025-08-19T14:30:14.313259Z",
    "datePaid": "2025-08-19T14:32:55Z",
    "pspReference": "DUMMY123REFERENCE",
    "paymentData": {
      "pspReference": "DUMMY123REFERENCE",
      "paymentMethod": "MASTERCARD",
      "paymentMethodVariant": "Mastercard Corporate Credit",
      "authStatus": "AUTHORISED",
      "authStatusDate": "2025-08-19T14:32:56.475653Z",
      "paymentWallet": {
        "cardLast4Digits": "1234",
        "paymentLinkId": "P._pay.ABC123XYZ"
      }
    },
    "saleData": {
      "amountMinorUnits": "1000",
      "currency": "GBP",
      "orderId": "order_123456",
      "paymentReference": "pay_123456",
      "orderMetadata": ""
    }
  }
}

# GetPayments
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{
  "storeId": "your-store-id",
  "pageCursor": {"page": 1, "pageSize": 10}
}' 'your-api-key'

# Expected Response:
{
  "response": {
    "total": "3",
    "payments": [
      {
        "paymentId": "P._pay.ABC123XYZ",
        "paymentReference": "pay_123456",
        "orderId": "order_123456",
        "orderMetadata": "",
        "status": "SUCCESS",
        "dateCreated": "2025-08-19T14:30:00.123456Z"
      },
      {
        "paymentId": "P._pay.DEF789UVW",
        "paymentReference": "pay_789012",
        "orderId": "order_789012",
        "orderMetadata": "",
        "status": "EXPIRED",
        "dateCreated": "2025-08-18T10:15:30.654321Z"
      },
      {
        "paymentId": "P._pay.GHI345JKL",
        "paymentReference": "pay_345678",
        "orderId": "order_345678",
        "orderMetadata": "",
        "status": "PENDING",
        "dateCreated": "2025-08-17T16:45:22.987654Z"
      }
    ]
  }
}

# Refund
./run.sh samples.KodyPaymentPublisher sandbox request.ecom.v1.Refund '{
  "storeId": "your-store-id",
  "paymentId": "P._pay.ABC123XYZ",
  "amount": "10.00",
  "reason": "Customer request"
}' 'your-api-key'

# Expected Response:
{
  "status": "REQUESTED",
  "paymentId": "P._pay.ABC123XYZ",
  "dateCreated": "2025-08-19T15:00:51.580125Z",
  "totalPaidAmount": "10.00",
  "totalAmountRefunded": "10.00",
  "remainingAmount": "0.00",
  "totalAmountRequested": "10.00",
  "paymentTransactionId": "12345678-1234-5678-9012-123456789abc"
}
```

## 📋 Supported APIs

| API | Purpose | Required Fields |
|-----|---------|----------------|
| **InitiatePayment** | Creates new payment requests | `storeId`, `paymentReference`, `amountMinorUnits`, `currency` |
| **InitiatePaymentStream** | Creates payment with real-time status updates (Preferred) | `storeId`, `paymentReference`, `amountMinorUnits`, `currency` |
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

The service uses environment variables for configuration. Create a `.env.sandbox` file from the example:

```bash
cp .env.sandbox.example .env.sandbox
# Edit .env.sandbox with your credentials
```

Required environment variables:
```bash
# Salesforce
LOGIN_URL=https://your-org.sandbox.my.salesforce.com
USERNAME=your-username@example.com
PASSWORD=your-password-plus-security-token
TOPIC=/event/KodyPayment__e

# Kody Payment
KODY_HOSTNAME=grpc-staging-ap.kodypay.com
```

## 🐳 Docker Deployment

```bash
./docker-build.sh              # Build image
./docker-run.sh sandbox        # Start service with environment
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
3. Configuration externalized in environment files