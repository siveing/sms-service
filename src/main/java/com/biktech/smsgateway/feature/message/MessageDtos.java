package com.biktech.smsgateway.feature.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** What apps and phones send and get back. */
public final class MessageDtos {

    private MessageDtos() {
    }

    @Schema(description = "An app asks for one SMS")
    public record SendRequest(
            @Schema(description = "Local or international format", example = "012345678")
            @NotBlank @Size(max = 20)
            String to,
            @Schema(description = "The text; Khmer is fine, long text goes out in several parts", example = "123456 is your code.")
            @NotBlank @Size(max = 1000)
            String text,
            @Schema(description = "Your own id for this SMS. Sending again with the same one returns the first and sends nothing new",
                    example = "otp-5f2b8c1e")
            @Size(max = 100)
            String reference,
            @Schema(description = "How long it may wait for a phone, in seconds: 300 when left out, never more than the "
                    + "gateway's limit (1 hour by default). Still unsent after that, it expires", example = "300")
            @Min(30) @Max(86_400)
            Integer validSeconds) {
    }

    @Schema(description = "Where one SMS stands")
    public record MessageView(
            UUID id,
            String reference,
            @Schema(description = "International format", example = "+85512345678")
            String to,
            @Schema(description = "PENDING waits for a phone, SENDING is held by one; SENT, FAILED and EXPIRED are final")
            SmsStatus status,
            @Schema(description = "SMS goes out from a phone; TELEGRAM is posted to the test group instead (set per app)")
            DeliveryChannel channel,
            int attempts,
            Instant sentAt,
            Instant expiresAt,
            @Schema(description = "Why the last try failed")
            String lastError,
            Instant createdAt) {
    }

    @Schema(description = "A phone asks for the next waiting messages")
    public record PullRequest(
            @Schema(description = "How many to take at once, 1 to 20; 5 when left out", example = "5")
            @Min(1) @Max(20)
            Integer limit) {
    }

    @Schema(description = "One message for the phone to text")
    public record PhoneMessage(
            @Schema(description = "Report the outcome with this id")
            UUID id,
            @Schema(description = "International format", example = "+85512345678")
            String to,
            String text) {
    }

    @Schema(description = "A phone reports what happened to one message")
    public record ReportRequest(
            @NotNull
            UUID id,
            @Schema(description = "true when the phone sent it; false to try again, up to the attempt limit")
            @NotNull
            Boolean sent,
            @Schema(description = "Why it failed, when sent is false")
            @Size(max = 500)
            String error) {
    }
}
