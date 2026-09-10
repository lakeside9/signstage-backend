package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직×구독형 플랜 계약(구독) — signstage-docs
 * business/organization-event-discount-pricing-review.md 8장 결정(2026-09-10 착수 확정,
 * 8.5절 명명). 조직(OWNER)이 요청하면 플랫폼 관리자가 승인해야 실제로 {@code ACTIVE}가
 * 된다({@link OrganizationCreationRequest}와 같은 요청→승인 패턴). "이 조직이 이
 * {@link BillingPlan}(구독형)을 몇 번 더 쓸 수 있는지"를 관리하는 조직 단위 사용량 게이트다 —
 * 개별 {@code Ceremony}의 가격 계산과는 무관하다(플랜의 단위 상품 소계·할인은 그대로 적용된다).
 *
 * <p>승인 시점에 플랜의 조건(이름/{@code subscriptionType}/기간/허용 횟수)을 스냅샷한다
 * (8.3-5/8.7 결정) — 승인 이후 카탈로그의 그 플랜 값이 바뀌어도 이미 승인된 구독은 영향받지
 * 않는다. 실사용 건수는 별도 카운터 컬럼으로 관리하지 않고 {@code Ceremony.subscriptionId}가
 * 이 구독을 참조하는 행 수를 그때그때 세어 계산한다(8.2-9번 권장 — 카운터 동기화 위험 회피).
 *
 * <p>조직당 {@code ACTIVE}(또는 승인 대기 중인 {@code PENDING}/{@code CANCELLATION_REQUESTED})
 * 구독은 항상 최대 1건이다 — DB 제약이 아니라 서비스 레이어(트랜잭션 안에서 조회 후 생성)로
 * 강제한다(8.4-1 결정, MySQL이 조건부 유니크 인덱스를 지원하지 않아서다). 재계약(같은 조직이
 * 새 구독을 요청해 승인받는 것)은 기존 {@code ACTIVE} 행을 {@code SUPERSEDED}로 전이시키는
 * 것으로 표현한다 — 하드 삭제하지 않는다(8.3-2 결정, 이 프로젝트의 append-only/상태 보존
 * 관례).
 */
@Entity
@Table(name = "organization_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrganizationSubscriptionStatus status;

    // ---- 승인 시점 스냅샷(8.3-5 결정) — 카탈로그가 나중에 바뀌어도 이 값은 고정 ----

    @Column(name = "plan_name_snapshot", length = 100)
    private String planNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type_snapshot", length = 20)
    private SubscriptionType subscriptionTypeSnapshot;

    @Column(name = "period_months_snapshot")
    private Integer periodMonthsSnapshot;

    @Column(name = "allowed_count_snapshot")
    private Integer allowedCountSnapshot;

    // ---- 기간 ----

    /** 승인 시점(ACTIVE 전이 시점)에 채워진다. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** {@code PERIOD_AND_COUNT}만 값을 갖는다 — {@code COUNT_ONLY}는 처음부터 끝까지 null. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_source", nullable = false, length = 10)
    private SubscriptionApprovalSource approvalSource;

    // ---- 심사(요청 승인/반려, 중도해지 승인/반려 — 최신 심사 결과를 담는다. 전체 이력은
    //      OrganizationSubscriptionHistory가 append-only로 남긴다) ----

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** ACTIVE 상태에서 OWNER가 중도 해지를 요청할 때 남기는 사유(관리자의 rejectionReason과 별개). */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private OrganizationSubscription(Organization organization, BillingPlan billingPlan, User requestedBy) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.requestedBy = requestedBy;
        this.status = OrganizationSubscriptionStatus.PENDING;
        this.approvalSource = SubscriptionApprovalSource.MANUAL;
    }

    /** PENDING → ACTIVE. 이 시점에 플랜 조건을 스냅샷하고 시작/종료일을 확정한다. */
    public void approve(Long reviewedBy, LocalDate startDate, LocalDate endDate) {
        this.status = OrganizationSubscriptionStatus.ACTIVE;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.planNameSnapshot = billingPlan.getName();
        this.subscriptionTypeSnapshot = billingPlan.getSubscriptionType();
        this.periodMonthsSnapshot = billingPlan.getSubscriptionPeriodMonths();
        this.allowedCountSnapshot = billingPlan.getSubscriptionAllowedCount();
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** PENDING → REJECTED. */
    public void reject(Long reviewedBy, String rejectionReason) {
        this.status = OrganizationSubscriptionStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }

    /** ACTIVE → CANCELLATION_REQUESTED. OWNER가 호출한다(서비스 레이어에서 역할 검증). */
    public void requestCancellation(String cancellationReason) {
        this.status = OrganizationSubscriptionStatus.CANCELLATION_REQUESTED;
        this.cancellationReason = cancellationReason;
    }

    /** CANCELLATION_REQUESTED → CANCELLED. */
    public void approveCancellation(Long reviewedBy) {
        this.status = OrganizationSubscriptionStatus.CANCELLED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    /** CANCELLATION_REQUESTED → ACTIVE(해지 요청 반려 — 구독은 그대로 유지). */
    public void rejectCancellation(Long reviewedBy, String rejectionReason) {
        this.status = OrganizationSubscriptionStatus.ACTIVE;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }

    /** ACTIVE → SUPERSEDED. 같은 조직이 새 구독을 승인받을 때 이전 ACTIVE 행에 호출한다. */
    public void supersede(Long reviewedBy) {
        this.status = OrganizationSubscriptionStatus.SUPERSEDED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    /** ACTIVE → EXPIRED. 배치 스케줄러가 호출한다(PERIOD_AND_COUNT만 대상). */
    public void expire() {
        this.status = OrganizationSubscriptionStatus.EXPIRED;
    }

    /** ACTIVE → EXHAUSTED. 허용 횟수를 다 쓰는 순간 그 트랜잭션 안에서 이벤트 기반으로 호출한다. */
    public void exhaust() {
        this.status = OrganizationSubscriptionStatus.EXHAUSTED;
    }

    public boolean isPeriodAndCount() {
        return subscriptionTypeSnapshot == SubscriptionType.PERIOD_AND_COUNT;
    }
}
