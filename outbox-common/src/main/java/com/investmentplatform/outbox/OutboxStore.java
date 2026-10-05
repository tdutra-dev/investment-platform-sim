package com.investmentplatform.outbox;

import java.time.LocalDateTime;
import java.util.List;

/** Persistence port of the outbox, implemented by each service's Spring Data repository. */
public interface OutboxStore<E extends OutboxEventBase> {

    /**
     * Locks and returns up to {@code limit} due PENDING rows, oldest first, skipping rows already locked by
     * another instance ({@code SELECT ... FOR UPDATE SKIP LOCKED}). Must run inside a transaction.
     */
    List<E> claimBatch(LocalDateTime now, int limit);

    long countByStatus(OutboxStatus status);

    int deleteSentBefore(LocalDateTime cutoff);
}
