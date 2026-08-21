package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscountHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationOptionalFeatureDiscountHistoryRepository extends JpaRepository<OrganizationOptionalFeatureDiscountHistory, Long> {

    List<OrganizationOptionalFeatureDiscountHistory> findAllByOrganizationIdAndOptionalFeatureIdOrderByCreatedAtDesc(
            Long organizationId, Long optionalFeatureId
    );
}
