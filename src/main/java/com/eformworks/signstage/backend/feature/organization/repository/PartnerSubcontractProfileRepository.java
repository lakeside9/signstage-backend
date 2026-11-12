package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.PartnerSubcontractProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerSubcontractProfileRepository extends JpaRepository<PartnerSubcontractProfile, Long> {

    Optional<PartnerSubcontractProfile> findByOrganizationId(Long organizationId);
}
