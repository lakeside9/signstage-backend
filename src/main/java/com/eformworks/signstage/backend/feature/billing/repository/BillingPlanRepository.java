package com.eformworks.signstage.backend.feature.billing.repository;

import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {
}
