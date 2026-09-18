package com.sidru.sidru_api.audit.infrastructure.persistence.jpa.repositories;

import com.sidru.sidru_api.audit.domain.model.aggregates.AuditLog;
import com.sidru.sidru_api.audit.domain.model.valueobjects.AuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByIdDesc();

    List<AuditLog> findByEventTypeOrderByIdDesc(AuditEventType eventType);

    long countByEventType(AuditEventType eventType);
}
