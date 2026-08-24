package com.example.workflowtracker.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEvent e
            where (e.status = :pendingStatus and e.nextAttemptAt <= :now)
               or (e.status = :claimedStatus and e.claimedAt <= :leaseExpiry)
            order by e.createdAt asc, e.id asc
            """)
    List<OutboxEvent> findClaimableForUpdate(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("claimedStatus") OutboxStatus claimedStatus,
            @Param("now") Instant now,
            @Param("leaseExpiry") Instant leaseExpiry,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent e
            set e.status = :publishedStatus,
                e.publishedAt = :publishedAt,
                e.claimedAt = null,
                e.claimToken = null,
                e.lastError = null
            where e.id = :id
              and e.status = :claimedStatus
              and e.claimToken = :claimToken
            """)
    int markPublished(@Param("id") UUID id,
                      @Param("claimToken") UUID claimToken,
                      @Param("claimedStatus") OutboxStatus claimedStatus,
                      @Param("publishedStatus") OutboxStatus publishedStatus,
                      @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent e
            set e.status = :pendingStatus,
                e.nextAttemptAt = :nextAttemptAt,
                e.lastError = :lastError,
                e.claimedAt = null,
                e.claimToken = null
            where e.id = :id
              and e.status = :claimedStatus
              and e.claimToken = :claimToken
            """)
    int scheduleRetry(@Param("id") UUID id,
                      @Param("claimToken") UUID claimToken,
                      @Param("claimedStatus") OutboxStatus claimedStatus,
                      @Param("pendingStatus") OutboxStatus pendingStatus,
                      @Param("nextAttemptAt") Instant nextAttemptAt,
                      @Param("lastError") String lastError);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent e
            set e.status = :deadLetterStatus,
                e.lastError = :lastError,
                e.claimedAt = null,
                e.claimToken = null
            where e.id = :id
              and e.status = :claimedStatus
              and e.claimToken = :claimToken
            """)
    int markDeadLetter(@Param("id") UUID id,
                       @Param("claimToken") UUID claimToken,
                       @Param("claimedStatus") OutboxStatus claimedStatus,
                       @Param("deadLetterStatus") OutboxStatus deadLetterStatus,
                       @Param("lastError") String lastError);
}
