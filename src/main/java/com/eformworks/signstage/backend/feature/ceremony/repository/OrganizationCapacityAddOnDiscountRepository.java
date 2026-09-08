package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationCapacityAddOnDiscountRepository extends JpaRepository<OrganizationCapacityAddOnDiscount, Long> {

    List<OrganizationCapacityAddOnDiscount> findAllByOrganizationIdAndCapacityAddOnIdOrderByEffectiveFromAsc(
            Long organizationId, Long capacityAddOnId
    );

    @Query("""
            select d from OrganizationCapacityAddOnDiscount d
            where d.organization.id = :organizationId
              and d.capacityAddOn.id = :capacityAddOnId
              and d.effectiveFrom <= :asOfDate
              and (d.effectiveTo is null or d.effectiveTo >= :asOfDate)
            """)
    Optional<OrganizationCapacityAddOnDiscount> findEffective(
            @Param("organizationId") Long organizationId,
            @Param("capacityAddOnId") Long capacityAddOnId,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<OrganizationCapacityAddOnDiscount> findAllByOrganizationId(Long organizationId);

    Page<OrganizationCapacityAddOnDiscount> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
