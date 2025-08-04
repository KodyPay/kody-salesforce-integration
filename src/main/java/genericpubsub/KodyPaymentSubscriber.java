package genericpubsub;

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
import io.grpc.stub.ClientCallStreamObserver;
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
public class KodyPaymentSubscriber extends CommonContext {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentSubscriber.class);


    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int TIMEOUT_SECONDS = 300;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final ReplayPreset replayPreset;
    private final ByteString customReplayId;
    private final String kodyHostname;
    private final String kodyApiKey;

    private StreamObserver<FetchRequest> subscribeStream;
    private StreamObserver<PublishRequest> publishStream;

    public KodyPaymentSubscriber(ApplicationConfig exampleConfigurations) {
        super(exampleConfigurations);

        // Read Kody config from YAML
        this.kodyHostname = exampleConfigurations.getKodyHostname();
        this.kodyApiKey = exampleConfigurations.getKodyApiKey();
        
        // Validate Kody configuration
        if (this.kodyHostname == null || this.kodyHostname.isEmpty()) {
            throw new IllegalArgumentException("KODY_HOSTNAME is required in configuration");
        }
        if (this.kodyApiKey == null || this.kodyApiKey.isEmpty()) {
            throw new IllegalArgumentException("KODY_API_KEY is required in configuration");
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

        this.replayPreset = exampleConfigurations.getReplayPreset();
        this.customReplayId = exampleConfigurations.getReplayId();

        initializeStreams();
    }

    private void initializeStreams() {
        // Initialize publish stream
        StreamObserver<PublishResponse> publishResponseObserver = new StreamObserver<PublishResponse>() {
            @Override
            public void onNext(PublishResponse response) {
                logger.info("📡 Published with RPC ID: {}", response.getRpcId());
                for (PublishResult result : response.getResultsList()) {
                    if (result.hasError()) {
                        logger.error("❌ Publish error: {}", result.getError().getMsg());
                    } else {
                        logger.info("✅ Published with replay ID: {}", result.getReplayId().toStringUtf8());
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
            logger.info("🎯 Processing event with replay ID: {}", event.getReplayId().toStringUtf8());

            Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
            GenericRecord record = deserialize(schema, event.getEvent().getPayload());

            String correlationId = safeGetString(record, "correlation_id__c");
            String method = safeGetString(record, "method__c");
            String payload = safeGetString(record, "payload__c");

            logger.info("🔧 Event - Correlation: {}, Method: {}", correlationId, method);

            if (method == null || !method.startsWith("request.")) {
                logger.debug("ℹ️ Ignoring non-request method: {}", method);
                return;
            }

            logger.info("💳 Processing request: {}", method);
            processKodyPaymentRequest(correlationId, method, payload);

        } catch (Exception e) {
            logger.error("❌ Error processing event", e);
        }
    }

    private void processKodyPaymentRequest(String correlationId, String method, String payload) {
        try {
            JsonNode payloadJson = objectMapper.readTree(payload);

            String responseJson;
            switch (method) {
                case "request.ecom.v1.InitiatePayment":
                    responseJson = handleInitiatePayment(payloadJson);
                    publishResponse(correlationId, "response.ecom.v1.InitiatePayment", responseJson);
                    break;

                case "request.ecom.v1.PaymentDetails":
                    responseJson = handlePaymentDetails(payloadJson);
                    publishResponse(correlationId, "response.ecom.v1.PaymentDetails", responseJson);
                    break;

                case "request.ecom.v1.GetPayments":
                    responseJson = handleGetPayments(payloadJson);
                    publishResponse(correlationId, "response.ecom.v1.GetPayments", responseJson);
                    break;

                case "request.ecom.v1.Refund":
                    responseJson = handleRefund(payloadJson);
                    publishResponse(correlationId, "response.ecom.v1.Refund", responseJson);
                    break;

                default:
                    logger.error("❌ Unsupported method: {}", method);
                    publishErrorResponse(correlationId, "Unsupported method: " + method);
            }

        } catch (Exception e) {
            logger.error("❌ Error processing request", e);
            publishErrorResponse(correlationId, "Error: " + e.getMessage());
        }
    }

    private String handleInitiatePayment(JsonNode payloadJson) throws Exception {
        logger.info("📦 Request: {}", payloadJson.toString());

        PaymentInitiationRequest.Builder requestBuilder = PaymentInitiationRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson.toString(), requestBuilder);
        PaymentInitiationRequest request = requestBuilder.build();

        PaymentInitiationResponse response = callKodyInitiatePayment(request);

        String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
        logger.info("📦 Response: {}", responseJson);
        return responseJson;
    }

    private String handlePaymentDetails(JsonNode payloadJson) throws Exception {
        logger.info("📦 Request: {}", payloadJson.toString());

        PaymentDetailsRequest.Builder requestBuilder = PaymentDetailsRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson.toString(), requestBuilder);
        PaymentDetailsRequest request = requestBuilder.build();

        PaymentDetailsResponse response = callKodyPaymentDetails(request);

        String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
        logger.info("📦 Response: {}", responseJson);
        return responseJson;
    }

    private PaymentInitiationResponse callKodyInitiatePayment(PaymentInitiationRequest request) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel);

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

    private PaymentDetailsResponse callKodyPaymentDetails(PaymentDetailsRequest request) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel);

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

    private KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub createKodyClient(ManagedChannel channel) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), kodyApiKey);

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

    private String handleGetPayments(JsonNode payloadJson) throws Exception {
        logger.info("📦 Request: {}", payloadJson.toString());

        GetPaymentsRequest.Builder requestBuilder = GetPaymentsRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson.toString(), requestBuilder);
        GetPaymentsRequest request = requestBuilder.build();

        GetPaymentsResponse response = callKodyGetPayments(request);

        String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
        logger.info("📦 Response: {}", responseJson);
        return responseJson;
    }

    private GetPaymentsResponse callKodyGetPayments(GetPaymentsRequest request) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel);

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

    private String handleRefund(JsonNode payloadJson) throws Exception {
        logger.info("📦 Request: {}", payloadJson.toString());

        RefundRequest.Builder requestBuilder = RefundRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(payloadJson.toString(), requestBuilder);
        RefundRequest request = requestBuilder.build();

        RefundResponse response = callKodyRefund(request);

        String responseJson = JsonFormat.printer().omittingInsignificantWhitespace().print(response);
        logger.info("📦 Response: {}", responseJson);
        return responseJson;
    }

    private RefundResponse callKodyRefund(RefundRequest request) {
        ManagedChannel kodyChannel = null;
        try {
            kodyChannel = ManagedChannelBuilder.forAddress(kodyHostname, 443)
                    .useTransportSecurity()
                    .build();

            KodyEcomPaymentsServiceGrpc.KodyEcomPaymentsServiceBlockingStub kodyClient = createKodyClient(kodyChannel);

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
    private void publishResponse(String correlationId, String responseMethod, String responsePayload) {
        try {
            logger.info("📤 Publishing response - Correlation: {}", correlationId);

            GenericRecord responseEvent = createResponseEvent(correlationId, responseMethod, responsePayload);
            PublishRequest publishRequest = createPublishRequest(responseEvent);

            publishStream.onNext(publishRequest);
            logger.info("🎉 Response sent!");

        } catch (Exception e) {
            logger.error("❌ Error publishing response", e);
        }
    }

    private void publishErrorResponse(String correlationId, String errorMessage) {
        try {
            String errorPayload = String.format("{\"error\": {\"message\": \"%s\"}}",
                    errorMessage.replace("\"", "\\\""));
            publishResponse(correlationId, "error", errorPayload);
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
        if (args.length < 1) {
            logger.error("❌ Usage: java -cp app.jar genericpubsub.KodyPaymentSubscriber sandbox");
            logger.error("   Or: ./run.sh genericpubsub.KodyPaymentSubscriber sandbox");
            return;
        }

        String environment = args[0];
        logger.info("🚀 Starting Kody Payment Subscriber for environment: {}", environment);

        try {
            ApplicationConfig exampleConfigurations = new ApplicationConfig("arguments-" + environment + ".yaml");

            try (KodyPaymentSubscriber subscriber = new KodyPaymentSubscriber(exampleConfigurations)) {
                subscriber.subscribeAndProcessPayments();
            }
        } catch (Exception e) {
            logger.error("❌ Error running subscriber", e);
        }
    }
}