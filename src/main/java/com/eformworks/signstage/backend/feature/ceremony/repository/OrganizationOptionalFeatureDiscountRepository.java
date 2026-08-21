package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationOptionalFeatureDiscountRepository extends JpaRepository<OrganizationOptionalFeatureDiscount, Long> {

    Optional<OrganizationOptionalFeatureDiscount> findByOrganizationIdAndOptionalFeatureId(Long organizationId, Long optionalFeatureId);

    List<OrganizationOptionalFeatureDiscount> findAllByOrganizationId(Long organizationId);
}
