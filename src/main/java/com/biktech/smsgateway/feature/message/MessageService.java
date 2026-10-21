package com.biktech.smsgateway.feature.message;

import com.biktech.smsgateway.common.ErrorCode;
import com.biktech.smsgateway.common.GatewayException;
import com.biktech.smsgateway.config.GatewayProperties;
import com.biktech.smsgateway.feature.caller.CallerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Queues messages, hands them to their sender (a phone, or the Telegram bot), and records the outcome. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_BATCH = 5;

    private final SmsMessageRepository repository;
    private final CallerRepository callers;
    private final GatewayProperties properties;

    /**
     * Queues one message. An app's repeated reference returns the first message and sends nothing new.
     *
     * @param clientId the app asking, or null from the console
     * @param source   who asked, as the console shows it
     * @param channel  real SMS from a phone, or a Telegram post
     */
    @Transactional
    public MessageDtos.MessageView send(UUID clientId, String source, DeliveryChannel channel, MessageDtos.SendRequest request) {
        String reference = clientId != null && StringUtils.hasText(request.reference()) ? request.reference().trim() : null;
        Instant now = Instant.now();
        if (clientId != null) {
            callers.touch(clientId, now);
        }
        if (reference != null) {
            Optional<SmsMessage> earlier = repository.findByClientIdAndReference(clientId, reference);
            if (earlier.isPresent()) {
                return view(earlier.get());
            }
        }
        Duration validity = request.validSeconds() == null
            ? properties.getDefaultValidity()
            : min(Duration.ofSeconds(request.validSeconds()), properties.getMaxValidity());

        SmsMessage message = new SmsMessage();
        message.setClientId(clientId);
        message.setSource(source);
        message.setReference(reference);
        message.setChannel(channel);
        message.setToNumber(PhoneNumbers.international(request.to()));
        message.setBody(request.text());
        message.setTextLength(request.text().length());
        message.setStatus(SmsStatus.PENDING);
        message.setExpiresAt(now.plus(validity));
        repository.saveAndFlush(message);
        log.info("[{}] {} queued {} for {}", channel, source, message.getId(), PhoneNumbers.masked(message.getToNumber()));
        return view(message);
    }

    @Transactional(readOnly = true)
    public MessageDtos.MessageView status(UUID clientId, UUID id) {
        return repository.findByIdAndClientId(id, clientId)
            .map(this::view)
            .orElseThrow(() -> new GatewayException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    /** A phone asks for work: it is seen, and takes the next waiting real SMS. */
    @Transactional
    public List<MessageDtos.PhoneMessage> pull(UUID phoneId, MessageDtos.PullRequest request) {
        callers.touch(phoneId, Instant.now());
        int limit = request == null || request.limit() == null ? DEFAULT_BATCH : request.limit();
        return claim(DeliveryChannel.SMS, phoneId, limit);
    }

    /** A phone reports one message it took. */
    @Transactional
    public void report(UUID phoneId, String phoneName, MessageDtos.ReportRequest request) {
        finish(request.id(), phoneId, phoneName, Boolean.TRUE.equals(request.sent()), request.error());
    }

    /**
     * Holds the next waiting messages of one channel for a sender, after closing what has run out of time.
     *
     * @param phoneId the phone taking them, or null for the Telegram bot
     */
    @Transactional
    public List<MessageDtos.PhoneMessage> claim(DeliveryChannel channel, UUID phoneId, int limit) {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(properties.getLease());
        closeStale(now, staleBefore);
        List<SmsMessage> taken = repository.lockClaimable(now, staleBefore, channel.name(), limit);
        for (SmsMessage message : taken) {
            message.setStatus(SmsStatus.SENDING);
            message.setPhoneId(phoneId);
            message.setClaimedAt(now);
            message.setAttempts(message.getAttempts() + 1);
        }
        return taken.stream()
            .map(message -> new MessageDtos.PhoneMessage(message.getId(), message.getToNumber(), message.getBody()))
            .toList();
    }

    /**
     * Records what the sender did with one message it holds. A late or foreign outcome changes nothing:
     * the message has moved on, or another sender holds it.
     *
     * @param phoneId the phone that held it, or null for the Telegram bot
     */
    @Transactional
    public void finish(UUID id, UUID phoneId, String senderName, boolean sent, String error) {
        SmsMessage message = repository.findById(id)
            .orElseThrow(() -> new GatewayException(ErrorCode.MESSAGE_NOT_FOUND));
        if (message.getStatus() != SmsStatus.SENDING || !Objects.equals(phoneId, message.getPhoneId())) {
            log.warn("[SMS] Ignored outcome for {} from {}: it is {}", message.getId(), senderName, message.getStatus());
            return;
        }
        if (sent) {
            message.setStatus(SmsStatus.SENT);
            message.setSentAt(Instant.now());
            message.setBody(null);
            message.setLastError(null);
            log.info("[{}] {} sent by {} to {}", message.getChannel(), message.getId(), senderName,
                PhoneNumbers.masked(message.getToNumber()));
            return;
        }
        message.setLastError(error);
        if (message.getAttempts() >= properties.getMaxAttempts()) {
            message.setStatus(SmsStatus.FAILED);
            message.setBody(null);
            log.warn("[{}] {} failed after {} attempts: {}", message.getChannel(), message.getId(), message.getAttempts(), error);
        } else {
            message.setStatus(SmsStatus.PENDING);
        }
    }

    /** Expires and gives up on stale messages even while no sender is asking, so the console stays true. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @Transactional
    public void housekeeping() {
        Instant now = Instant.now();
        closeStale(now, now.minus(properties.getLease()));
    }

    private void closeStale(Instant now, Instant staleBefore) {
        repository.closeExpired(now, List.of(SmsStatus.PENDING, SmsStatus.SENDING), SmsStatus.EXPIRED);
        repository.failAbandoned(staleBefore, properties.getMaxAttempts(), SmsStatus.SENDING, SmsStatus.FAILED);
    }

    private MessageDtos.MessageView view(SmsMessage message) {
        return new MessageDtos.MessageView(
            message.getId(),
            message.getReference(),
            message.getToNumber(),
            message.getStatus(),
            message.getChannel(),
            message.getAttempts(),
            message.getSentAt(),
            message.getExpiresAt(),
            message.getLastError(),
            message.getCreatedAt());
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
