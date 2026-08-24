package com.example.workflowtracker.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
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
}
