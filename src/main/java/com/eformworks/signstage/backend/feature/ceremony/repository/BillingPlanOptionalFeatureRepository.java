package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanOptionalFeature;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanOptionalFeatureRepository extends JpaRepository<BillingPlanOptionalFeature, Long> {

    List<BillingPlanOptionalFeature> findAllByBillingPlanId(Long billingPlanId);

    /** 플랜 수정 시 선택옵션 구성을 통째로 교체하는 데 쓴다({@code BillingPlanService#updatePlan}). */
    void deleteAllByBillingPlanId(Long billingPlanId);
}
