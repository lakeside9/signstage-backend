package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanPricePeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanPricePeriodHistoryRepository extends JpaRepository<BillingPlanPricePeriodHistory, Long> {

    List<BillingPlanPricePeriodHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);
}
