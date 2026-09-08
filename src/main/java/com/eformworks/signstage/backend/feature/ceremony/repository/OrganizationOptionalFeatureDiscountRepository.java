package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationOptionalFeatureDiscountRepository extends JpaRepository<OrganizationOptionalFeatureDiscount, Long> {

    List<OrganizationOptionalFeatureDiscount> findAllByOrganizationIdAndOptionalFeatureIdOrderByEffectiveFromAsc(
            Long organizationId, Long optionalFeatureId
    );

    @Query("""
            select d from OrganizationOptionalFeatureDiscount d
            where d.organization.id = :organizationId
              and d.optionalFeature.id = :optionalFeatureId
              and d.effectiveFrom <= :asOfDate
              and (d.effectiveTo is null or d.effectiveTo >= :asOfDate)
            """)
    Optional<OrganizationOptionalFeatureDiscount> findEffective(
            @Param("organizationId") Long organizationId,
            @Param("optionalFeatureId") Long optionalFeatureId,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<OrganizationOptionalFeatureDiscount> findAllByOrganizationId(Long organizationId);

    Page<OrganizationOptionalFeatureDiscount> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
