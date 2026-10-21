package com.biktech.smsgateway.feature.caller;

import com.biktech.smsgateway.common.ErrorCode;
import com.biktech.smsgateway.common.GatewayException;
import com.biktech.smsgateway.feature.message.DeliveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Apps and phones, and the keys they sign in with. A key is shown once, when it is made. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CallerRepository repository;

    /** A key just made, to show once. */
    public record IssuedKey(String name, String key) {
    }

    @Transactional(readOnly = true)
    public List<Caller> list(CallerKind kind) {
        return repository.findAllByKindOrderByNameAsc(kind);
    }

    @Transactional
    public IssuedKey create(CallerKind kind, String name) {
        String trimmed = name.trim();
        if (repository.existsByKindAndNameIgnoreCase(kind, trimmed)) {
            throw new GatewayException(ErrorCode.NAME_TAKEN);
        }
        Caller caller = new Caller();
        caller.setKind(kind);
        caller.setName(trimmed);
        String key = assignKey(caller);
        repository.save(caller);
        log.info("[Caller] {} {} added", kind, trimmed);
        return new IssuedKey(trimmed, key);
    }

    /** The old key stops working at once. */
    @Transactional
    public IssuedKey newKey(UUID id) {
        Caller caller = get(id);
        String key = assignKey(caller);
        log.info("[Caller] {} {} given a new key", caller.getKind(), caller.getName());
        return new IssuedKey(caller.getName(), key);
    }

    @Transactional
    public Caller setEnabled(UUID id, boolean enabled) {
        Caller caller = get(id);
        caller.setEnabled(enabled);
        log.info("[Caller] {} {} {}", caller.getKind(), caller.getName(), enabled ? "enabled" : "disabled");
        return caller;
    }

    @Transactional
    public Caller setChannel(UUID id, DeliveryChannel channel) {
        Caller caller = get(id);
        caller.setChannel(channel);
        log.info("[Caller] {} {} now delivers by {}", caller.getKind(), caller.getName(), channel);
        return caller;
    }

    /** The enabled app or phone this key belongs to. */
    @Transactional(readOnly = true)
    public Optional<CallerPrincipal> authenticate(String key, CallerKind kind) {
        if (key == null || !key.startsWith(kind.keyPrefix())) {
            return Optional.empty();
        }
        return repository.findByKeyHash(hash(key))
            .filter(caller -> caller.isEnabled() && caller.getKind() == kind)
            .map(caller -> new CallerPrincipal(caller.getId(), caller.getName(), caller.getKind(), caller.getChannel()));
    }

    @Transactional
    public void touch(UUID id, Instant now) {
        repository.touch(id, now);
    }

    private Caller get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND));
    }

    private String assignKey(Caller caller) {
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String key = caller.getKind().keyPrefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        caller.setKeyHash(hash(key));
        caller.setKeyHint(key.substring(0, caller.getKind().keyPrefix().length() + 4) + "…");
        return key;
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }
}
