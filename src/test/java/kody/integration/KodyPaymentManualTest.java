package kody.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.ApplicationConfig;
import samples.KodyPaymentPublisher;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manual test for Kody Payment APIs that can be run directly
 * This demonstrates the complete workflow without requiring JUnit infrastructure
 *
 * Usage: ./run.sh genericpubsub.KodyPaymentManualTest sandbox
 */
public class KodyPaymentManualTest {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentManualTest.class);

    private static String actualPaymentId = null; // Will be captured from InitiatePayment response
    private static KodyPaymentService subscriber;
    private static KodyPaymentPublisher publisher; 
    private static Thread subscriberThread;

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("❌ Usage: mvn exec:java -Dexec.mainClass=\"kody.integration.KodyPaymentManualTest\" -Dexec.classpathScope=\"test\" -Dexec.args=\"sandbox\"");
            logger.error("   Or: ./run-test.sh sandbox");
            return;
        }

        String environment = args[0];
        logger.info("🚀 Starting Kody Payment Manual Test for environment: {}", environment);

        try {
            // Setup
            setupTest(environment);

            // Run all tests in sequence - FAIL FAST on first error
            
            // Test 1: InitiatePayment - captures payment ID for later tests
            if (!testInitiatePayment()) {
                logger.error("❌ FAST FAIL: InitiatePayment test failed! Stopping execution.");
                System.exit(1);
            }
            logger.info("✅ Test 1 passed, continuing...\n");

            // Test 2: PaymentDetails - uses payment ID from Test 1
            if (actualPaymentId != null) {
                if (!testPaymentDetails()) {
                    logger.error("❌ FAST FAIL: PaymentDetails test failed! Stopping execution.");
                    System.exit(1);
                }
                logger.info("✅ Test 2 passed, continuing...\n");
            } else {
                logger.error("❌ FAST FAIL: No payment ID captured from InitiatePayment! Stopping execution.");
                System.exit(1);
            }

            // Test 3: GetPayments - independent test
            if (!testGetPayments()) {
                logger.error("❌ FAST FAIL: GetPayments test failed! Stopping execution.");
                System.exit(1);
            }
            logger.info("✅ Test 3 passed, continuing...\n");

            // Test 4: Refund - uses payment ID from Test 1
            if (actualPaymentId != null) {
                if (!testRefund()) {
                    logger.error("❌ FAST FAIL: Refund test failed! Stopping execution.");
                    System.exit(1);
                }
                logger.info("✅ Test 4 passed, continuing...\n");
            } else {
                logger.error("❌ FAST FAIL: No payment ID available for Refund test! Stopping execution.");
                System.exit(1);
            }
            
            // Test 5: Error Handling
            if (!testErrorHandling()) {
                logger.error("❌ FAST FAIL: Error handling test failed! Stopping execution.");
                System.exit(1);
            }
            logger.info("✅ Test 5 passed, continuing...\n");
            
            // Test 6: Concurrent Requests
            if (!testConcurrentRequests()) {
                logger.error("❌ FAST FAIL: Concurrent requests test failed! Stopping execution.");
                System.exit(1);
            }
            logger.info("✅ Test 6 passed!\n");

            // All tests passed if we reach here (fail-fast would have returned earlier)
            logger.info("🎉 ALL TESTS PASSED! ✅");
            logger.info("📊 Test Summary:");
            logger.info("  ✅ InitiatePayment API - Working");
            logger.info("  ✅ PaymentDetails API - Working");  
            logger.info("  ✅ GetPayments API - Working");
            logger.info("  ✅ Refund API - Working");
            logger.info("  ✅ Error Handling - Working");
            logger.info("  ✅ Concurrent Requests - Working");
            
            // Exit with success code
            System.exit(0);

        } catch (Exception e) {
            logger.error("❌ Test execution failed", e);
            System.exit(1);
        } finally {
            cleanup();
        }
    }

    private static void setupTest(String environment) throws Exception {
        logger.info("🔧 Setting up test environment...");

        // Load configuration
        ApplicationConfig config = new ApplicationConfig("arguments-" + environment + ".yaml");

        // Validate configuration
        if (config.getKodyHostname() == null || config.getKodyHostname().isEmpty()) {
            throw new IllegalArgumentException("KODY_HOSTNAME is required in configuration");
        }
        if (config.getKodyApiKey() == null || config.getKodyApiKey().isEmpty()) {
            throw new IllegalArgumentException("KODY_API_KEY is required in configuration");
        }

        logger.info("🔧 Configuration loaded:");
        logger.info("  📡 Kody Hostname: {}", config.getKodyHostname());
        logger.info("  🔑 API Key configured: Yes");

        // Start subscriber in background
        subscriber = new KodyPaymentService(config);
        subscriberThread = new Thread(() -> {
            try {
                subscriber.subscribeAndProcessPayments();
            } catch (Exception e) {
                logger.error("❌ Subscriber error", e);
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
        logger.info("🚀 Test setup completed!\n");
    }

    private static boolean testInitiatePayment() {
        logger.info("🧪 Test 1: InitiatePayment API");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.InitiatePayment";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentReference\": \"pay_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"amountMinorUnits\": 1000,\n" +
                    "  \"currency\": \"GBP\",\n" +
                    "  \"orderId\": \"order_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"returnUrl\": \"https://example.com/return\",\n" +
                    "  \"payerEmailAddress\": \"test@example.com\"\n" +
                    "}";

            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 30);

            if (response != null && 
                correlationId.equals(response.getCorrelationId()) &&
                "response.ecom.v1.InitiatePayment".equals(response.getMethod()) &&
                response.getPayload() != null) {

                // Extract payment ID from response for use in subsequent tests
                actualPaymentId = extractPaymentIdFromResponse(response.getPayload());

                logger.info("✅ Test 1 PASSED: InitiatePayment API working");
                logger.info("🆔 Captured Payment ID: {}", actualPaymentId);
                logger.info("📦 Response: {}\n", response.getPayload());
                return true;
            } else {
                logger.error("❌ Test 1 FAILED: Invalid response structure");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 1 FAILED: InitiatePayment API", e);
            return false;
        }
    }

    private static boolean testPaymentDetails() {
        logger.info("🧪 Test 2: PaymentDetails API");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.PaymentDetails";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentId\": \"" + actualPaymentId + "\"\n" +
                    "}";

            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 30);

            if (response != null && 
                correlationId.equals(response.getCorrelationId()) &&
                "response.ecom.v1.PaymentDetails".equals(response.getMethod()) &&
                response.getPayload() != null) {

                logger.info("✅ Test 2 PASSED: PaymentDetails API working");
                logger.info("📦 Response: {}\n", response.getPayload());
                return true;
            } else {
                logger.error("❌ Test 2 FAILED: Invalid response structure");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 2 FAILED: PaymentDetails API", e);
            return false;
        }
    }

    private static boolean testGetPayments() {
        logger.info("🧪 Test 3: GetPayments API");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.GetPayments";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"pageCursor\": {\n" +
                    "    \"page\": 1,\n" +
                    "    \"pageSize\": 10\n" +
                    "  }\n" +
                    "}";

            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 30);

            if (response != null && 
                correlationId.equals(response.getCorrelationId()) &&
                "response.ecom.v1.GetPayments".equals(response.getMethod()) &&
                response.getPayload() != null) {

                logger.info("✅ Test 3 PASSED: GetPayments API working");
                logger.info("📦 Response: {}\n", response.getPayload());
                return true;
            } else {
                logger.error("❌ Test 3 FAILED: Invalid response structure");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 3 FAILED: GetPayments API", e);
            return false;
        }
    }

    private static boolean testRefund() {
        logger.info("🧪 Test 4: Refund API");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.Refund";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentId\": \"" + actualPaymentId + "\",\n" +
                    "  \"amount\": \"10.00\"\n" +
                    "}";

            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 30);

            if (response != null && 
                correlationId.equals(response.getCorrelationId()) &&
                "response.ecom.v1.Refund".equals(response.getMethod()) &&
                response.getPayload() != null) {

                logger.info("✅ Test 4 PASSED: Refund API working");
                logger.info("📦 Response: {}\n", response.getPayload());
                return true;
            } else {
                logger.error("❌ Test 4 FAILED: Invalid response structure");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 4 FAILED: Refund API", e);
            return false;
        }
    }

    private static boolean testErrorHandling() {
        logger.info("🧪 Test 5: Error Handling");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.InvalidMethod";
            String payload = "{}";

            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 30);

            if (response != null && 
                correlationId.equals(response.getCorrelationId()) &&
                "error".equals(response.getMethod()) &&
                response.getPayload() != null &&
                response.getPayload().contains("Unsupported method")) {

                logger.info("✅ Test 5 PASSED: Error handling working");
                logger.info("📦 Error Response: {}\n", response.getPayload());
                return true;
            } else {
                logger.error("❌ Test 5 FAILED: Error handling not working properly");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 5 FAILED: Error handling test", e);
            return false;
        }
    }

    private static boolean testConcurrentRequests() {
        logger.info("🧪 Test 6: Concurrent Requests");
        try {
            CountDownLatch latch = new CountDownLatch(3);
            boolean[] results = new boolean[3];

            // Launch 3 concurrent requests
            for (int i = 0; i < 3; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        String correlationId = UUID.randomUUID().toString();
                        String method = "request.ecom.v1.GetPayments";
                        ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
                        String payload = "{\n" +
                                "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                                "  \"pageCursor\": {\n" +
                                "    \"page\": " + (index + 1) + ",\n" +
                                "    \"pageSize\": 5\n" +
                                "  }\n" +
                                "}";

                        KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                                correlationId, method, payload, config.getKodyApiKey(), 30);

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
            if (!completed) {
                logger.error("❌ Test 6 FAILED: Timeout waiting for concurrent requests");
                return false;
            }

            // Check all results
            boolean allSucceeded = true;
            for (int i = 0; i < results.length; i++) {
                if (!results[i]) {
                    logger.error("❌ Concurrent request {} failed", i + 1);
                    allSucceeded = false;
                }
            }

            if (allSucceeded) {
                logger.info("✅ Test 6 PASSED: Concurrent requests working");
                logger.info("📊 All 3 concurrent requests completed successfully\n");
                return true;
            } else {
                logger.error("❌ Test 6 FAILED: Some concurrent requests failed");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Test 6 FAILED: Concurrent requests test", e);
            return false;
        }
    }

    private static void cleanup() {
        logger.info("🧹 Cleaning up test resources...");

        try {
            if (publisher != null) {
                publisher.close();
                logger.info("✅ Publisher closed");
            }

            if (subscriber != null) {
                subscriber.close();
                logger.info("✅ Subscriber closed");
            }

            if (subscriberThread != null) {
                subscriberThread.interrupt();
                logger.info("✅ Subscriber thread stopped");
            }

            // Give some time for cleanup
            Thread.sleep(1000);

        } catch (Exception e) {
            logger.warn("⚠️ Error during cleanup", e);
        }

        logger.info("🏁 Test cleanup completed");
    }

    /**
     * Extracts payment ID from InitiatePayment response JSON
     * Simple extraction for paymentId field
     */
    private static String extractPaymentIdFromResponse(String responseJson) {
        try {
            logger.info("🔍 Extracting payment ID from response: {}", responseJson);

            String paymentIdPattern = "\"paymentId\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(paymentIdPattern);
            java.util.regex.Matcher matcher = pattern.matcher(responseJson);

            if (matcher.find()) {
                String paymentId = matcher.group(1);
                logger.info("✅ Found paymentId: {}", paymentId);
                return paymentId;
            }

            logger.warn("⚠️ Could not extract payment ID from response: {}", responseJson);
            return null;

        } catch (Exception e) {
            logger.error("❌ Error extracting payment ID from response", e);
            return null;
        }
    }
}