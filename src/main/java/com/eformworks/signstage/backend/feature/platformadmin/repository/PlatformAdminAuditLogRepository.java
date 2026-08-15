package com.eformworks.signstage.backend.feature.platformadmin.repository;

import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminAuditLogRepository extends JpaRepository<PlatformAdminAuditLog, Long> {

    Page<PlatformAdminAuditLog> findAllByAction(PlatformAdminAction action, Pageable pageable);
}
