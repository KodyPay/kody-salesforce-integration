package utility;

import com.google.gson.*;
import com.kodypay.grpc.ecom.v1.PaymentInitiationRequest;

import java.lang.reflect.Type;

// 自定义反序列化器
public class PaymentInitiationRequestDeserializer implements JsonDeserializer<PaymentInitiationRequest> {
    @Override
    public PaymentInitiationRequest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        return PaymentInitiationRequest.newBuilder()
                .setStoreId(jsonObject.get("store_id__c").getAsString())
                .setPaymentReference(jsonObject.get("payment_reference__c").getAsString())
                .setOrderId(jsonObject.get("order_id__c").getAsString())
                .setAmountMinorUnits((jsonObject.get("amount_minor_units__c").getAsLong()))
                .setCurrency(jsonObject.get("currency__c").getAsString())
                .setReturnUrl(jsonObject.get("return_url__c").getAsString())
                .setPayerStatement(jsonObject.get("payer_statement__c").getAsString())
                .setPayerEmailAddress(jsonObject.get("payer_email_address__c").getAsString())
                .setPayerLocale(jsonObject.get("payer_locale__c").getAsString())
                .setPayerIpAddress(jsonObject.get("payer_ip_address__c").getAsString())
                .setExpiry(PaymentInitiationRequest.ExpirySettings.newBuilder()
                        .setShowTimer(jsonObject.get("show_timer__c").getAsBoolean())
                        .setExpiringSeconds(jsonObject.get("amount_minor_units__c").getAsLong())
                        .build())
                .build();
    }
}