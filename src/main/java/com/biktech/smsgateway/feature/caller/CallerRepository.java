package com.biktech.smsgateway.feature.caller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallerRepository extends JpaRepository<Caller, UUID> {

    List<Caller> findAllByKindOrderByNameAsc(CallerKind kind);

    Optional<Caller> findByKeyHash(String keyHash);

    boolean existsByKindAndNameIgnoreCase(CallerKind kind, String name);

    @Modifying
    @Query("UPDATE Caller c SET c.lastSeenAt = :now WHERE c.id = :id")
    void touch(UUID id, Instant now);
}
