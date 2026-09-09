package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOnPricePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapacityAddOnPricePeriodRepository extends JpaRepository<CapacityAddOnPricePeriod, Long> {

    List<CapacityAddOnPricePeriod> findAllByCapacityAddOnIdOrderByEffectiveFromAsc(Long capacityAddOnId);

    Optional<CapacityAddOnPricePeriod> findByIdAndCapacityAddOnId(Long id, Long capacityAddOnId);

    @Query("""
            select p from CapacityAddOnPricePeriod p
            where p.capacityAddOn.id = :capacityAddOnId
              and p.effectiveFrom <= :asOfDate
              and (p.effectiveTo is null or p.effectiveTo >= :asOfDate)
            """)
    Optional<CapacityAddOnPricePeriod> findEffective(
            @Param("capacityAddOnId") Long capacityAddOnId,
            @Param("asOfDate") LocalDate asOfDate
    );

    void deleteAllByCapacityAddOnId(Long capacityAddOnId);

    long countByCapacityAddOnId(Long capacityAddOnId);
}
