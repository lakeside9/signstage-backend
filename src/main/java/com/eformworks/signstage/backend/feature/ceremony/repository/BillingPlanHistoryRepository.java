package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import java.util.List;

public interface BillingPlanHistoryRepository extends AppendOnlyRepository<BillingPlanHistory, Long> {

    /** 이력 조회용(최신순). */
    List<BillingPlanHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);
}
