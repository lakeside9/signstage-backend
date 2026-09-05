package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationHistory;
import java.util.List;

public interface OrganizationHistoryRepository extends AppendOnlyRepository<OrganizationHistory, Long> {

    /** 최신순 — 생성 시점 1건 + 이후 정보/상태가 바뀔 때마다 1건씩(사용자·관리자 구분 없음). */
    List<OrganizationHistory> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
