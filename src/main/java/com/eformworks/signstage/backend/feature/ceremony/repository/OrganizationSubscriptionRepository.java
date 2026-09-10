package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscription;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Long> {

    /** 조직당 진행 중(PENDING/ACTIVE/CANCELLATION_REQUESTED) 구독 조회 — "현재 구독 상태" 화면용. */
    List<OrganizationSubscription> findAllByOrganizationIdAndStatusIn(
            Long organizationId, List<OrganizationSubscriptionStatus> statuses
    );

    Optional<OrganizationSubscription> findByOrganizationIdAndStatus(
            Long organizationId, OrganizationSubscriptionStatus status
    );

    boolean existsByOrganizationIdAndStatusIn(Long organizationId, List<OrganizationSubscriptionStatus> statuses);

    Page<OrganizationSubscription> findAllByStatus(OrganizationSubscriptionStatus status, Pageable pageable);

    /** 배치 스케줄러가 매일 만료 대상(PERIOD_AND_COUNT, endDate 지남)을 찾는다. */
    List<OrganizationSubscription> findAllByStatusAndEndDateBefore(
            OrganizationSubscriptionStatus status, LocalDate date
    );
}
