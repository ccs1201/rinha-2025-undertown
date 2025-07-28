package br.com.ccs.rinha.api.model.input;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentRequest {
    public UUID correlationId;
    public BigDecimal amount;
    public Instant requestedAt;
    public boolean isDefault;

    private String json;

    public void setDefaultFalse() {
        this.isDefault = false;
    }

    public void setDefaultTrue() {
        this.isDefault = true;
    }

    public String getJson() {
        if (json == null) {
            toJson();
        }
        return json;
    }

    public static PaymentRequest parse(String json) {
        PaymentRequest req = new PaymentRequest();
        int startId = json.indexOf(":\"") + 2;
        int endId = json.indexOf('"', startId);
        req.correlationId = UUID.fromString(json.substring(startId, endId));
        int startAmount = json.indexOf(":", endId) + 1;
        int endAmount = json.indexOf('}', startAmount);
        req.amount = new BigDecimal(json.substring(startAmount, endAmount).trim());
        req.requestedAt = Instant.now();
        req.setDefaultFalse();

        return req;
    }

    private void toJson() {
        var sb = new StringBuilder(128);
        json = sb.append("{")
                .append("\"correlationId\":\"").append(correlationId).append("\",")
                .append("\"amount\":").append(amount).append(",")
                .append("\"requestedAt\":\"").append(requestedAt).append("\"")
                .append("}")
                .toString();
    }
}