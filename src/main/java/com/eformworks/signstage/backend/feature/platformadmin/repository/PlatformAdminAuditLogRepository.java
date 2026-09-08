package com.eformworks.signstage.backend.feature.platformadmin.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformAdminAuditLogRepository extends AppendOnlyRepository<PlatformAdminAuditLog, Long> {

    Page<PlatformAdminAuditLog> findAllByAction(PlatformAdminAction action, Pageable pageable);
}
