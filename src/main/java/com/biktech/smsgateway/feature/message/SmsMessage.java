package com.biktech.smsgateway.feature.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One SMS an app or the console asked for, and what the phones did with it. */
@Entity
@Table(name = "sms_messages",
    uniqueConstraints = @UniqueConstraint(name = "uk_sms_client_reference", columnNames = {"client_id", "reference"}),
    indexes = @Index(name = "idx_sms_status_created", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class SmsMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The app that asked for it; null when sent from the console. */
    @Column(name = "client_id")
    private UUID clientId;

    /** Who asked, as shown in the console: the app's name, or the console user. */
    @Column(name = "source", nullable = false, length = 120)
    private String source;

    /** The app's own id for it; asking again with the same one sends nothing new. */
    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "to_number", nullable = false, length = 20)
    private String toNumber;

    /** Cleared once the message is final, so no OTP stays in the table. */
    @Column(name = "body", length = 1000)
    private String body;

    @Column(name = "text_length", nullable = false)
    private int textLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SmsStatus status;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SMS'")
    @Column(name = "channel", nullable = false, length = 10)
    private DeliveryChannel channel = DeliveryChannel.SMS;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** The phone holding it, or that last held it; null for Telegram. */
    @Column(name = "phone_id")
    private UUID phoneId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
