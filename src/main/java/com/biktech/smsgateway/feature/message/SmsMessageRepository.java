package com.biktech.smsgateway.feature.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, UUID>, JpaSpecificationExecutor<SmsMessage> {

    Optional<SmsMessage> findByIdAndClientId(UUID id, UUID clientId);

    Optional<SmsMessage> findByClientIdAndReference(UUID clientId, String reference);

    long countByStatus(SmsStatus status);

    List<SmsMessage> findTop10ByOrderByCreatedAtDesc();

    /** Rows of [status, count] for messages created since the given time. */
    @Query("SELECT m.status, COUNT(m) FROM SmsMessage m WHERE m.createdAt >= :since GROUP BY m.status")
    List<Object[]> countByStatusSince(Instant since);

    /** Rows of [phoneId, status, count] for messages a phone took since the given time. */
    @Query("""
        SELECT m.phoneId, m.status, COUNT(m) FROM SmsMessage m
        WHERE m.createdAt >= :since AND m.phoneId IS NOT NULL
        GROUP BY m.phoneId, m.status
        """)
    List<Object[]> countByPhoneSince(Instant since);

    /** Rows of [clientId, count] for messages each app asked for since the given time. */
    @Query("""
        SELECT m.clientId, COUNT(m) FROM SmsMessage m
        WHERE m.createdAt >= :since AND m.clientId IS NOT NULL
        GROUP BY m.clientId
        """)
    List<Object[]> countByClientSince(Instant since);

    /** Moves rows in one of the given statuses that are past their time to a final status, wiping the text. */
    @Modifying
    @Query("""
        UPDATE SmsMessage m SET m.status = :finalStatus, m.body = null
        WHERE m.status IN :open AND m.expiresAt <= :now
        """)
    int closeExpired(Instant now, Collection<SmsStatus> open, SmsStatus finalStatus);

    /** Gives up on rows a sender took and never reported, once they have had every attempt; wipes the text. */
    @Modifying
    @Query("""
        UPDATE SmsMessage m SET m.status = :failed, m.body = null, m.lastError = 'No outcome reported by the sender'
        WHERE m.status = :sending AND m.claimedAt < :staleBefore AND m.attempts >= :maxAttempts
        """)
    int failAbandoned(Instant staleBefore, int maxAttempts, SmsStatus sending, SmsStatus failed);

    /** Rows of [status, count] for one channel's messages created since the given time. */
    @Query("""
        SELECT m.status, COUNT(m) FROM SmsMessage m
        WHERE m.createdAt >= :since AND m.channel = :channel
        GROUP BY m.status
        """)
    List<Object[]> countByStatusSinceOnChannel(Instant since, DeliveryChannel channel);

    /** Next messages of one channel a sender may take, oldest first; rows another sender holds are skipped. */
    @Query(value = """
        SELECT * FROM sms_messages
        WHERE expires_at > :now
          AND channel = :channel
          AND (status = 'PENDING' OR (status = 'SENDING' AND claimed_at < :staleBefore))
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<SmsMessage> lockClaimable(Instant now, Instant staleBefore, String channel, int limit);
}
