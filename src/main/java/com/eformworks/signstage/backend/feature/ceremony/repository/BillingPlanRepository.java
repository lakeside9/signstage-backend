package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {
}
