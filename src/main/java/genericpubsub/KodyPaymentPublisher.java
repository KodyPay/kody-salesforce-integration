package genericpubsub;

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


    private final Map<String, CountDownLatch> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, PaymentResponse> responses = new ConcurrentHashMap<>();
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

    public KodyPaymentPublisher(ApplicationConfig exampleConfigurations) {
        super(exampleConfigurations);

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
        CountDownLatch responseLatch = new CountDownLatch(1);
        pendingRequests.put(correlationId, responseLatch);

        try {
            logger.info("💳 Sending payment request and waiting for response...");
            logger.info("🆔 Correlation ID: {}", correlationId);
            logger.info("🔧 Method: {}", method);
            logger.info("📦 Request: {}", payload);

            publishPaymentRequest(correlationId, method, payload);

            logger.info("⏳ Waiting for response with correlation ID: {} (timeout: {}s)", correlationId, timeoutSeconds);
            if (responseLatch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                PaymentResponse response = responses.remove(correlationId);
                if (response != null) {
                    logger.info("✅ Received response for correlation ID: {}", correlationId);
                    logger.info("📦 Response: {}", response.getPayload());
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

    public void publishPaymentRequest(String correlationId, String method, String payload) throws Exception {
        logger.info("📡 Publishing payment request...");

        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicReference<CountDownLatch> finishLatchRef = new AtomicReference<>(finishLatch);
        final int numExpectedPublishResponses = 1;
        final List<PublishResponse> publishResponses = Lists.newArrayListWithExpectedSize(numExpectedPublishResponses);
        AtomicInteger failed = new AtomicInteger(0);

        StreamObserver<PublishResponse> pubObserver = getDefaultPublishStreamObserver(finishLatchRef,
                numExpectedPublishResponses, publishResponses, failed);

        ClientCallStreamObserver<PublishRequest> requestObserver = (ClientCallStreamObserver<PublishRequest>) asyncStub.publishStream(pubObserver);

        PublishRequest publishRequest = generatePaymentPublishRequest(correlationId, method, payload);
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

            logger.info("🎯 Event - Correlation: {}, Method: {}", correlationId, method);
            logger.info("📦 Payload: {}", payload);

            boolean isPendingRequest = pendingRequests.containsKey(correlationId);
            boolean isResponseMethod = method != null && method.startsWith("response.");

            logger.info("🔍 isPending: {}, isResponse: {}", isPendingRequest, isResponseMethod);

            if (isPendingRequest && isResponseMethod) {
                logger.info("🎉 FOUND MATCHING RESPONSE! Correlation ID: {}", correlationId);

                PaymentResponse response = new PaymentResponse(correlationId, method, payload);
                responses.put(correlationId, response);

                CountDownLatch latch = pendingRequests.get(correlationId);
                if (latch != null) {
                    latch.countDown();
                    logger.info("✅ Notified waiting thread for response: {}", correlationId);
                }
            } else {
                logger.info("ℹ️ Ignoring event - isPending: {}, isResponse: {}", isPendingRequest, isResponseMethod);
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
        setupTopicDetails("/event/KodyPayment__e", true, true);

        Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
        GenericRecord event = createPaymentRequestEvent(schema, correlationId, method, payload);

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

    public GenericRecord createPaymentRequestEvent(Schema schema, String correlationId, String method, String payload) {
        GenericRecordBuilder builder = new GenericRecordBuilder(schema);

        setFieldIfExists(builder, schema, "CreatedDate", System.currentTimeMillis());
        setFieldIfExists(builder, schema, "CreatedById", this.userId);
        setFieldIfExists(builder, schema, "correlation_id__c", correlationId);
        setFieldIfExists(builder, schema, "method__c", method);
        setFieldIfExists(builder, schema, "payload__c", payload);

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
        if (args.length < 4) {
            logger.error("❌ Usage: ./run.sh genericpubsub.KodyPaymentPublisher <environment> <method> '<json_payload>'");
            logger.error("   Examples:");
            logger.error("     ./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.InitiatePayment '{\"storeId\":\"123\",\"amount\":1000}'");
            logger.error("     ./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.PaymentDetails '{\"storeId\":\"123\",\"paymentId\":\"pay123\"}'");
            logger.error("     ./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.GetPayments '{\"storeId\":\"123\"}'");
            logger.error("     ./run.sh genericpubsub.KodyPaymentPublisher sandbox request.ecom.v1.Refund '{\"storeId\":\"123\",\"paymentId\":\"pay123\",\"amount\":\"10.00\"}'");
            return;
        }

        String environment = args[1];
        String method = args[2];
        String payload = args[3];

        logger.info("🚀 Starting Kody Payment Publisher for environment: {} with method: {}", environment, method);

        try {
            ApplicationConfig exampleConfigurations = new ApplicationConfig("arguments-" + environment + ".yaml");

            KodyPaymentPublisher publisher = new KodyPaymentPublisher(exampleConfigurations);
            logger.info("✅ Publisher initialized with response subscription");

            String correlationId = UUID.randomUUID().toString();

            logger.info("🆔 Correlation ID: {}", correlationId);
            logger.info("🔧 Request Method: {}", method);
            logger.info("📦 Request Payload: {}", payload);

            try {
                PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                        correlationId, method, payload, 60);

                logger.info("🎉 KODY PAYMENT SUCCESS!");
                logger.info("🆔 Response Correlation ID: {}", response.getCorrelationId());
                logger.info("🔧 Response Method: {}", response.getMethod());
                logger.info("📦 Response Payload: {}", response.getPayload());

            } catch (Exception e) {
                logger.error("❌ KODY PAYMENT FAILED: {}", e.getMessage());
            }

            publisher.close();
        } catch (Exception e) {
            logger.error("❌ Error during Kody Payment test", e);
        }
    }
}