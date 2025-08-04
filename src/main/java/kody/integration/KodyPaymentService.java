package kody.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import com.kodypay.grpc.ecom.v1.KodyEcomPaymentsServiceGrpc;
import com.kodypay.grpc.ecom.v1.PaymentInitiationRequest;
import com.kodypay.grpc.ecom.v1.PaymentInitiationResponse;
import com.kodypay.grpc.ecom.v1.PaymentDetailsRequest;
import com.kodypay.grpc.ecom.v1.PaymentDetailsResponse;
import com.kodypay.grpc.ecom.v1.GetPaymentsRequest;
import com.kodypay.grpc.ecom.v1.GetPaymentsResponse;
import com.kodypay.grpc.ecom.v1.RefundRequest;
import com.kodypay.grpc.ecom.v1.RefundResponse;
import com.salesforce.eventbus.protobuf.*;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.apache.avro.Schema;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.CommonContext;
import utility.ApplicationConfig;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Kody Payment Subscriber using full streaming for both pub and sub
 */
public class KodyPaymentService extends CommonContext {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentService.class);

    /**
     * Supported Kody Payment API Methods
     * 
     * All available methods are clearly defined here for easy management.
     * Each method includes its request/response types and the gRPC call function.
     * 
     * To add a new method:
     * 1. Add it to this enum with request/response method names and function
     * 2. That's it! The generic handler will take care of the rest
     */
    public enum PaymentMethod {
        INITIATE_PAYMENT(
            "request.ecom.v1.InitiatePayment", 
            "response.ecom.v1.InitiatePayment",
            PaymentInitiationRequest.newBuilder(),
            (service, request, apiKey) -> service.callKodyInitiatePayment((PaymentInitiationRequest) request, apiKey)
        ),
        PAYMENT_DETAILS(
            "request.ecom.v1.PaymentDetails", 
            "response.ecom.v1.PaymentDetails",
            PaymentDetailsRequest.newBuilder(),
            (service, request, apiKey) -> service.callKodyPaymentDetails((PaymentDetailsRequest) request, apiKey)
        ),
        GET_PAYMENTS(
            "request.ecom.v1.GetPayments", 
            "response.ecom.v1.GetPayments",
            GetPaymentsRequest.newBuilder(),
            (service, request, apiKey) -> service.callKodyGetPayments((GetPaymentsRequest) request, apiKey)
        ),
        REFUND(
            "request.ecom.v1.Refund", 
            "response.ecom.v1.Refund",
            RefundRequest.newBuilder(),
            (service, request, apiKey) -> service.callKodyRefund((RefundRequest) request, apiKey)
        );

        private final String requestMethod;
        private final String responseMethod;
        private final com.google.protobuf.Message.Builder requestBuilder;
        private final KodyApiFunction kodyApiFunction;

        @FunctionalInterface
        interface KodyApiFunction {
            com.google.protobuf.Message call(KodyPaymentService service, com.google.protobuf.Message request, String apiKey) throws Exception;
        }

        PaymentMethod(String requestMethod, String responseMethod, 
                     com.google.protobuf.Message.Builder requestBuilder,
                     KodyApiFunction kodyApiFunction) {
            this.requestMethod = requestMethod;
            this.responseMethod = responseMethod;
            this.requestBuilder = requestBuilder;
            this.kodyApiFunction = kodyApiFunction;
        }

        public com.google.protobuf.Message.Builder getRequestBuilder() { 
            return requestBuilder.clone(); 
        }
        
        public com.google.protobuf.Message callKodyApi(KodyPaymentService service, com.google.protobuf.Message request, String apiKey) throws Exception {
            return kodyApiFunction.call(service, request, apiKey);
        }

        public String getRequestMethod() { return requestMethod; }
        public String getResponseMethod() { return responseMethod; }

        public static PaymentMethod fromRequestMethod(String method) {
            for (PaymentMethod pm : values()) {
                if (pm.requestMethod.equals(method)) {
                    return pm;
                }
            }
            return null;
        }

        /**
         * Get all available request methods for logging/documentation
         */
        public static String[] getAllRequestMethods() {
            return java.util.Arrays.stream(values())
                    .map(PaymentMethod::getRequestMethod)
                    .toArray(String[]::new);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int TIMEOUT_SECONDS = 300;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final ReplayPreset replayPreset;
    private final ByteString customReplayId;
    private final String kodyHostname;

    private StreamObserver<FetchRequest> subscribeStream;
    private StreamObserver<PublishRequest> publishStream;

    public KodyPaymentService(ApplicationConfig config) {
        super(config);

        // Read Kody config from YAML
        this.kodyHostname = config.getKodyHostname();

        // Validate Kody configuration
        if (this.kodyHostname == null || this.kodyHostname.isEmpty()) {
            throw new IllegalArgumentException("KODY_HOSTNAME is required in configuration");
        }

        String topic = "/event/KodyPayment__e";
        setupTopicDetails(topic, false, true);

        // Verify publish permissions
        try {
            TopicInfo pubTopicInfo = blockingStub.getTopic(TopicRequest.newBuilder().setTopicName(topic).build());
            if (!pubTopicInfo.getCanPublish()) {
                throw new IllegalArgumentException("Topic not available for publish");
            }
            logger.info("✅ Topic supports both subscribe and publish");
        } catch (Exception e) {
            logger.error("❌ Cannot verify publish permissions", e);
            throw e;
        }

        this.replayPreset = config.getReplayPreset();
        this.customReplayId = config.getReplayId();

        // Log all available payment methods on startup
        logger.info("🚀 Kody Payment Service initialized with {} methods:", PaymentMethod.values().length);
        for (PaymentMethod method : PaymentMethod.values()) {
            logger.info("   📋 {} → {}", method.getRequestMethod(), method.getResponseMethod());
        }

        initializeStreams();
    }

    private void initializeStreams() {
        // Initialize publish stream
        StreamObserver<PublishResponse> publishResponseObserver = new StreamObserver<PublishResponse>() {
            @Override
            public void onNext(PublishResponse response) {
                for (PublishResult result : response.getResultsList()) {
                    if (result.hasError()) {
                        logger.error("❌ Publish error: {}", result.getError().getMsg());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("❌ Publish stream error", t);
            }

            @Override
            public void onCompleted() {
                logger.info("✅ Publish stream completed");
            }
        };

        publishStream = asyncStub.publishStream(publishResponseObserver);
        logger.info("✅ Publish stream initialized");
    }

    public void subscribeAndProcessPayments() {
        logger.info("🚀 Starting subscription on: {}", busTopicName);

        StreamObserver<FetchResponse> subscribeResponseObserver = new StreamObserver<FetchResponse>() {
            @Override
            public void onNext(FetchResponse fetchResponse) {
                logger.info("📨 Received {} events", fetchResponse.getEventsCount());

                for (ConsumerEvent event : fetchResponse.getEventsList()) {
                    processPaymentEvent(event);
                }

                // Continue polling
                if (subscribeStream != null) {
                    FetchRequest fetchRequest = FetchRequest.newBuilder()
                            .setTopicName(busTopicName)
                            .setNumRequested(10)
                            .build();
                    subscribeStream.onNext(fetchRequest);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("❌ Subscribe stream error", throwable);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("✅ Subscribe stream completed");
                latch.countDown();
            }
        };

        subscribeStream = asyncStub.subscribe(subscribeResponseObserver);

        FetchRequest.Builder fetchRequestBuilder = FetchRequest.newBuilder()
                .setTopicName(busTopicName)
                .setReplayPreset(replayPreset)
                .setNumRequested(10);

        if (replayPreset == ReplayPreset.CUSTOM) {
            fetchRequestBuilder.setReplayId(customReplayId);
        }

        logger.info("🔍 Starting subscription with replay preset: {}", replayPreset);
        subscribeStream.onNext(fetchRequestBuilder.build());
        logger.info("✅ Subscription active, waiting for events...");

        // Keep alive with periodic polling
        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(5000);
                    if (subscribeStream != null) {
                        FetchRequest fetchRequest = FetchRequest.newBuilder()
                                .setTopicName(busTopicName)
                                .setNumRequested(10)
                                .build();
                        subscribeStream.onNext(fetchRequest);
                        logger.debug("🔄 Sent periodic fetch request");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("❌ Polling error", e);
            }
        }).start();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    break;
                }
                logger.info("⏳ Still listening...");
            }
        } catch (InterruptedException e) {
            logger.info("🛑 Subscription interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private void processPaymentEvent(ConsumerEvent event) {
        try {
            Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
            GenericRecord record = deserialize(schema, event.getEvent().getPayload());

            String correlationId = safeGetString(record, "correlation_id__c");
            String method = safeGetString(record, "method__c");
            String payload = safeGetString(record, "payload__c");
            String apiKey = safeGetString(record, "api_key__c");

            if (method == null || !method.startsWith("request.")) {
                return;
            }

            logger.info("💳 Processing request: {}", method);
            processKodyPaymentRequest(correlationId, method, payload, apiKey);

        } catch (Exception e) {
            logger.error("❌ Error processing event", e);
        }
    }

    private void processKodyPaymentRequest(String correlationId, String method, String payload, String apiKey) {
        try {
            // API key must be provided in the event (proxy pattern)
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("API key is required in event payload");
            }
            
            logger.info("🔑 Using API key: {}***", apiKey.substring(0, Math.min(8, apiKey.length())));
            
            JsonNode payloadJson = objectMapper.readTree(payload);

            // Look up the payment method from our registry
            PaymentMethod paymentMethod = PaymentMethod.fromRequestMethod(method);
            
            if (paymentMethod == null) {
                logger.error("❌ Unsupported method: {} (Available: {})", method, 
                    String.join(", ", PaymentMethod.getAllRequestMethods()));
                publishErrorResponse(correlationId, "Unsupported method: " + method);
                return;
            }

            logger.info("💳 Processing method: {} → {}", paymentMethod.getRequestMethod(), paymentMethod.getResponseMethod());
            
            String responseJson = processPaymentMethod(paymentMethod, payloadJson, apiKey);
            publishResponse(correlationId, paymentMethod.getResponseMethod(), responseJson);

        } catch (Exception e) {
            logger.error("❌ Error processing request", e);
            publishErrorResponse(correlationId, "Error: " + e.getMessage());
        }
    }

    /**
     * Generic method processor - handles all payment methods using the enum configuration
     */
    private String processPaymentMethod(PaymentMethod paymentMethod, JsonNode payloadJson, String apiKey) throws Exception {
        logger.info("📦 Request: {}", payloadJson.toString());

        // Parse JSON to protobuf request using the method's builder
        com.google.protobuf.Message.Builder requestBuilder = paymentMethod.getRequestBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson.toString(), requestBuilder);
        com.google.protobuf.Message request = requestBuilder.build();

        // Call the Kody API using the method's function
        com.google.protobuf.Message response = paymentMethod.callKodyApi(this, request, apiKey);

        // Convert response to JSON
        String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
        logger.info("📦 Response: {}", responseJson);
        
        return responseJson;
    }


    private PaymentInitiationResponse callKodyInitiatePayment(PaymentInitiationRequest request, String apiKey) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel, apiKey);

            PaymentInitiationResponse response = kodyClient.initiatePayment(request);

            // Log the complete response JSON
            String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
            logger.info("✅ Kody API Response: {}", responseJson);

            return response;

        } catch (Exception e) {
            logger.error("❌ Kody API error", e);
            return PaymentInitiationResponse.newBuilder()
                    .setError(PaymentInitiationResponse.Error.newBuilder()
                            .setType(PaymentInitiationResponse.Error.Type.UNKNOWN)
                            .setMessage("Error: " + e.getMessage()))
                    .build();
        } finally {
            if (kodyChannel != null) {
                shutdownChannel(kodyChannel);
            }
        }
    }

    private PaymentDetailsResponse callKodyPaymentDetails(PaymentDetailsRequest request, String apiKey) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel, apiKey);

            return kodyClient.paymentDetails(request);

        } catch (Exception e) {
            logger.error("❌ Kody API error", e);
            return PaymentDetailsResponse.newBuilder()
                    .setError(PaymentDetailsResponse.Error.newBuilder()
                            .setType(PaymentDetailsResponse.Error.Type.UNKNOWN)
                            .setMessage("Error: " + e.getMessage()))
                    .build();
        } finally {
            if (kodyChannel != null) {
                shutdownChannel(kodyChannel);
            }
        }
    }

    private GetPaymentsResponse callKodyGetPayments(GetPaymentsRequest request, String apiKey) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel, apiKey);

            return kodyClient.getPayments(request);

        } catch (Exception e) {
            logger.error("❌ Kody API error", e);
            return GetPaymentsResponse.newBuilder()
                    .setError(GetPaymentsResponse.Error.newBuilder()
                            .setType(GetPaymentsResponse.Error.Type.UNKNOWN)
                            .setMessage("Error: " + e.getMessage()))
                    .build();
        } finally {
            if (kodyChannel != null) {
                shutdownChannel(kodyChannel);
            }
        }
    }

    private RefundResponse callKodyRefund(RefundRequest request, String apiKey) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel, apiKey);

            // The refund method returns a stream, so we need to get the first response
            java.util.Iterator<RefundResponse> responseIterator = kodyClient.refund(request);
            if (responseIterator.hasNext()) {
                return responseIterator.next();
            } else {
                return RefundResponse.newBuilder()
                        .setStatus(RefundResponse.RefundStatus.FAILED)
                        .setFailureReason("No response received from Kody API")
                        .build();
            }

        } catch (Exception e) {
            logger.error("❌ Kody API error", e);
            return RefundResponse.newBuilder()
                    .setStatus(RefundResponse.RefundStatus.FAILED)
                    .setFailureReason("Error: " + e.getMessage())
                    .build();
        } finally {
            if (kodyChannel != null) {
                shutdownChannel(kodyChannel);
            }
        }
    }

    private KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub createKodyClient(ManagedChannel channel, String apiKey) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("X-API-Key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

        return KodyEcomPaymentsServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void publishResponse(String correlationId, String responseMethod, String responsePayload) {
        try {
            GenericRecord responseEvent = createResponseEvent(correlationId, responseMethod, responsePayload);
            PublishRequest publishRequest = createPublishRequest(responseEvent);

            publishStream.onNext(publishRequest);
            logger.info("📤 Response sent - Correlation: {}", correlationId);

        } catch (Exception e) {
            logger.error("❌ Error publishing response", e);
        }
    }

    private void publishErrorResponse(String correlationId, String errorMessage) {
        try {
            String errorPayload = String.format("{\"error\": {\"message\": \"%s\"}}",
                    errorMessage.replace("\"", "\\\""));
            publishResponse(correlationId, "response.error", errorPayload);
        } catch (Exception e) {
            logger.error("❌ Error publishing error response", e);
        }
    }

    private GenericRecord createResponseEvent(String correlationId, String method, String payload) throws Exception {
        Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
        GenericRecordBuilder builder = new GenericRecordBuilder(schema);

        setFieldIfExists(builder, schema, "CreatedDate", System.currentTimeMillis());
        setFieldIfExists(builder, schema, "CreatedById", this.userId);
        setFieldIfExists(builder, schema, "correlation_id__c", correlationId);
        setFieldIfExists(builder, schema, "method__c", method);
        setFieldIfExists(builder, schema, "payload__c", payload);

        return builder.build();
    }

    private PublishRequest createPublishRequest(GenericRecord event) throws Exception {
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(event.getSchema());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(buffer, null);
        writer.write(event, encoder);

        ProducerEvent producerEvent = ProducerEvent.newBuilder()
                .setSchemaId(schemaInfo.getSchemaId())
                .setPayload(ByteString.copyFrom(buffer.toByteArray()))
                .build();

        return PublishRequest.newBuilder()
                .setTopicName("/event/KodyPayment__e")
                .addEvents(producerEvent)
                .build();
    }

    private void setFieldIfExists(GenericRecordBuilder builder, Schema schema, String fieldName, Object value) {
        if (schema.getField(fieldName) != null) {
            builder.set(fieldName, value);
        }
    }

    private String safeGetString(GenericRecord record, String fieldName) {
        try {
            Object value = record.get(fieldName);
            return value != null ? value.toString() : null;
        } catch (AvroRuntimeException e) {
            return null;
        }
    }

    @Override
    public void close() {
        logger.info("🛑 Shutting down streams...");
        if (publishStream != null) {
            try {
                publishStream.onCompleted();
            } catch (Exception e) {
                logger.debug("Publish stream already closed");
            }
        }
        if (subscribeStream != null) {
            try {
                subscribeStream.onCompleted();
            } catch (Exception e) {
                logger.debug("Subscribe stream already closed");
            }
        }
        super.close();
    }

    public static void main(String[] args) {
        System.out.println("🔧 DEBUG: Main method started");
        if (args.length < 1) {
            System.out.println("🔧 DEBUG: No arguments provided");
            logger.error("❌ Usage: java -cp app.jar kody.integration.KodyPaymentService sandbox");
            logger.error("   Or: ./run.sh kody.integration.KodyPaymentService sandbox");
            return;
        }

        String environment = args[0];
        System.out.println("🔧 DEBUG: Environment: " + environment);
        logger.info("🚀 Starting Kody Payment Subscriber for environment: {}", environment);

        try {
            ApplicationConfig config = new ApplicationConfig("arguments-" + environment + ".yaml");

            try (KodyPaymentService subscriber = new KodyPaymentService(config)) {
                subscriber.subscribeAndProcessPayments();
            }
        } catch (Exception e) {
            logger.error("❌ Error running subscriber", e);
        }
    }
}