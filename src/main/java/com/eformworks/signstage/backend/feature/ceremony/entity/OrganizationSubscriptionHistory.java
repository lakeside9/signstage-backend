package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link OrganizationSubscription} 상태 전이 이력. append-only다 —
 * {@link UnitProductPricePeriodHistory}와 같은 패턴으로, 상태가 바뀔 때마다(요청/승인/반려/
 * 해지요청/해지승인/해지반려/재계약대체/만료/소진) 그 순간의 상태와 사유를 한 행씩 쌓는다.
 * {@code OrganizationSubscription}은 하드 삭제되지 않으므로 살아있는 행을 그대로 FK로 참조한다
 * (오버라이드처럼 제거되는 엔티티가 아니라서 {@code organization}/{@code billingPlan} 조합으로
 * 우회 스코핑할 필요가 없다). "누가/언제"는 {@link BaseEntity#getCreatedBy()}/
 * {@link BaseEntity#getCreatedAt()}로 충분해 별도 컬럼을 두지 않되, 관리자 심사 행위자만은
 * {@code reviewedBy}로 별도 보존한다 — 요청 생성 이벤트는 요청자가 {@code createdBy}이고,
 * 그 뒤 승인/반려 이벤트는 심사자가 {@code createdBy}가 아닐 수 있어(배치가 만드는 만료/소진
 * 이벤트 등) 심사자를 명시적으로 남겨야 하는 경우를 위해서다.
 */
@Entity
@Table(name = "organization_subscription_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OrganizationSubscriptionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_subscription_id", nullable = false)
    private OrganizationSubscription organizationSubscription;

    /** 이 이력 행이 찍힌 시점의 상태(전이 후 값). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrganizationSubscriptionStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /** 반려 사유 또는 해지 요청 사유 — 해당하는 이벤트가 아니면 null. */
    @Column(length = 500)
    private String note;

    @Builder
    private OrganizationSubscriptionHistory(
            OrganizationSubscription organizationSubscription,
            OrganizationSubscriptionStatus status,
            Long reviewedBy,
            String note
    ) {
        this.organizationSubscription = organizationSubscription;
        this.status = status;
        this.reviewedBy = reviewedBy;
        this.note = note;
    }
}
