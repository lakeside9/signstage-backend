package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.CustomerPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    @Query("""
            select p from CustomerPrice p
            left join fetch p.billingPlan
            left join fetch p.unitProduct
            order by p.billingPlan.id, p.unitProduct.id, p.period.effectiveFrom desc
            """)
    List<CustomerPrice> findAllWithTargets();

    @Query("""
            select p from CustomerPrice p
            where p.billingPlan.id = :billingPlanId
              and p.period.effectiveFrom <= :asOfDate
              and (p.period.effectiveTo is null or p.period.effectiveTo >= :asOfDate)
            """)
    Optional<CustomerPrice> findEffectiveForPlan(@Param("billingPlanId") Long billingPlanId, @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select p from CustomerPrice p
            where p.unitProduct.id = :unitProductId
              and p.period.effectiveFrom <= :asOfDate
              and (p.period.effectiveTo is null or p.period.effectiveTo >= :asOfDate)
            """)
    Optional<CustomerPrice> findEffectiveForUnitProduct(
            @Param("unitProductId") Long unitProductId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query("""
            select count(p) > 0 from CustomerPrice p
            where p.billingPlan.id = :billingPlanId
              and p.period.effectiveFrom <= :to
              and (p.period.effectiveTo is null or p.period.effectiveTo >= :from)
              and (:excludeId is null or p.id <> :excludeId)
            """)
    boolean existsOverlapForPlan(
            @Param("billingPlanId") Long billingPlanId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            select count(p) > 0 from CustomerPrice p
            where p.unitProduct.id = :unitProductId
              and p.period.effectiveFrom <= :to
              and (p.period.effectiveTo is null or p.period.effectiveTo >= :from)
              and (:excludeId is null or p.id <> :excludeId)
            """)
    boolean existsOverlapForUnitProduct(
            @Param("unitProductId") Long unitProductId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId
    );

    boolean existsByBillingPlanId(Long billingPlanId);

    boolean existsByUnitProductId(Long unitProductId);
}
