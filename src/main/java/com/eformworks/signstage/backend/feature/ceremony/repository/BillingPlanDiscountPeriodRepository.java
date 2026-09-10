package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanDiscountPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPlanDiscountPeriodRepository extends JpaRepository<BillingPlanDiscountPeriod, Long> {

    List<BillingPlanDiscountPeriod> findAllByBillingPlanIdOrderByEffectiveFromAsc(Long billingPlanId);

    Optional<BillingPlanDiscountPeriod> findByIdAndBillingPlanId(Long id, Long billingPlanId);

    @Query("""
            select p from BillingPlanDiscountPeriod p
            where p.billingPlan.id = :billingPlanId
              and p.effectiveFrom <= :asOfDate
              and (p.effectiveTo is null or p.effectiveTo >= :asOfDate)
            """)
    Optional<BillingPlanDiscountPeriod> findEffective(
            @Param("billingPlanId") Long billingPlanId,
            @Param("asOfDate") LocalDate asOfDate
    );

    void deleteAllByBillingPlanId(Long billingPlanId);

    long countByBillingPlanId(Long billingPlanId);
}
