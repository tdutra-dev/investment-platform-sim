package com.investmentplatform.auditlogservice.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLogEntry, String> {

    List<AuditLogEntry> findByAggregateIdOrderByReceivedAtDesc(String aggregateId);
}
