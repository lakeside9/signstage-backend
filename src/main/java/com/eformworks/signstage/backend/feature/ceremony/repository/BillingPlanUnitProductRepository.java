package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanUnitProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanUnitProductRepository extends JpaRepository<BillingPlanUnitProduct, Long> {

    List<BillingPlanUnitProduct> findAllByBillingPlanId(Long billingPlanId);

    Optional<BillingPlanUnitProduct> findByBillingPlanIdAndUnitProductId(Long billingPlanId, Long unitProductId);

    void deleteAllByBillingPlanId(Long billingPlanId);
}
