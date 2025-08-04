package genericpubsub;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.ApplicationConfig;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration test for Kody Payment APIs
 * This test demonstrates the complete workflow:
 * 1. Start subscriber
 * 2. Test all payment APIs (InitiatePayment, PaymentDetails, GetPayments, Refund)
 * 3. Verify request-response flow
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KodyPaymentIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentIntegrationTest.class);

    private static KodyPaymentSubscriber subscriber;
    private static KodyPaymentPublisher publisher;
    private static Thread subscriberThread;
    private static ApplicationConfig config;

    // Store payment ID from InitiatePayment for later tests
    private static String testPaymentId = "test-payment-id-12345";

    @BeforeAll
    static void setUp() throws Exception {
        logger.info("🚀 Setting up Kody Payment Integration Test");

        // Load configuration
        config = new ApplicationConfig("arguments-sandbox.yaml");

        // Start subscriber in background thread
        subscriber = new KodyPaymentSubscriber(config);
        subscriberThread = new Thread(() -> {
            try {
                subscriber.subscribeAndProcessPayments();
            } catch (Exception e) {
                logger.error("Subscriber error", e);
            }
        });
        subscriberThread.setDaemon(true);
        subscriberThread.start();

        // Give subscriber time to initialize
        Thread.sleep(3000);
        logger.info("✅ Subscriber started and ready");

        // Initialize publisher
        publisher = new KodyPaymentPublisher(config);
        logger.info("✅ Publisher initialized");
    }

    @AfterAll
    static void tearDown() throws Exception {
        logger.info("🛑 Cleaning up test resources");

        if (publisher != null) {
            publisher.close();
        }

        if (subscriber != null) {
            subscriber.close();
        }

        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }

        logger.info("✅ Test cleanup completed");
    }

    @Test
    @Order(1)
    @DisplayName("Test InitiatePayment API")
    void testInitiatePayment() throws Exception {
        logger.info("🧪 Testing InitiatePayment API");

        String correlationId = UUID.randomUUID().toString();
        String method = "request.ecom.v1.InitiatePayment";
        String payload = "{\n" +
                "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                "  \"paymentReference\": \"pay_test_" + System.currentTimeMillis() + "\",\n" +
                "  \"amountMinorUnits\": 1000,\n" +
                "  \"currency\": \"GBP\",\n" +
                "  \"orderId\": \"order_test_" + System.currentTimeMillis() + "\",\n" +
                "  \"returnUrl\": \"https://example.com/return\",\n" +
                "  \"payerEmailAddress\": \"test@example.com\"\n" +
                "}";

        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                correlationId, method, payload, 30);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertEquals(correlationId, response.getCorrelationId(), "Correlation ID should match");
        Assertions.assertEquals("response.ecom.v1.InitiatePayment", response.getMethod(), "Response method should match");
        Assertions.assertNotNull(response.getPayload(), "Response payload should not be null");

        logger.info("✅ InitiatePayment test completed successfully");
        logger.info("📦 Response: {}", response.getPayload());
    }

    @Test
    @Order(2)
    @DisplayName("Test PaymentDetails API")
    void testPaymentDetails() throws Exception {
        logger.info("🧪 Testing PaymentDetails API");

        String correlationId = UUID.randomUUID().toString();
        String method = "request.ecom.v1.PaymentDetails";
        String payload = "{\n" +
                "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                "  \"paymentId\": \"" + testPaymentId + "\"\n" +
                "}";

        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                correlationId, method, payload, 30);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertEquals(correlationId, response.getCorrelationId(), "Correlation ID should match");
        Assertions.assertEquals("response.ecom.v1.PaymentDetails", response.getMethod(), "Response method should match");
        Assertions.assertNotNull(response.getPayload(), "Response payload should not be null");

        logger.info("✅ PaymentDetails test completed successfully");
        logger.info("📦 Response: {}", response.getPayload());
    }

    @Test
    @Order(3)
    @DisplayName("Test GetPayments API")
    void testGetPayments() throws Exception {
        logger.info("🧪 Testing GetPayments API");

        String correlationId = UUID.randomUUID().toString();
        String method = "request.ecom.v1.GetPayments";
        String payload = "{\n" +
                "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                "  \"pageCursor\": {\n" +
                "    \"page\": 1,\n" +
                "    \"pageSize\": 10\n" +
                "  }\n" +
                "}";

        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                correlationId, method, payload, 30);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertEquals(correlationId, response.getCorrelationId(), "Correlation ID should match");
        Assertions.assertEquals("response.ecom.v1.GetPayments", response.getMethod(), "Response method should match");
        Assertions.assertNotNull(response.getPayload(), "Response payload should not be null");

        logger.info("✅ GetPayments test completed successfully");
        logger.info("📦 Response: {}", response.getPayload());
    }

    @Test
    @Order(4)
    @DisplayName("Test Refund API")
    void testRefund() throws Exception {
        logger.info("🧪 Testing Refund API");

        String correlationId = UUID.randomUUID().toString();
        String method = "request.ecom.v1.Refund";
        String payload = "{\n" +
                "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                "  \"paymentId\": \"" + testPaymentId + "\",\n" +
                "  \"amount\": \"10.00\"\n" +
                "}";

        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                correlationId, method, payload, 30);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertEquals(correlationId, response.getCorrelationId(), "Correlation ID should match");
        Assertions.assertEquals("response.ecom.v1.Refund", response.getMethod(), "Response method should match");
        Assertions.assertNotNull(response.getPayload(), "Response payload should not be null");

        logger.info("✅ Refund test completed successfully");
        logger.info("📦 Response: {}", response.getPayload());
    }

    @Test
    @Order(5)
    @DisplayName("Test Error Handling - Invalid Method")
    void testErrorHandling() throws Exception {
        logger.info("🧪 Testing Error Handling with invalid method");

        String correlationId = UUID.randomUUID().toString();
        String method = "request.ecom.v1.InvalidMethod";
        String payload = "{}";

        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                correlationId, method, payload, 30);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertEquals(correlationId, response.getCorrelationId(), "Correlation ID should match");
        Assertions.assertEquals("error", response.getMethod(), "Response method should be error");
        Assertions.assertTrue(response.getPayload().contains("Unsupported method"), 
                "Error payload should contain unsupported method message");

        logger.info("✅ Error handling test completed successfully");
        logger.info("📦 Error Response: {}", response.getPayload());
    }

    @Test
    @Order(6)
    @DisplayName("Test Multiple Concurrent Requests")
    void testConcurrentRequests() throws Exception {
        logger.info("🧪 Testing multiple concurrent requests");

        CountDownLatch latch = new CountDownLatch(3);
        boolean[] results = new boolean[3];

        // Launch 3 concurrent requests
        for (int i = 0; i < 3; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    String correlationId = UUID.randomUUID().toString();
                    String method = "request.ecom.v1.GetPayments";
                    String payload = "{\n" +
                            "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                            "  \"pageCursor\": {\n" +
                            "    \"page\": " + (index + 1) + ",\n" +
                            "    \"pageSize\": 5\n" +
                            "  }\n" +
                            "}";

                    KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponse(
                            correlationId, method, payload, 30);

                    results[index] = response != null && response.getCorrelationId().equals(correlationId);
                    logger.info("✅ Concurrent request {} completed: {}", index + 1, results[index]);

                } catch (Exception e) {
                    logger.error("❌ Concurrent request {} failed", index + 1, e);
                    results[index] = false;
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all requests to complete
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        Assertions.assertTrue(completed, "All concurrent requests should complete within timeout");

        // Verify all requests succeeded
        for (int i = 0; i < results.length; i++) {
            Assertions.assertTrue(results[i], "Concurrent request " + (i + 1) + " should succeed");
        }

        logger.info("✅ Concurrent requests test completed successfully");
    }

    @Test
    @Order(7)
    @DisplayName("Test Configuration Validation")
    void testConfigurationValidation() {
        logger.info("🧪 Testing configuration validation");

        // Verify required configuration is present
        Assertions.assertNotNull(config.getKodyHostname(), "Kody hostname should be configured");
        Assertions.assertNotNull(config.getKodyApiKey(), "Kody API key should be configured");
        Assertions.assertFalse(config.getKodyHostname().isEmpty(), "Kody hostname should not be empty");
        Assertions.assertFalse(config.getKodyApiKey().isEmpty(), "Kody API key should not be empty");

        logger.info("✅ Configuration validation test completed successfully");
        logger.info("🔧 Kody Hostname: {}", config.getKodyHostname());
        logger.info("🔑 API Key configured: {}", config.getKodyApiKey().length() > 0 ? "Yes" : "No");
    }
}