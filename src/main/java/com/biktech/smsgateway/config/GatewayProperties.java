package com.biktech.smsgateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

/** The console account, and how long messages wait and are held. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Admin admin = new Admin();

    /** The outside address of the gateway, for setup commands; empty uses the address the console was opened with. */
    private String publicUrl;

    private Duration defaultValidity = Duration.ofMinutes(5);

    private Duration maxValidity = Duration.ofHours(1);

    private Duration lease = Duration.ofMinutes(2);

    private int maxAttempts = 3;

    private Duration onlineWithin = Duration.ofSeconds(30);

    private ZoneId timeZone = ZoneId.of("Asia/Phnom_Penh");

    private Telegram telegram = new Telegram();

    /** The bot that posts Telegram-channel messages to a group. */
    @Getter
    @Setter
    public static class Telegram {
        private String botToken;
        private String chatId;
        /** A topic inside the group (message_thread_id); none posts to the group's General topic. */
        private Long topicId;
        private String apiUrl = "https://api.telegram.org";
    }

    @Getter
    @Setter
    public static class Admin {
        private String username = "admin";
        private String password;
    }
}
