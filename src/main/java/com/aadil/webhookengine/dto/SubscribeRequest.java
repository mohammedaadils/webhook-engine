package com.aadil.webhookengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

/**
 * Request body for POST /api/webhook/subscribe
 *
 * Example:
 * {
 *   "eventType":   "order.created",
 *   "callbackUrl": "https://webhook.site/abc123",
 *   "secretKey":   "my-secret"          // optional
 * }
 */
@Getter
@Setter
public class SubscribeRequest {

    @NotBlank(message = "eventType is required")
    @Size(max = 100, message = "eventType must be 100 characters or fewer")
    private String eventType;

    @NotBlank(message = "callbackUrl is required")
    @URL(message = "callbackUrl must be a valid URL")
    @Size(max = 500, message = "callbackUrl must be 500 characters or fewer")
    private String callbackUrl;

    // Optional — no @NotBlank
    @Size(max = 255, message = "secretKey must be 255 characters or fewer")
    private String secretKey;
}
