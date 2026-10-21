package com.biktech.smsgateway.feature.console;

import com.biktech.smsgateway.config.GatewayProperties;
import com.biktech.smsgateway.feature.caller.Caller;
import com.biktech.smsgateway.feature.caller.CallerKind;
import com.biktech.smsgateway.feature.caller.CallerRepository;
import com.biktech.smsgateway.feature.message.DeliveryChannel;
import com.biktech.smsgateway.feature.message.PhoneNumbers;
import com.biktech.smsgateway.feature.message.SmsMessage;
import com.biktech.smsgateway.feature.message.SmsMessageRepository;
import com.biktech.smsgateway.feature.message.SmsStatus;
import com.biktech.smsgateway.feature.telegram.TelegramSender;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads what the console shows. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleService {

    private static final int PAGE_SIZE = 50;
    private static final Duration DAY = Duration.ofHours(24);

    private final SmsMessageRepository messages;
    private final CallerRepository callers;
    private final GatewayProperties properties;
    private final TelegramSender telegram;

    public ConsoleViews.Overview overview() {
        Instant since = Instant.now().minus(DAY);
        Map<SmsStatus, Long> today = new EnumMap<>(SmsStatus.class);
        for (Object[] row : messages.countByStatusSince(since)) {
            today.put((SmsStatus) row[0], (Long) row[1]);
        }
        List<ConsoleViews.Tile> tiles = List.of(
            new ConsoleViews.Tile("Waiting", messages.countByStatus(SmsStatus.PENDING), "for a phone now", "pending"),
            new ConsoleViews.Tile("Sending", messages.countByStatus(SmsStatus.SENDING), "held by a phone now", "sending"),
            new ConsoleViews.Tile("Sent", today.getOrDefault(SmsStatus.SENT, 0L), "last 24 hours", "sent"),
            new ConsoleViews.Tile("Failed", today.getOrDefault(SmsStatus.FAILED, 0L), "last 24 hours", "failed"),
            new ConsoleViews.Tile("Expired", today.getOrDefault(SmsStatus.EXPIRED, 0L), "last 24 hours", "expired"));
        List<ConsoleViews.MessageRow> recent = rows(messages.findTop10ByOrderByCreatedAtDesc());
        return new ConsoleViews.Overview(tiles, phones(), recent, telegram());
    }

    public List<ConsoleViews.PhoneRow> phones() {
        Instant now = Instant.now();
        Map<UUID, Map<SmsStatus, Long>> counts = countsByPhone(now.minus(DAY));
        return callers.findAllByKindOrderByNameAsc(CallerKind.PHONE).stream()
            .map(phone -> {
                Map<SmsStatus, Long> mine = counts.getOrDefault(phone.getId(), Map.of());
                String state = state(phone, now);
                return new ConsoleViews.PhoneRow(
                    phone.getId(), phone.getName(), phone.getKeyHint(), phone.isEnabled(), state, stateLabel(state),
                    ago(phone.getLastSeenAt(), now), mine.getOrDefault(SmsStatus.SENT, 0L),
                    mine.getOrDefault(SmsStatus.FAILED, 0L), date(phone.getCreatedAt()));
            })
            .toList();
    }

    public List<ConsoleViews.AppRow> apps() {
        Instant now = Instant.now();
        Map<UUID, Long> asked = new HashMap<>();
        for (Object[] row : messages.countByClientSince(now.minus(DAY))) {
            asked.put((UUID) row[0], (Long) row[1]);
        }
        return callers.findAllByKindOrderByNameAsc(CallerKind.CLIENT).stream()
            .map(app -> new ConsoleViews.AppRow(
                app.getId(), app.getName(), app.getKeyHint(), app.isEnabled(), app.getChannel(), ago(app.getLastSeenAt(), now),
                asked.getOrDefault(app.getId(), 0L), date(app.getCreatedAt())))
            .toList();
    }

    public ConsoleViews.MessagePage messages(SmsStatus status, String number, int page) {
        Specification<SmsMessage> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(number) && !PhoneNumbers.searchDigits(number).isEmpty()) {
                where.add(cb.like(root.get("toNumber"), "%" + PhoneNumbers.searchDigits(number) + "%"));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<SmsMessage> found = messages.findAll(
            filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ConsoleViews.MessagePage(
            rows(found.getContent()), found.getNumber(), found.getTotalPages(), found.getTotalElements());
    }

    public ConsoleViews.TelegramStatus telegram() {
        Map<SmsStatus, Long> today = new EnumMap<>(SmsStatus.class);
        for (Object[] row : messages.countByStatusSinceOnChannel(Instant.now().minus(DAY), DeliveryChannel.TELEGRAM)) {
            today.put((SmsStatus) row[0], (Long) row[1]);
        }
        String chatId = properties.getTelegram().getChatId();
        String chat = StringUtils.hasText(chatId) && chatId.length() > 4 ? "…" + chatId.substring(chatId.length() - 4) : chatId;
        Long topicId = properties.getTelegram().getTopicId();
        if (topicId != null) {
            chat = chat + ", topic " + topicId;
        }
        return new ConsoleViews.TelegramStatus(telegram.configured(), chat,
            today.getOrDefault(SmsStatus.SENT, 0L), today.getOrDefault(SmsStatus.FAILED, 0L));
    }

    public boolean signInOn() {
        return StringUtils.hasText(properties.getAdmin().getPassword());
    }

    private List<ConsoleViews.MessageRow> rows(List<SmsMessage> found) {
        Map<UUID, String> phoneNames = callers.findAllByKindOrderByNameAsc(CallerKind.PHONE).stream()
            .collect(Collectors.toMap(Caller::getId, Caller::getName, (a, b) -> a));
        Function<SmsMessage, ConsoleViews.MessageRow> row = message -> new ConsoleViews.MessageRow(
            message.getId(),
            time(message.getCreatedAt()),
            PhoneNumbers.masked(message.getToNumber()),
            message.getSource(),
            message.getReference(),
            message.getStatus(),
            message.getChannel(),
            message.getAttempts(),
            sender(message, phoneNames),
            time(message.getSentAt()),
            time(message.getExpiresAt()),
            message.getLastError(),
            message.getTextLength());
        return found.stream().map(row).toList();
    }

    private static String sender(SmsMessage message, Map<UUID, String> phoneNames) {
        if (message.getChannel() == DeliveryChannel.TELEGRAM) {
            return "Telegram";
        }
        return message.getPhoneId() == null ? null : phoneNames.getOrDefault(message.getPhoneId(), "Removed phone");
    }

    private Map<UUID, Map<SmsStatus, Long>> countsByPhone(Instant since) {
        Map<UUID, Map<SmsStatus, Long>> counts = new HashMap<>();
        for (Object[] row : messages.countByPhoneSince(since)) {
            counts.computeIfAbsent((UUID) row[0], id -> new EnumMap<>(SmsStatus.class)).put((SmsStatus) row[1], (Long) row[2]);
        }
        return counts;
    }

    private String state(Caller phone, Instant now) {
        if (!phone.isEnabled()) {
            return "disabled";
        }
        if (phone.getLastSeenAt() == null) {
            return "never";
        }
        return phone.getLastSeenAt().isAfter(now.minus(properties.getOnlineWithin())) ? "online" : "offline";
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "online" -> "Online";
            case "offline" -> "Offline";
            case "never" -> "Never connected";
            default -> "Disabled";
        };
    }

    private String ago(Instant then, Instant now) {
        if (then == null) {
            return null;
        }
        long seconds = Math.max(0, Duration.between(then, now).toSeconds());
        if (seconds < 60) {
            return seconds + " s ago";
        }
        if (seconds < 3_600) {
            return seconds / 60 + " min ago";
        }
        if (seconds < 86_400) {
            return seconds / 3_600 + " h ago";
        }
        return time(then);
    }

    private String time(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ofPattern("d MMM, HH:mm:ss").withZone(properties.getTimeZone()).format(instant);
    }

    private String date(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ofPattern("d MMM yyyy").withZone(properties.getTimeZone()).format(instant);
    }
}
