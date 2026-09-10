package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryUnitProduct;
import java.util.List;

public interface BillingPlanHistoryUnitProductRepository extends AppendOnlyRepository<BillingPlanHistoryUnitProduct, Long> {

    List<BillingPlanHistoryUnitProduct> findAllByBillingPlanHistoryId(Long billingPlanHistoryId);
}
