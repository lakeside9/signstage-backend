package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscountHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationCapacityAddOnDiscountHistoryRepository extends JpaRepository<OrganizationCapacityAddOnDiscountHistory, Long> {

    List<OrganizationCapacityAddOnDiscountHistory> findAllByOrganizationIdAndCapacityAddOnIdOrderByCreatedAtDesc(
            Long organizationId, Long capacityAddOnId
    );
}
