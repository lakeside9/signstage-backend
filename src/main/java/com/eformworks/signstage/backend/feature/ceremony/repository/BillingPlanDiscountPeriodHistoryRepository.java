package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriodHistory;
import java.util.List;

public interface BillingPlanDiscountPeriodHistoryRepository extends AppendOnlyRepository<BillingPlanDiscountPeriodHistory, Long> {

    List<BillingPlanDiscountPeriodHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);
}
