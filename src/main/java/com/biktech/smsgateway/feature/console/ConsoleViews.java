package com.biktech.smsgateway.feature.console;

import com.biktech.smsgateway.feature.message.DeliveryChannel;
import com.biktech.smsgateway.feature.message.SmsStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/** What the console pages show, already formatted. */
public final class ConsoleViews {

    private ConsoleViews() {
    }

    public record Tile(String label, long value, String note, String tone) {
    }

    /** state is online, offline, never or disabled. */
    public record PhoneRow(UUID id, String name, String keyHint, boolean enabled, String state, String stateLabel,
                           String lastSeen, long sent, long failed, String added) {
    }

    public record AppRow(UUID id, String name, String keyHint, boolean enabled, DeliveryChannel channel, String lastUsed,
                         long sent, String added) {
    }

    /** Whether the bot is set up, the end of the group's chat id, and what it posted in the last 24 hours. */
    public record TelegramStatus(boolean configured, String chat, long sent, long failed) {
    }

    public record MessageRow(UUID id, String created, String to, String source, String reference, SmsStatus status,
                             DeliveryChannel channel, int attempts, String phone, String sentAt, String expiresAt, String lastError, int textLength) {
    }

    public record Overview(List<Tile> tiles, List<PhoneRow> phones, List<MessageRow> recent, TelegramStatus telegram) {
    }

    public record MessagePage(List<MessageRow> rows, int page, int totalPages, long total) {
        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }
    }

    @Getter
    @Setter
    public static class SendForm {
        @NotBlank(message = "Enter a number")
        @Size(max = 20)
        private String to;

        @NotBlank(message = "Enter the text")
        @Size(max = 1000, message = "Keep it under 1000 characters")
        private String text;

        @Min(value = 30, message = "At least 30 seconds")
        @Max(value = 86_400, message = "At most a day")
        private Integer validSeconds = 300;

        private DeliveryChannel channel = DeliveryChannel.SMS;
    }

    @Getter
    @Setter
    public static class NameForm {
        @NotBlank(message = "Enter a name")
        @Size(max = 100, message = "Keep it under 100 characters")
        private String name;
    }
}
