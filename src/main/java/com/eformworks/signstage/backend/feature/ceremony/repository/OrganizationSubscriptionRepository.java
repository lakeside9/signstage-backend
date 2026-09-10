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

    /**
     * 과금 플랜 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 구독은 하드 삭제되지 않고
     * 재계약 시 {@code SUPERSEDED}로 상태만 바뀌므로(클래스 javadoc 참고), 이 행 존재 여부만으로
     * "지금이든 과거든 이 플랜을 구독한 적이 있는지"까지 전부 확인된다.
     */
    boolean existsByBillingPlanId(Long billingPlanId);
}
