package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationMarginPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMarginPolicyRepository extends JpaRepository<OrganizationMarginPolicy, Long> {

    Optional<OrganizationMarginPolicy> findByOrganizationId(Long organizationId);
}
