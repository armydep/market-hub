package com.am.market_hub.admin.repository;

import com.am.market_hub.admin.domain.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** No custom queries: Phase 1 has no audit-read endpoint. */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
