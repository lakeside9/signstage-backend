package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscriptionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSubscriptionHistoryRepository extends JpaRepository<OrganizationSubscriptionHistory, Long> {

    List<OrganizationSubscriptionHistory> findAllByOrganizationSubscriptionIdOrderByCreatedAtDesc(
            Long organizationSubscriptionId
    );
}
