package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanCapacityAddOn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanCapacityAddOnRepository extends JpaRepository<BillingPlanCapacityAddOn, Long> {

    List<BillingPlanCapacityAddOn> findAllByBillingPlanId(Long billingPlanId);

    /** 플랜 수정 시 구매 가능 상품 구성을 통째로 교체하는 데 쓴다({@code BillingPlanService#updatePlan}). */
    void deleteAllByBillingPlanId(Long billingPlanId);
}
