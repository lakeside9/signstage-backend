package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.repository.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByCode(String code);
}
