package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanDiscountPeriodHistoryRepository extends AppendOnlyRepository<BillingPlanDiscountPeriodHistory, Long> {

    List<BillingPlanDiscountPeriodHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);

    /** 플랜 삭제 시 이 플랜 자신의 할인 기간 변경 이력을 함께 지우는 데 쓴다({@link BillingPlanHistoryRepository#deleteAllByBillingPlanId}와 같은 이유). */
    @Modifying
    @Query("delete from BillingPlanDiscountPeriodHistory h where h.billingPlan.id = :billingPlanId")
    void deleteAllByBillingPlanId(@Param("billingPlanId") Long billingPlanId);
}
