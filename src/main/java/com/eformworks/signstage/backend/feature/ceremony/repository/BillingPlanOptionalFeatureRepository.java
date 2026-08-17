package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanOptionalFeatureRepository extends JpaRepository<BillingPlanOptionalFeature, Long> {

    List<BillingPlanOptionalFeature> findAllByBillingPlanId(Long billingPlanId);
}
