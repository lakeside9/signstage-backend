package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanHistoryRepository extends AppendOnlyRepository<BillingPlanHistory, Long> {

    /** 이력 조회용(최신순). */
    List<BillingPlanHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);

    /**
     * 플랜 삭제({@code BillingPlanService#deletePlan}) 시 이 플랜 자신의 편집 이력을 함께 지우는
     * 데 쓴다 — {@code AppendOnlyRepository}는 delete를 노출하지 않지만 커스텀 쿼리는 예외다
     * ({@code UnitProductHistoryRepository.deleteAllByUnitProductId}와 같은 패턴). 호출 전
     * {@code BillingPlanHistoryUnitProductRepository.deleteAllByBillingPlanHistory_BillingPlanId}로
     * 자식 스냅샷부터 지워야 한다(FK 순서).
     */
    @Modifying
    @Query("delete from BillingPlanHistory h where h.billingPlan.id = :billingPlanId")
    void deleteAllByBillingPlanId(@Param("billingPlanId") Long billingPlanId);
}
