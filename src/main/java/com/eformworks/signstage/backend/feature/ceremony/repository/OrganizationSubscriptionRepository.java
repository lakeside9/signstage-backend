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

    /**
     * 조직의 구독 신청 이력(페이지네이션, 2026-09-14 추가 — 같은 날 후속으로 목록형+검색+
     * 페이지네비게이션으로 전환하면서 List 반환에서 Page 반환으로 바꿨다) — signstage-docs
     * business/subscription-margin-screen-separation-review.md 후속. PENDING/REJECTED로
     * 끝난 옛 신청, SUPERSEDED로 대체된 옛 계약까지 전부 포함한다. `Pageable`에 정렬을 안
     * 주면 이 메서드 이름의 `OrderByCreatedAtDesc`가 기본 정렬로 적용된다(최신순).
     */
    Page<OrganizationSubscription> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);

    /** 위와 같은 목록에 상태 필터(검색 영역의 "상태" 드롭다운)를 더한 버전. */
    Page<OrganizationSubscription> findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
            Long organizationId, OrganizationSubscriptionStatus status, Pageable pageable
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
