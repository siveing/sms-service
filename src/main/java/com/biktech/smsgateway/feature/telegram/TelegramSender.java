package com.biktech.smsgateway.feature.telegram;

import com.biktech.smsgateway.config.GatewayProperties;
import com.biktech.smsgateway.feature.message.DeliveryChannel;
import com.biktech.smsgateway.feature.message.MessageDtos;
import com.biktech.smsgateway.feature.message.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Posts Telegram-channel messages to the test group through the bot, instead of texting them. Free, for testing. */
@Slf4j
@Component
public class TelegramSender {

    private static final int BATCH = 10;
    private static final String SENDER = "Telegram";
    private static final Pattern DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");

    private final MessageService messages;
    private final GatewayProperties properties;
    private final RestClient client;

    /** The body is buffered so it goes out with a Content-Length, not chunked. */
    public TelegramSender(MessageService messages, GatewayProperties properties) {
        this.messages = messages;
        this.properties = properties;
        JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        http.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
            .baseUrl(properties.getTelegram().getApiUrl())
            .requestFactory(new BufferingClientHttpRequestFactory(http))
            .build();
    }

    public boolean configured() {
        GatewayProperties.Telegram telegram = properties.getTelegram();
        return StringUtils.hasText(telegram.getBotToken()) && StringUtils.hasText(telegram.getChatId());
    }

    @Scheduled(fixedDelay = 2_000, initialDelay = 5_000)
    public void deliver() {
        if (!configured()) {
            return;
        }
        for (MessageDtos.PhoneMessage message : messages.claim(DeliveryChannel.TELEGRAM, null, BATCH)) {
            try {
                post(message);
                messages.finish(message.id(), null, SENDER, true, null);
            } catch (Exception ex) {
                messages.finish(message.id(), null, SENDER, false, describe(ex));
            }
        }
    }

    private void post(MessageDtos.PhoneMessage message) {
        GatewayProperties.Telegram telegram = properties.getTelegram();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", telegram.getChatId());
        if (telegram.getTopicId() != null) {
            body.put("message_thread_id", telegram.getTopicId());
        }
        body.put("text", "📩 To " + message.to() + "\n\n" + message.text());
        body.put("disable_web_page_preview", true);
        body.put("protect_content", true);
        client.post()
            .uri("/bot{token}/sendMessage", telegram.getBotToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    /** Telegram's own reason when it answered; never the request URL, which holds the bot token. */
    private static String describe(Exception ex) {
        if (ex instanceof RestClientResponseException response) {
            Matcher reason = DESCRIPTION.matcher(response.getResponseBodyAsString());
            String text = "Telegram answered " + response.getStatusCode().value()
                + (reason.find() ? ": " + reason.group(1) : "");
            return text.length() > 500 ? text.substring(0, 500) : text;
        }
        return "Telegram could not be reached (" + ex.getClass().getSimpleName() + ")";
    }
}
