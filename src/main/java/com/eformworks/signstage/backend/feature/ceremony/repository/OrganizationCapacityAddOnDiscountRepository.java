package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationCapacityAddOnDiscount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationCapacityAddOnDiscountRepository extends JpaRepository<OrganizationCapacityAddOnDiscount, Long> {

    Optional<OrganizationCapacityAddOnDiscount> findByOrganizationIdAndCapacityAddOnId(Long organizationId, Long capacityAddOnId);

    List<OrganizationCapacityAddOnDiscount> findAllByOrganizationId(Long organizationId);
}
