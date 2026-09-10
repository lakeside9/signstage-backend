package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistoryUnitProduct;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanHistoryUnitProductRepository extends AppendOnlyRepository<BillingPlanHistoryUnitProduct, Long> {

    List<BillingPlanHistoryUnitProduct> findAllByBillingPlanHistoryId(Long billingPlanHistoryId);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 어떤 플랜 이력에든 포함된 적이 있는지. */
    boolean existsByUnitProductId(Long unitProductId);

    /**
     * 플랜 삭제 시 이 플랜의 이력 행들이 스냅샷해 둔 단위 상품 구성 행을 함께 지운다 —
     * {@code billing_plan_id} 컬럼이 없어(부모는 {@link com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory}) 조인 경로로 찾는다.
     * {@code BillingPlanHistoryRepository.deleteAllByBillingPlanId}보다 먼저 호출해야 한다(FK 순서).
     */
    @Modifying
    @Query("delete from BillingPlanHistoryUnitProduct h where h.billingPlanHistory.billingPlan.id = :billingPlanId")
    void deleteAllByBillingPlanHistory_BillingPlanId(@Param("billingPlanId") Long billingPlanId);
}
