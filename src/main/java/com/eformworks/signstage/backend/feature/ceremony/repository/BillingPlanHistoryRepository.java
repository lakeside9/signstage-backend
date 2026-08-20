package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingPlanHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanHistoryRepository extends JpaRepository<BillingPlanHistory, Long> {

    /** 이력 조회용(최신순). */
    List<BillingPlanHistory> findAllByBillingPlanIdOrderByCreatedAtDesc(Long billingPlanId);
}
