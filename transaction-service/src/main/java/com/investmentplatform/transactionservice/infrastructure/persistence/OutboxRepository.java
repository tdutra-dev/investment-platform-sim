package com.investmentplatform.transactionservice.infrastructure.persistence;

import com.investmentplatform.outbox.OutboxStatus;
import com.investmentplatform.outbox.OutboxStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID>, OutboxStore<OutboxEvent> {

    @Override
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Override
    long countByStatus(OutboxStatus status);

    @Override
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = com.investmentplatform.outbox.OutboxStatus.SENT AND e.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") LocalDateTime cutoff);
}
