package com.aadil.webhookengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/webhook/emit
 *
 * Example:
 * {
 *   "eventType": "order.created",
 *   "payload":   "{\"orderId\": 42, \"amount\": 999}"
 * }
 *
 * payload is stored as a raw JSON string in the DB (TEXT column).
 * The client is responsible for sending valid JSON as the payload value.
 */
@Getter
@Setter
public class EmitRequest {

    @NotBlank(message = "eventType is required")
    @Size(max = 100, message = "eventType must be 100 characters or fewer")
    private String eventType;

    @NotBlank(message = "payload is required")
    private String payload;
}
