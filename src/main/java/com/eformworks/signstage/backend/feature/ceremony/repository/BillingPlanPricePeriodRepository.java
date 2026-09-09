package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanPricePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanPricePeriodRepository extends JpaRepository<BillingPlanPricePeriod, Long> {

    /** 이 플랜의 기간 전체(과거/현재/예정) — 관리 화면 목록/겹침 검사에 쓴다. */
    List<BillingPlanPricePeriod> findAllByBillingPlanIdOrderByEffectiveFromAsc(Long billingPlanId);

    Optional<BillingPlanPricePeriod> findByIdAndBillingPlanId(Long id, Long billingPlanId);

    /**
     * "오늘"(asOfDate) 유효한 기간 하나 — {@code TaxPolicyRepository.findEffectivePolicy}와 같은
     * 조건식(지연 평가, 배치 불필요). 기간 겹침이 제대로 막혀 있었다면 결과는 항상 0건 또는 1건이다.
     */
    @Query("""
            select p from BillingPlanPricePeriod p
            where p.billingPlan.id = :billingPlanId
              and p.effectiveFrom <= :asOfDate
              and (p.effectiveTo is null or p.effectiveTo >= :asOfDate)
            """)
    Optional<BillingPlanPricePeriod> findEffective(
            @Param("billingPlanId") Long billingPlanId,
            @Param("asOfDate") LocalDate asOfDate
    );

    void deleteAllByBillingPlanId(Long billingPlanId);

    long countByBillingPlanId(Long billingPlanId);
}
