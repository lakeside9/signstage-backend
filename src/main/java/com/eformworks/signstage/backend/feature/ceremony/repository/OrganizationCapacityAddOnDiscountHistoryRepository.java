package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscountHistory;
import java.util.List;

public interface OrganizationCapacityAddOnDiscountHistoryRepository extends AppendOnlyRepository<OrganizationCapacityAddOnDiscountHistory, Long> {

    List<OrganizationCapacityAddOnDiscountHistory> findAllByOrganizationIdAndCapacityAddOnIdOrderByCreatedAtDesc(
            Long organizationId, Long capacityAddOnId
    );
}
