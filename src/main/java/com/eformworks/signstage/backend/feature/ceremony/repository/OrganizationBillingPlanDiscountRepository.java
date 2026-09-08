package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationBillingPlanDiscount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationBillingPlanDiscountRepository extends JpaRepository<OrganizationBillingPlanDiscount, Long> {

    /** 이 조직×이 플랜의 기간 전체(과거/현재/예정) — 관리 화면 목록/겹침 검사에 쓴다. */
    List<OrganizationBillingPlanDiscount> findAllByOrganizationIdAndBillingPlanIdOrderByEffectiveFromAsc(
            Long organizationId, Long billingPlanId
    );

    /**
     * "오늘"(asOfDate) 유효한 기간 하나 — {@code TaxPolicyRepository.findEffectivePolicy}와 같은
     * 조건식(지연 평가, 배치 불필요). 기간 겹침이 제대로 막혀 있었다면 결과는 항상 0건 또는 1건이다.
     */
    @Query("""
            select d from OrganizationBillingPlanDiscount d
            where d.organization.id = :organizationId
              and d.billingPlan.id = :billingPlanId
              and d.effectiveFrom <= :asOfDate
              and (d.effectiveTo is null or d.effectiveTo >= :asOfDate)
            """)
    Optional<OrganizationBillingPlanDiscount> findEffective(
            @Param("organizationId") Long organizationId,
            @Param("billingPlanId") Long billingPlanId,
            @Param("asOfDate") LocalDate asOfDate
    );

    /** 조직별 할인 관리 화면이 이 조직에 걸린 오버라이드 전체(모든 품목·모든 기간)를 한 번에 보여주는 데 쓴다. */
    List<OrganizationBillingPlanDiscount> findAllByOrganizationId(Long organizationId);

    /** 조직 횡단 목록 화면(discount-management-screen-separation-review.md)의 organizationId 필터용 페이지 조회. */
    Page<OrganizationBillingPlanDiscount> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
