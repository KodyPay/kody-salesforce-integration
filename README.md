# Kody-Salesforce Integration

The publisher-subscriber integration is fully functional:
- ✅ Events published successfully to Salesforce Pub/Sub
- ✅ Subscriber receives events in real-time
- ✅ Payment data extracted and processed
- ✅ Kody Payment API called successfully with support for InitiatePayment, PaymentDetails, GetPayments, and Refund APIs

## Overview
This project provides a Java integration between Salesforce Pub/Sub API and Kody Payment API. It includes examples for generic Publish, Subscribe operations, and specific implementations for Kody payment processing. The examples demonstrate real-time event processing and API integration patterns.

## Project Structure
In the `src/main` directory of the project, you will find several sub-directories as follows:
* `java/`: This directory contains the main source code for all the examples grouped into separate packages:
  * `genericpubsub/`: This package contains the examples covering the general flows of all RPCs of Pub/Sub API. 
  * `utility`: This package contains a list of utility classes used across all the examples. 
* `proto/` - This directory contains the same `pubsub_api.proto` file found at the root of this repo. The plugin used to generate the sources requires for this proto file to be present in the `src` directory.  
* `resources/` - This directory contains a list of resources needed for running the examples.

## Running the Examples
### Prerequisites
1. Install [Java 11](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html), [Maven](https://maven.apache.org/install.html).
2. Clone this project.
3. Run `mvn clean install` from the `java` directory to build the project and generate required sources from the proto file.
4. The `arguments.yaml` file in the `src/main/resources` sub-directory contains a list of required and optional configurations needed to run the examples. The file contains detailed comments on how to set the configurations.
5. Get the username, password, and login URL of the Salesforce org you wish to use.
6. For the examples in `genericpubsub` package, a custom **_Order Event_** [platform event](https://developer.salesforce.com/docs/atlas.en-us.platform_events.meta/platform_events/platform_events_define_ui.htm) has to be created in the Salesforce org. Ensure your `Order Event` platform event matches the following structure:
   - Standard Fields
       - Label: `Order Event`
       - Plural Label: `Order Events`
   - Custom Fields
       - `Order Number` (Text, 18)
       - `City` (Text, 50)
       - `Amount` (Number, (16,2))

### Execution
1. Update the configurations in the `src/main/resources/arguments.yaml` file. The required configurations will apply to all the examples and the optional ones depends on which example is being executed. The configurations include:
   1. Required configurations:
       * `PUBSUB_HOST`: Specify the Pub/Sub API endpoint to be used.
       * `PUBSUB_PORT`: Specify the Pub/Sub API port to be used (usually 7443).
       * `LOGIN_URL`: Specify the login url of the Salesforce org being used to run the examples.
       * `USERNAME` & `PASSWORD`: For authentication via username and password, you will need to specify the username and password of the Salesforce org. 
       * `ACCESS_TOKEN` & `TENANT_ID`: For authentication via session token and tenant ID, you will need to specify the sessionToken and tenant ID of the Salesforce org.
       *  When using managed event subscriptions (beta), one of these configurations is required.
          * `MANAGED_SUB_DEVELOPER_NAME`: Specify the developer name of ManagedEventSubscription. This parameter is used in ManagedSubscribe.java.
          * `MANAGED_SUB_ID`: Specify the ID of the ManagedEventSubscription Tooling API record. This parameter is used in ManagedSubscribe.java.

   2. Optional Parameters:
       * `TOPIC`: Specify the topic for which you wish to publish/subscribe. 
       * `NUMBER_OF_EVENTS_TO_PUBLISH`: Specify the number of events to publish while using the PublishStream RPC.
       * `SINGLE_PUBLISH_REQUEST`: Specify if you want to publish the events in a single or multiple PublishRequests.
       * `NUMBER_OF_EVENTS_IN_FETCHREQUEST`: Specify the number of events that the Subscribe RPC requests from the server in each FetchRequest. The example fetches at most 5 events in each Subscribe request. If you pass in more than 5, it sends multiple Subscribe requests with at most 5 events requested in FetchRequest each. For more information about requesting events, see [Pull Subscription and Flow Control](https://developer.salesforce.com/docs/platform/pub-sub-api/guide/flow-control.html) in the Pub/Sub API documentation.
       * `PROCESS_CHANGE_EVENT_HEADER_FIELDS`: Specify whether the Subscribe or ManagedSubscribe client should process the change data capture event bitmap fields in `ChangeEventHeader`. In this sample, only the `changedFields` field is expanded. To expand the `diffFields` and `nulledFields` header fields, modify the sample code. See [Event Deserialization Considerations](https://developer.salesforce.com/docs/platform/pub-sub-api/guide/event-deserialization-considerations.html).
       * `REPLAY_PRESET`: Specify the ReplayPreset for subscribe examples.
         * If a subscription has to be started using the CUSTOM replay preset, the `REPLAY_ID` parameter is mandatory. 
         * The `REPLAY_ID` is a byte array and must be specified in this format: `[<byte_values_separated_by_commas>]`. Please enter the values as is within the square brackets and without any quotes. 
         * Example: `[0, 1, 2, 3, 4, -5, 6, 7, -8]`
       
2. After setting up the configurations, any example can be executed using the `./run.sh` file available at the parent directory.
   * Format for running the examples: `./run.sh <package_name>.<example_class_name>`
   * Example: `./run.sh genericpubsub.PublishStream`

## Implementation
- This repo can be used as a reference point for clients looking to create a Java app to integrate with Pub/Sub API. Note that the project structure and the examples included in this repo are intended for demo purposes only and clients are free to implement their own Java apps in any way they see fit.
- The Generic Subscribe and ManagedSubscribe (beta) RPC examples create a long-lived subscription. After all requested events are received, Subscribe sends a new `FetchRequest` and ManagedSubscribe sends a new `ManagedFetchRequest` to keep the subscription alive and the client listening to new events.
- The Generic Subscribe and ManagedSubscribe (beta) RPC examples demonstrate a basic flow control strategy where a new `FetchRequest` or `ManagedFetchRequest` is sent only after the requested number of events in the previous requests are received. The ManagedSubscribe RPC example also shows how to commit a Replay ID by sending commit requests. Custom flow control strategies can be implemented as needed. More info on flow control available [here](https://developer.salesforce.com/docs/platform/pub-sub-api/guide/flow-control.html).
- The Generic Subscribe RPC example demonstrates error handling. After an exception occurs, it attempts to resubscribe after the last received event by implementing Binary Exponential Backoff. The example processes events and sends the retry requests asynchronously. If the error is an invalid replay ID,  it tries to resubscribe since the earliest stored event in the event bus. See the `onError()` method in `Subscribe.java`.

## 🚀 **Kody Payment Integration Quick Start**

### 1. Start the Subscriber (in one terminal)
```bash
./run.sh genericpubsub.KodyPaymentSubscriber sandbox
```

### 2. Publish Payment Requests (in another terminal)
```bash
# InitiatePayment
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.InitiatePayment '{
  "storeId": "7fbec013-34e1-4e93-a0ee-f4f91b94eb17",
  "paymentReference": "pay_123456",
  "amountMinorUnits": 1000,
  "currency": "GBP",
  "orderId": "order_123456",
  "returnUrl": "https://example.com/return",
  "payerEmailAddress": "test@example.com"
}'

# PaymentDetails
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.PaymentDetails '{
  "storeId": "7fbec013-34e1-4e93-a0ee-f4f91b94eb17",
  "paymentId": "your-payment-id"
}'

# GetPayments
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.GetPayments '{
  "storeId": "7fbec013-34e1-4e93-a0ee-f4f91b94eb17",
  "pageCursor": {"page": 1, "pageSize": 10}
}'

# Refund
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.Refund '{
  "storeId": "7fbec013-34e1-4e93-a0ee-f4f91b94eb17",
  "paymentId": "your-payment-id",
  "amount": "10.00"
}'
```

### 💡 **Copy-Paste Ready Commands**

```bash
# InitiatePayment with unique timestamp
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.InitiatePayment '{"storeId":"7fbec013-34e1-4e93-a0ee-f4f91b94eb17","paymentReference":"pay_1234567890","amountMinorUnits":1000,"currency":"GBP","orderId":"order_1234567890","returnUrl":"https://example.com/return","payerEmailAddress":"test@example.com"}'

# GetPayments
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.GetPayments '{"storeId":"7fbec013-34e1-4e93-a0ee-f4f91b94eb17","pageCursor":{"page":1,"pageSize":10}}'

# PaymentDetails (replace YOUR_PAYMENT_ID)
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.PaymentDetails '{"storeId":"7fbec013-34e1-4e93-a0ee-f4f91b94eb17","paymentId":"YOUR_PAYMENT_ID"}'

# Refund (replace YOUR_PAYMENT_ID)
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.Refund '{"storeId":"7fbec013-34e1-4e93-a0ee-f4f91b94eb17","paymentId":"YOUR_PAYMENT_ID","amount":"10.00"}'
```

### 3. Supported Kody APIs
- **InitiatePayment**: Creates a new payment request
- **PaymentDetails**: Retrieves details of a specific payment
- **GetPayments**: Gets a list of payments for a store
- **Refund**: Processes a refund for a specific payment

## 📋 **What Works**

1. **Publisher (`KodyPaymentPublisher`)**:
   - Accepts command-line arguments for different Kody API methods
   - Creates appropriate request payloads for each API
   - Publishes to Salesforce Pub/Sub and waits for responses
   - Supports correlation ID-based request-response pattern

2. **Subscriber (`KodyPaymentSubscriber`)**:
   - Receives events from Salesforce Pub/Sub in real-time
   - Routes requests to appropriate Kody API endpoints
   - Returns complete JSON responses for generic logging
   - Handles API responses and errors gracefully

3. **Event Flow**:
   - Complete end-to-end request-response processing
   - Real-time event delivery and processing
   - Proper error handling and logging

## 🔧 **Kody Configuration**

- **Environment**: `sandbox` (configured in `arguments-sandbox.yaml`)
- **Topic**: `/event/KodyPayment__e`
- **Replay Mode**: `LATEST` for publisher, configurable for subscriber
- **Kody API**: Staging environment (`grpc-staging-ap.kodypay.com`)

Required configuration in `arguments-sandbox.yaml`:
```yaml
# Kody gRPC API Hostname
KODY_HOSTNAME: grpc-staging-ap.kodypay.com
# Kody API Key for authentication
KODY_API_KEY: your-api-key-here
```

## 🐛 **Expected API Behavior**

When testing with demo credentials, you may see:
- `PERMISSION_DENIED: Invalid API Key` - Expected with demo credentials
- `INVALID_ARGUMENT: ValidationError` - Expected with test data

These errors confirm the integration is working - we're successfully reaching the Kody API.

## 🧪 **Testing the Integration**

### Comprehensive Integration Test
Run all APIs with a single command:
```bash
./run-test.sh sandbox
```

This will test:
- ✅ InitiatePayment API
- ✅ PaymentDetails API
- ✅ GetPayments API
- ✅ Refund API
- ✅ Error handling
- ✅ Concurrent requests

### Individual API Testing
Test specific APIs:
```bash
# Test InitiatePayment
./run.sh genericpubsub.KodyPaymentQuickTest sandbox InitiatePayment

# Test PaymentDetails
./run.sh genericpubsub.KodyPaymentQuickTest sandbox PaymentDetails <paymentId>

# Test GetPayments
./run.sh genericpubsub.KodyPaymentQuickTest sandbox GetPayments

# Test Refund
./run.sh genericpubsub.KodyPaymentQuickTest sandbox Refund <paymentId>
```

### Manual Testing
Use the generic publisher/subscriber setup:
```bash
# Terminal 1: Start subscriber
./run.sh genericpubsub.KodyPaymentSubscriber sandbox

# Terminal 2: Send requests with full JSON payloads
./run.sh genericpubsub.KodyPaymentPublisher sandbox ecom.v1.InitiatePayment '{"storeId":"7fbec013-34e1-4e93-a0ee-f4f91b94eb17","paymentReference":"pay_test","amountMinorUnits":1000,"currency":"GBP","orderId":"order_test","returnUrl":"https://example.com/return","payerEmailAddress":"test@example.com"}'
```

## 📁 **Key Files**

- `KodyPaymentPublisher.java` - Generic command-line publisher (accepts any method + JSON payload)
- `KodyPaymentSubscriber.java` - Real-time event subscriber with API integration
- `ApplicationConfig.java` - Configuration management for external settings
- `arguments-sandbox.yaml` - Environment-specific configuration
- `KodyPaymentManualTest.java` - Comprehensive integration test
- `KodyPaymentQuickTest.java` - Individual API testing utility
- `run-test.sh` - Test runner script
