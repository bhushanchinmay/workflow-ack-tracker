package com.example.workflowtracker.repository;

import com.example.workflowtracker.domain.Workflow;
import com.example.workflowtracker.domain.WorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    @EntityGraph(attributePaths = "acknowledgements")
    Optional<Workflow> findWithAcknowledgementsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "acknowledgements")
    @Query("select w from Workflow w where w.id = :id")
    Optional<Workflow> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "acknowledgements")
    List<Workflow> findByStatusOrderByCreatedAtAsc(WorkflowStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "acknowledgements")
    @Query("select w from Workflow w where w.status = :status and w.ackDeadline <= :now")
    List<Workflow> findOverdueForUpdate(@Param("status") WorkflowStatus status,
                                        @Param("now") Instant now,
                                        Pageable pageable);
}
