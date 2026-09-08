package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryCapacity;
import java.util.List;

public interface BillingPlanHistoryCapacityRepository extends AppendOnlyRepository<BillingPlanHistoryCapacity, Long> {

    List<BillingPlanHistoryCapacity> findAllByBillingPlanHistoryId(Long billingPlanHistoryId);
}
