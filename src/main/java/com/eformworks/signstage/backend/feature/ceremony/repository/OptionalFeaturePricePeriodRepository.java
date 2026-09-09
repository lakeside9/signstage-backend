package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeaturePricePeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OptionalFeaturePricePeriodRepository extends JpaRepository<OptionalFeaturePricePeriod, Long> {

    List<OptionalFeaturePricePeriod> findAllByOptionalFeatureIdOrderByEffectiveFromAsc(Long optionalFeatureId);

    Optional<OptionalFeaturePricePeriod> findByIdAndOptionalFeatureId(Long id, Long optionalFeatureId);

    @Query("""
            select p from OptionalFeaturePricePeriod p
            where p.optionalFeature.id = :optionalFeatureId
              and p.effectiveFrom <= :asOfDate
              and (p.effectiveTo is null or p.effectiveTo >= :asOfDate)
            """)
    Optional<OptionalFeaturePricePeriod> findEffective(
            @Param("optionalFeatureId") Long optionalFeatureId,
            @Param("asOfDate") LocalDate asOfDate
    );

    void deleteAllByOptionalFeatureId(Long optionalFeatureId);

    long countByOptionalFeatureId(Long optionalFeatureId);
}
