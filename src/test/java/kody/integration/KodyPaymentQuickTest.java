package kody.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.ApplicationConfig;
import samples.KodyPaymentPublisher;

import java.util.UUID;

/**
 * Quick test for individual Kody Payment APIs
 * This allows testing each API individually without full integration test overhead
 * 
 * Usage: 
 *   ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.InitiatePayment
 *   ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.PaymentDetails <paymentId>
 *   ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.GetPayments
 *   ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.Refund <paymentId>
 */
public class KodyPaymentQuickTest {
    private static final Logger logger = LoggerFactory.getLogger(KodyPaymentQuickTest.class);

    public static void main(String[] args) {
        if (args.length < 2) {
            logger.error("❌ Usage: ./run.sh genericpubsub.KodyPaymentQuickTest <environment> <api> [paymentId]");
            logger.error("   APIs: request.ecom.v1.InitiatePayment, request.ecom.v1.InitiatePaymentStream, request.ecom.v1.PaymentDetails, request.ecom.v1.GetPayments, request.ecom.v1.Refund");
            logger.error("   Examples:");
            logger.error("     ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.InitiatePayment");
            logger.error("     ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.InitiatePaymentStream");
            logger.error("     ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.PaymentDetails test-payment-123");
            logger.error("     ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.GetPayments");
            logger.error("     ./run.sh genericpubsub.KodyPaymentQuickTest sandbox request.ecom.v1.Refund test-payment-123");
            return;
        }

        String environment = args[0];
        String api = args[1];
        String paymentId = args.length > 2 ? args[2] : null;

        logger.info("🧪 Quick Test - {} API on {} environment", api, environment);

        KodyPaymentService subscriber = null;
        KodyPaymentPublisher publisher = null;
        Thread subscriberThread = null;

        try {
            // Setup
            logger.info("🔧 Setting up test...");
            ApplicationConfig config = new ApplicationConfig("arguments-" + environment + ".yaml");

            // Start subscriber
            subscriber = new KodyPaymentService(config);
            final KodyPaymentService finalSubscriber = subscriber;
            subscriberThread = new Thread(() -> {
                try {
                    finalSubscriber.subscribeAndProcessPayments();
                } catch (Exception e) {
                    logger.error("❌ Subscriber error", e);
                }
            });
            subscriberThread.setDaemon(true);
            subscriberThread.start();

            Thread.sleep(3000); // Let subscriber initialize

            // Create publisher
            publisher = new KodyPaymentPublisher(config);
            logger.info("✅ Setup completed");

            // Run specific test
            boolean success = false;
            switch (api.toLowerCase()) {
                case "initiatepayment":
                case "request.ecom.v1.initiatepayment":
                    success = testInitiatePayment(publisher);
                    break;
                case "initiatepaymentstream":
                case "request.ecom.v1.initiatepaymentstream":
                    success = testInitiatePaymentStream(publisher);
                    break;
                case "paymentdetails":
                case "request.ecom.v1.paymentdetails":
                    if (paymentId == null) {
                        logger.info("📦 No payment ID provided, creating one first...");
                        paymentId = createPaymentAndGetId(publisher);
                    }
                    if (paymentId != null) {
                        success = testPaymentDetails(publisher, paymentId);
                    } else {
                        logger.error("❌ Cannot test PaymentDetails without a valid payment ID");
                        success = false;
                    }
                    break;
                case "getpayments":
                case "request.ecom.v1.getpayments":
                    success = testGetPayments(publisher);
                    break;
                case "refund":
                case "request.ecom.v1.refund":
                    if (paymentId == null) {
                        logger.info("📦 No payment ID provided, creating one first...");
                        paymentId = createPaymentAndGetId(publisher);
                    }
                    if (paymentId != null) {
                        success = testRefund(publisher, paymentId);
                    } else {
                        logger.error("❌ Cannot test Refund without a valid payment ID");
                        success = false;
                    }
                    break;
                default:
                    logger.error("❌ Unsupported API: {}. Use: request.ecom.v1.InitiatePayment, request.ecom.v1.InitiatePaymentStream, request.ecom.v1.PaymentDetails, request.ecom.v1.GetPayments, or request.ecom.v1.Refund", api);
                    return;
            }

            if (success) {
                logger.info("🎉 {} API TEST PASSED! ✅", api);
            } else {
                logger.error("❌ {} API TEST FAILED!", api);
            }

        } catch (Exception e) {
            logger.error("❌ Test execution failed", e);
        } finally {
            // Cleanup
            try {
                if (publisher != null) publisher.close();
                if (subscriber != null) subscriber.close();
                if (subscriberThread != null) subscriberThread.interrupt();
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.warn("⚠️ Cleanup error", e);
            }
        }
    }

    private static boolean testInitiatePayment(KodyPaymentPublisher publisher) {
        logger.info("💳 Testing InitiatePayment API...");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.InitiatePayment";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentReference\": \"pay_quick_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"amountMinorUnits\": 2500,\n" +
                    "  \"currency\": \"GBP\",\n" +
                    "  \"orderId\": \"order_quick_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"returnUrl\": \"https://example.com/return\",\n" +
                    "  \"payerEmailAddress\": \"quicktest@example.com\"\n" +
                    "}";

            logger.info("📤 Sending request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null) {
                logger.info("📦 Response received:");
                logger.info("   🆔 Correlation ID: {}", response.getCorrelationId());
                logger.info("   🔧 Method: {}", response.getMethod());
                logger.info("   📋 Payload: {}", response.getPayload());
                return true;
            } else {
                logger.error("❌ No response received");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ InitiatePayment test failed", e);
            return false;
        }
    }

    private static boolean testInitiatePaymentStream(KodyPaymentPublisher publisher) {
        logger.info("💳 Testing InitiatePaymentStream API...");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.InitiatePaymentStream";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentReference\": \"pay_stream_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"amountMinorUnits\": 2500,\n" +
                    "  \"currency\": \"GBP\",\n" +
                    "  \"orderId\": \"order_stream_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"returnUrl\": \"https://example.com/return\",\n" +
                    "  \"payerEmailAddress\": \"streamtest@example.com\"\n" +
                    "}";

            logger.info("📤 Sending stream request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null) {
                logger.info("📦 Stream response received:");
                logger.info("   🆔 Correlation ID: {}", response.getCorrelationId());
                logger.info("   🔧 Method: {}", response.getMethod());
                logger.info("   📋 Payload: {}", response.getPayload());
                return true;
            } else {
                logger.error("❌ No stream response received");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ InitiatePaymentStream test failed", e);
            return false;
        }
    }

    private static boolean testPaymentDetails(KodyPaymentPublisher publisher, String paymentId) {
        logger.info("🔍 Testing PaymentDetails API with paymentId: {}", paymentId);
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.PaymentDetails";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentId\": \"" + paymentId + "\"\n" +
                    "}";

            logger.info("📤 Sending request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null) {
                logger.info("📦 Response received:");
                logger.info("   🆔 Correlation ID: {}", response.getCorrelationId());
                logger.info("   🔧 Method: {}", response.getMethod());
                logger.info("   📋 Payload: {}", response.getPayload());
                return true;
            } else {
                logger.error("❌ No response received");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ PaymentDetails test failed", e);
            return false;
        }
    }

    private static boolean testGetPayments(KodyPaymentPublisher publisher) {
        logger.info("📋 Testing GetPayments API...");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.GetPayments";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"pageCursor\": {\n" +
                    "    \"page\": 1,\n" +
                    "    \"pageSize\": 5\n" +
                    "  }\n" +
                    "}";

            logger.info("📤 Sending request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null) {
                logger.info("📦 Response received:");
                logger.info("   🆔 Correlation ID: {}", response.getCorrelationId());
                logger.info("   🔧 Method: {}", response.getMethod());
                logger.info("   📋 Payload: {}", response.getPayload());
                return true;
            } else {
                logger.error("❌ No response received");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ GetPayments test failed", e);
            return false;
        }
    }

    private static boolean testRefund(KodyPaymentPublisher publisher, String paymentId) {
        logger.info("💸 Testing Refund API with paymentId: {}", paymentId);
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.Refund";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentId\": \"" + paymentId + "\",\n" +
                    "  \"amount\": \"5.00\"\n" +
                    "}";

            logger.info("📤 Sending request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null) {
                logger.info("📦 Response received:");
                logger.info("   🆔 Correlation ID: {}", response.getCorrelationId());
                logger.info("   🔧 Method: {}", response.getMethod());
                logger.info("   📋 Payload: {}", response.getPayload());
                return true;
            } else {
                logger.error("❌ No response received");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Refund test failed", e);
            return false;
        }
    }

    /**
     * Creates a payment and extracts the payment ID for use in other tests
     */
    private static String createPaymentAndGetId(KodyPaymentPublisher publisher) {
        logger.info("🔄 Creating payment to get payment ID...");
        try {
            String correlationId = UUID.randomUUID().toString();
            String method = "request.ecom.v1.InitiatePayment";
            ApplicationConfig config = new ApplicationConfig("arguments-sandbox.yaml");
            String payload = "{\n" +
                    "  \"storeId\": \"" + config.getKodyStoreId() + "\",\n" +
                    "  \"paymentReference\": \"pay_quick_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"amountMinorUnits\": 2500,\n" +
                    "  \"currency\": \"GBP\",\n" +
                    "  \"orderId\": \"order_quick_test_" + System.currentTimeMillis() + "\",\n" +
                    "  \"returnUrl\": \"https://example.com/return\",\n" +
                    "  \"payerEmailAddress\": \"quicktest@example.com\"\n" +
                    "}";

            logger.info("📤 Sending InitiatePayment request...");
            KodyPaymentPublisher.PaymentResponse response = publisher.sendPaymentRequestAndWaitForResponseWithCustomApiKey(
                    correlationId, method, payload, config.getKodyApiKey(), 45);

            if (response != null && response.getPayload() != null) {
                String paymentId = extractPaymentIdFromResponse(response.getPayload());
                if (paymentId != null) {
                    logger.info("✅ Payment created successfully! Payment ID: {}", paymentId);
                    return paymentId;
                } else {
                    logger.warn("⚠️ Payment created but could not extract payment ID from response");
                    logger.info("📦 Response: {}", response.getPayload());
                    return null;
                }
            } else {
                logger.error("❌ Failed to create payment - no response received");
                return null;
            }

        } catch (Exception e) {
            logger.error("❌ Error creating payment", e);
            return null;
        }
    }

    /**
     * Extracts payment ID from InitiatePayment response JSON
     * Simple extraction for paymentId field
     */
    private static String extractPaymentIdFromResponse(String responseJson) {
        try {
            logger.info("🔍 Extracting payment ID from response: {}", responseJson);

            // Simple pattern for paymentId field
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