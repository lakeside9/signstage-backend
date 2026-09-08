package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanCapacity;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanCapacityRepository extends JpaRepository<BillingPlanCapacity, Long> {

    List<BillingPlanCapacity> findAllByBillingPlanId(Long billingPlanId);

    Optional<BillingPlanCapacity> findByBillingPlanIdAndCapacityType(Long billingPlanId, CapacityType capacityType);

    /** 플랜 수정 시 한도 구성을 통째로 교체하는 데 쓴다({@code BillingPlanService#updatePlan}). */
    void deleteAllByBillingPlanId(Long billingPlanId);
}
