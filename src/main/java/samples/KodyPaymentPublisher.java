package samples;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.*;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kody Payment Publisher:
 * 1. Publishes requests to KodyPayment__e with method and JSON payload
 * 2. Subscribes to same topic waiting for responses with matching correlation ID
 */
public class KodyPaymentPublisher extends CommonContext {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentPublisher.class);
    private final int TIMEOUT_SECONDS = 30;

    private final String kodyApiKey;
    private final Map<String, CountDownLatch> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, PaymentResponse> responses = new ConcurrentHashMap<>();
    private final Map<String, List<PaymentResponse>> streamingResponses = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> streamingLatches = new ConcurrentHashMap<>();
    private volatile boolean responseSubscriberRunning = false;
    private StreamObserver<FetchRequest> responseStream;

    public static class PaymentResponse {
        private final String correlationId;
        private final String method;
        private final String payload;

        public PaymentResponse(String correlationId, String method, String payload) {
            this.correlationId = correlationId;
            this.method = method;
            this.payload = payload;
        }

        public String getCorrelationId() { return correlationId; }
        public String getMethod() { return method; }
        public String getPayload() { return payload; }

        @Override
        public String toString() {
            return String.format("PaymentResponse{correlationId='%s', method='%s', payload='%s'}",
                    correlationId, method, payload);
        }
    }

    public KodyPaymentPublisher(ApplicationConfig config) {
        super(config);
        
        // Read Kody API key from configuration for proxy usage
        this.kodyApiKey = config.getKodyApiKey();

        String publishTopic = "/event/KodyPayment__e";
        setupTopicDetails(publishTopic, true, true);
        logger.info("🔧 Kody Payment Publisher using topic: {}", publishTopic);

        startResponseSubscriber();

        try {
            Thread.sleep(3000);
            logger.info("✅ Response subscriber ready!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public PaymentResponse sendPaymentRequestAndWaitForResponse(String correlationId, String method, String payload, int timeoutSeconds) throws Exception {
        return sendPaymentRequestAndWaitForResponseWithCustomApiKey(correlationId, method, payload, this.kodyApiKey, timeoutSeconds);
    }

    public PaymentResponse sendPaymentRequestAndWaitForResponseWithCustomApiKey(String correlationId, String method, String payload, String apiKey, int timeoutSeconds) throws Exception {
        // Check if this is a streaming method
        if (method != null && method.contains("Stream")) {
            logger.info("🌊 Detected streaming method: {} - waiting for multiple responses", method);
            return sendStreamingPaymentRequestWithCustomApiKey(correlationId, method, payload, apiKey, timeoutSeconds);
        }
        
        CountDownLatch responseLatch = new CountDownLatch(1);
        pendingRequests.put(correlationId, responseLatch);

        try {
            logger.info("💳 Sending payment request - Correlation: {}, Method: {}, Timeout: {}s", correlationId, method, timeoutSeconds);

            publishPaymentRequestWithCustomApiKey(correlationId, method, payload, apiKey);
            if (responseLatch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                PaymentResponse response = responses.remove(correlationId);
                if (response != null) {
                    logger.info("✅ Response received - Correlation: {}", correlationId);
                    return response;
                } else {
                    throw new RuntimeException("Response received but data was null");
                }
            } else {
                throw new RuntimeException("Timeout waiting for response after " + timeoutSeconds + " seconds");
            }
        } finally {
            pendingRequests.remove(correlationId);
            responses.remove(correlationId);
        }
    }

    /**
     * Special handler for streaming payment methods (e.g., InitiatePaymentStream)
     * Waits for multiple responses with the same correlation ID until payment is completed
     */
    private PaymentResponse sendStreamingPaymentRequestWithCustomApiKey(String correlationId, String method, String payload, String apiKey, int timeoutSeconds) throws Exception {
        // Initialize collection for streaming responses
        List<PaymentResponse> allResponses = new ArrayList<>();
        streamingResponses.put(correlationId, allResponses);
        
        // Use a special latch for streaming - we don't know exact count, so we'll use timeout
        CountDownLatch firstResponseLatch = new CountDownLatch(1);
        pendingRequests.put(correlationId, firstResponseLatch);

        try {
            logger.info("🌊 Sending streaming payment request - Correlation: {}, Method: {}, Timeout: {}s", 
                correlationId, method, timeoutSeconds);

            publishPaymentRequestWithCustomApiKey(correlationId, method, payload, apiKey);
            
            // Wait for first response to ensure stream started
            if (!firstResponseLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("No initial response received after 10 seconds");
            }
            
            logger.info("📦 First response received, continuing to listen for updates...");
            
            // Continue collecting responses until we see a terminal state or timeout
            long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
            PaymentResponse lastResponse = null;
            boolean isComplete = false;
            
            while (System.currentTimeMillis() < endTime && !isComplete) {
                Thread.sleep(1000); // Check every second
                
                List<PaymentResponse> currentResponses = streamingResponses.get(correlationId);
                if (currentResponses != null && !currentResponses.isEmpty()) {
                    lastResponse = currentResponses.get(currentResponses.size() - 1);
                    
                    // Check if payment reached terminal state
                    String responsePayload = lastResponse.getPayload();
                    if (responsePayload != null) {
                        // Check for successful payment completion indicators
                        if (responsePayload.contains("\"status\":\"COMPLETED\"") || 
                            responsePayload.contains("\"status\":\"SUCCESS\"") ||
                            responsePayload.contains("\"paid\":true") ||
                            responsePayload.contains("PAYMENT_CONFIRMED")) {
                            logger.info("✅ Payment completed successfully!");
                            isComplete = true;
                        } else if (responsePayload.contains("\"status\":\"FAILED\"") || 
                                 responsePayload.contains("\"status\":\"CANCELLED\"")) {
                            logger.warn("❌ Payment failed or cancelled");
                            isComplete = true;
                        }
                    }
                    
                    // Log the full payload for stream updates
                    logger.info("📊 Stream update #{}: {}", currentResponses.size(), lastResponse.getPayload());
                }
            }
            
            List<PaymentResponse> finalResponses = streamingResponses.get(correlationId);
            if (finalResponses != null && !finalResponses.isEmpty()) {
                logger.info("🎉 Stream completed with {} total responses", finalResponses.size());
                // Return the last response which should have the final state
                return finalResponses.get(finalResponses.size() - 1);
            } else {
                throw new RuntimeException("No responses received from stream");
            }
            
        } finally {
            pendingRequests.remove(correlationId);
            streamingResponses.remove(correlationId);
            responses.remove(correlationId);
        }
    }

    public void publishPaymentRequest(String correlationId, String method, String payload) throws Exception {
        publishPaymentRequestWithCustomApiKey(correlationId, method, payload, this.kodyApiKey);
    }

    public void publishPaymentRequestWithCustomApiKey(String correlationId, String method, String payload, String apiKey) throws Exception {
        logger.info("📡 Publishing payment request...");

        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicReference<CountDownLatch> finishLatchRef = new AtomicReference<>(finishLatch);
        final int numExpectedPublishResponses = 1;
        final List<PublishResponse> publishResponses = Lists.newArrayListWithExpectedSize(numExpectedPublishResponses);
        AtomicInteger failed = new AtomicInteger(0);

        StreamObserver<PublishResponse> pubObserver = getDefaultPublishStreamObserver(finishLatchRef,
                numExpectedPublishResponses, publishResponses, failed);

        ClientCallStreamObserver<PublishRequest> requestObserver = (ClientCallStreamObserver<PublishRequest>) asyncStub.publishStream(pubObserver);

        PublishRequest publishRequest = generatePaymentPublishRequestWithCustomApiKey(correlationId, method, payload, apiKey);
        requestObserver.onNext(publishRequest);

        validatePublishResponse(finishLatch, numExpectedPublishResponses, publishResponses, failed, 1);
        requestObserver.onCompleted();

        logger.info("✅ Payment request published successfully");
    }

    private void startResponseSubscriber() {
        if (!responseSubscriberRunning) {
            synchronized (this) {
                if (!responseSubscriberRunning) {
                    responseSubscriberRunning = true;
                    logger.info("🔄 Starting response subscriber...");

                    Thread responseThread = new Thread(this::subscribeToResponseTopic);
                    responseThread.setName("ResponseSubscriber");
                    responseThread.setDaemon(true);
                    responseThread.start();
                }
            }
        }
    }

    private void subscribeToResponseTopic() {
        try {
            logger.info("🔗 Starting response subscription to KodyPayment__e");

            StreamObserver<FetchResponse> responseObserver = new StreamObserver<FetchResponse>() {
                @Override
                public void onNext(FetchResponse fetchResponse) {
                    logger.info("📡 Response subscription received {} events", fetchResponse.getEventsCount());

                    for (ConsumerEvent event : fetchResponse.getEventsList()) {
                        try {
                            logger.info("🎯 Received ANY event - processing...");
                            processResponseEvent(event);
                        } catch (Exception e) {
                            logger.warn("⚠️ Error processing response event: {}", e.getMessage());
                        }
                    }

                    if (responseStream != null && responseSubscriberRunning) {
                        try {
                            FetchRequest fetchRequest = FetchRequest.newBuilder()
                                    .setTopicName("/event/KodyPayment__e")
                                    .setNumRequested(100)
                                    .build();
                            responseStream.onNext(fetchRequest);
                        } catch (Exception e) {
                            logger.debug("Response stream polling stopped: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("❌ Error in response subscription", throwable);
                }

                @Override
                public void onCompleted() {
                    logger.info("✅ Response subscription completed");
                }
            };

            responseStream = asyncStub.subscribe(responseObserver);

            FetchRequest.Builder fetchRequestBuilder = FetchRequest.newBuilder()
                    .setTopicName("/event/KodyPayment__e")
                    .setReplayPreset(ReplayPreset.LATEST)
                    .setNumRequested(100);

            logger.info("🔍 Starting response subscription with LATEST replay preset");
            responseStream.onNext(fetchRequestBuilder.build());
            logger.info("✅ Response subscription active");

            while (responseSubscriberRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000); // Poll less frequently

                    if (responseStream != null && responseSubscriberRunning) {
                        FetchRequest fetchRequest = FetchRequest.newBuilder()
                                .setTopicName("/event/KodyPayment__e")
                                .setNumRequested(100)
                                .build();
                        responseStream.onNext(fetchRequest);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("⚠️ Error in response polling: {}", e.getMessage());
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("❌ Error in response subscription", e);
        }
    }

    private void processResponseEvent(ConsumerEvent event) {
        try {
            Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
            GenericRecord record = deserialize(schema, event.getEvent().getPayload());

            String correlationId = extractFieldAsString(record, "correlation_id__c");
            String method = extractFieldAsString(record, "method__c");
            String payload = extractFieldAsString(record, "payload__c");

            boolean isPendingRequest = pendingRequests.containsKey(correlationId);
            boolean isStreamingRequest = streamingResponses.containsKey(correlationId);
            boolean isResponseMethod = method != null && method.startsWith("response.");

            logger.info("🔍 isPending: {}, isStreaming: {}, isResponse: {}", isPendingRequest, isStreamingRequest, isResponseMethod);

            if ((isPendingRequest || isStreamingRequest) && isResponseMethod) {
                logger.info("🎉 Found matching response - Correlation: {}", correlationId);

                PaymentResponse response = new PaymentResponse(correlationId, method, payload);
                
                // Handle streaming responses
                if (isStreamingRequest) {
                    List<PaymentResponse> streamResponses = streamingResponses.get(correlationId);
                    if (streamResponses != null) {
                        streamResponses.add(response);
                        logger.info("📊 Added streaming response #{} for correlation: {}", 
                            streamResponses.size(), correlationId);
                    }
                }
                
                // Also store in regular responses map
                responses.put(correlationId, response);

                // Count down the latch for first response
                CountDownLatch latch = pendingRequests.get(correlationId);
                if (latch != null && latch.getCount() > 0) {
                    latch.countDown();
                }
                
                // Count down streaming latch if exists
                CountDownLatch streamLatch = streamingLatches.get(correlationId);
                if (streamLatch != null && streamLatch.getCount() > 0) {
                    streamLatch.countDown();
                }
            } else {
                logger.info("ℹ️ Ignoring event - isPending: {}, isStreaming: {}, isResponse: {}", 
                    isPendingRequest, isStreamingRequest, isResponseMethod);
            }

        } catch (Exception e) {
            logger.warn("⚠️ Error processing response event: {}", e.getMessage());
        }
    }

    private String extractFieldAsString(GenericRecord record, String fieldName) {
        try {
            Object value = record.get(fieldName);
            return value != null ? value.toString() : null;
        } catch (AvroRuntimeException e) {
            return null;
        }
    }

    private PublishRequest generatePaymentPublishRequest(String correlationId, String method, String payload) throws IOException {
        return generatePaymentPublishRequestWithCustomApiKey(correlationId, method, payload, this.kodyApiKey);
    }

    private PublishRequest generatePaymentPublishRequestWithCustomApiKey(String correlationId, String method, String payload, String apiKey) throws IOException {
        setupTopicDetails("/event/KodyPayment__e", true, true);

        Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
        GenericRecord event = createPaymentRequestEvent(schema, correlationId, method, payload, apiKey);

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

    public GenericRecord createPaymentRequestEvent(Schema schema, String correlationId, String method, String payload, String apiKey) {
        GenericRecordBuilder builder = new GenericRecordBuilder(schema);

        setFieldIfExists(builder, schema, "CreatedDate", System.currentTimeMillis());
        setFieldIfExists(builder, schema, "CreatedById", this.userId);
        setFieldIfExists(builder, schema, "correlation_id__c", correlationId);
        setFieldIfExists(builder, schema, "method__c", method);
        setFieldIfExists(builder, schema, "payload__c", payload);
        setFieldIfExists(builder, schema, "api_key__c", apiKey);

        logger.info("📝 Created payment request event - Correlation: {}", correlationId);
        return builder.build();
    }

    private void setFieldIfExists(GenericRecordBuilder builder, Schema schema, String fieldName, Object value) {
        if (schema.getField(fieldName) != null) {
            builder.set(fieldName, value);
        }
    }

    private void validatePublishResponse(CountDownLatch finishLatch,
                                         int expectedResponseCount, List<PublishResponse> publishResponses,
                                         AtomicInteger failed, int expectedNumEventsPublished) throws Exception {
        if (!finishLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new RuntimeException("publishStream timed out after: " + TIMEOUT_SECONDS + "sec");
        }

        if (expectedResponseCount != publishResponses.size()) {
            throw new RuntimeException("PublishStream received: " + publishResponses.size() + " PublishResponses instead of expected " + expectedResponseCount);
        }

        if (failed.get() != 0) {
            throw new RuntimeException("Failed to publish " + failed + " out of " + expectedNumEventsPublished + " events");
        }

        logger.info("✅ Successfully published {} payment request events", expectedNumEventsPublished);
    }

    private StreamObserver<PublishResponse> getDefaultPublishStreamObserver(AtomicReference<CountDownLatch> finishLatchRef,
                                                                            int expectedResponseCount,
                                                                            List<PublishResponse> publishResponses,
                                                                            AtomicInteger failed) {
        return new StreamObserver<>() {
            @Override
            public void onNext(PublishResponse publishResponse) {
                publishResponses.add(publishResponse);

                for (PublishResult publishResult : publishResponse.getResultsList()) {
                    if (publishResult.hasError()) {
                        failed.incrementAndGet();
                        logger.error("❌ Publishing failed: {}", publishResult.getError().getMsg());
                    } else {
                        logger.info("✅ PING published - Correlation: {}", publishResult.getCorrelationKey());
                    }
                }

                if (publishResponses.size() == expectedResponseCount) {
                    finishLatchRef.get().countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("❌ Error during publish: {}", Status.fromThrowable(t));
                finishLatchRef.get().countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("✅ PING publish completed");
                finishLatchRef.get().countDown();
            }
        };
    }

    @Override
    public void close() {
        logger.info("🛑 Shutting down publisher...");
        responseSubscriberRunning = false;

        // Give some time for any pending operations
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (responseStream != null) {
            try {
                responseStream.onCompleted();
            } catch (Exception e) {
                logger.debug("Response stream already closed: {}", e.getMessage());
            }
        }
        super.close();
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            logger.error("❌ Usage: ./run.sh genericpubsub.KodyPaymentPublisher <environment> <method> '<json_payload>' <api_key>");
            logger.error("   Examples:");
            logger.error("     # Pure proxy mode - API key required");
            logger.error("     ./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{\"storeId\":\"123\",\"amount\":1000}' 'your-api-key'");
            return;
        }

        String environment = args[1];
        String method = args[2];
        String payload = args[3];
        String customApiKey = args[4];

        logger.info("🚀 Starting Kody Payment Publisher for environment: {} with method: {}", environment, method);
        logger.info("🔑 Using API key: {}***", customApiKey.substring(0, Math.min(8, customApiKey.length())));

        try {
            ApplicationConfig config = new ApplicationConfig("arguments-" + environment + ".yaml");

            KodyPaymentPublisher publisher = new KodyPaymentPublisher(config);
            logger.info("✅ Publisher initialized with response subscription");

            String correlationId = UUID.randomUUID().toString();
            logger.info("🚀 Sending request - Correlation: {}, Method: {}", correlationId, method);

            try {
                PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                        correlationId, method, payload, customApiKey, 60);

                // Check if response contains an error
                boolean hasError = response.getPayload() != null && 
                                 response.getPayload().toLowerCase().contains("error");
                
                if (hasError) {
                    logger.warn("⚠️ KODY API RETURNED ERROR!");
                } else {
                    logger.info("🎉 KODY PAYMENT SUCCESS!");
                }
                
                logger.info("📦 Response: {}", response.getPayload());

            } catch (Exception e) {
                logger.error("❌ KODY PAYMENT FAILED: {}", e.getMessage());
            }

            publisher.close();
        } catch (Exception e) {
            logger.error("❌ Error during Kody Payment test", e);
        }
    }
}