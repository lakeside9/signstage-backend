package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscountHistory;
import java.util.List;

public interface OrganizationBillingPlanDiscountHistoryRepository extends AppendOnlyRepository<OrganizationBillingPlanDiscountHistory, Long> {

    /** 최신순 — 설정(생성/수정) 시점마다, 그리고 제거 시점에 1건씩(removed=true) 쌓인다. */
    List<OrganizationBillingPlanDiscountHistory> findAllByOrganizationIdAndBillingPlanIdOrderByCreatedAtDesc(
            Long organizationId, Long billingPlanId
    );
}
