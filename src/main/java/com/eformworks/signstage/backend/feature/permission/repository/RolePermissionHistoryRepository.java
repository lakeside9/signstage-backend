package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.permission.entity.RolePermissionHistory;

public interface RolePermissionHistoryRepository extends AppendOnlyRepository<RolePermissionHistory, Long> {
}
