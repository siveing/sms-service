package com.biktech.smsgateway.feature.caller;

import com.biktech.smsgateway.feature.message.DeliveryChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** An app or phone allowed in, by its own key. Only the key's hash is kept. */
@Entity
@Table(name = "callers", uniqueConstraints = {
    @UniqueConstraint(name = "uk_caller_kind_name", columnNames = {"kind", "name"}),
    @UniqueConstraint(name = "uk_caller_key_hash", columnNames = "key_hash")})
@Getter
@Setter
@NoArgsConstructor
public class Caller {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private CallerKind kind;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    /** The start of the key, so people can tell keys apart. */
    @Column(name = "key_hint", nullable = false, length = 20)
    private String keyHint;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Where an app's messages go; phones ignore it. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SMS'")
    @Column(name = "channel", nullable = false, length = 10)
    private DeliveryChannel channel = DeliveryChannel.SMS;

    /** The last time an app sent, or a phone asked for work. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
