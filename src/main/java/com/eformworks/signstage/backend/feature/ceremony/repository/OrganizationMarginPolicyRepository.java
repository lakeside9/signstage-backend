package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationMarginPolicy;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationMarginPolicyRepository extends JpaRepository<OrganizationMarginPolicy, Long> {

    List<OrganizationMarginPolicy> findAllByOrganizationIdOrderByEffectiveFromDesc(Long organizationId);

    Optional<OrganizationMarginPolicy> findByIdAndOrganizationId(Long id, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OrganizationMarginPolicy p where p.organization.id = :organizationId")
    List<OrganizationMarginPolicy> findAllForUpdate(Long organizationId);

    @Query("""
            select p from OrganizationMarginPolicy p where p.organization.id = :organizationId
            and p.effectiveFrom <= :date and (p.effectiveTo is null or p.effectiveTo >= :date)
            """)
    Optional<OrganizationMarginPolicy> findEffective(Long organizationId, LocalDate date);
}
