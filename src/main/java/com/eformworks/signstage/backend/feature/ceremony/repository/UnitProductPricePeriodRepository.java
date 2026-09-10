package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitProductPricePeriodRepository extends JpaRepository<UnitProductPricePeriod, Long> {

    List<UnitProductPricePeriod> findAllByUnitProductIdOrderByEffectiveFromAsc(Long unitProductId);

    Optional<UnitProductPricePeriod> findByIdAndUnitProductId(Long id, Long unitProductId);

    @Query("""
            select p from UnitProductPricePeriod p
            where p.unitProduct.id = :unitProductId
              and p.effectiveFrom <= :asOfDate
              and (p.effectiveTo is null or p.effectiveTo >= :asOfDate)
            """)
    Optional<UnitProductPricePeriod> findEffective(
            @Param("unitProductId") Long unitProductId,
            @Param("asOfDate") LocalDate asOfDate
    );

    void deleteAllByUnitProductId(Long unitProductId);

    long countByUnitProductId(Long unitProductId);
}
