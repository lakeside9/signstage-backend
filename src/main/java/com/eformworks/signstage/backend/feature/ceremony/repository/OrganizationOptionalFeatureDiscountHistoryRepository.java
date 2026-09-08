package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationOptionalFeatureDiscountHistory;
import java.util.List;

public interface OrganizationOptionalFeatureDiscountHistoryRepository extends AppendOnlyRepository<OrganizationOptionalFeatureDiscountHistory, Long> {

    List<OrganizationOptionalFeatureDiscountHistory> findAllByOrganizationIdAndOptionalFeatureIdOrderByCreatedAtDesc(
            Long organizationId, Long optionalFeatureId
    );
}
