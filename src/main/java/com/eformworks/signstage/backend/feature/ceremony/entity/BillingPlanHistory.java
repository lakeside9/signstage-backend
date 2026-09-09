package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 과금 플랜(BillingPlan)의 이름 변경 이력. append-only다 — 수정/삭제 메서드를 두지 않는다.
 * 생성 시점과 {@code BillingPlanService#updatePlan} 호출 시(이름이 바뀔 때)마다 그 순간의
 * 상태를 스냅샷 한 행씩 쌓는다({@link CeremonyPlanHistory}와 같은 패턴). "누가/언제"는
 * {@link BaseEntity#getCreatedBy()}/{@link BaseEntity#getCreatedAt()}로 충분해 별도 컬럼을
 * 두지 않는다. 한도(용량) 구성은 이 엔티티의 고정 필드가 아니라
 * {@link BillingPlanHistoryCapacity}로 별도 스냅샷된다(2026-09-08, 항목 B).
 *
 * <p>가격정보/사용여부 변경 이력은 이 엔티티가 아니라 {@link BillingPlanPricePeriodHistory}가
 * 담당한다(signstage-docs business/billing-catalog-price-validity-period-review.md 결정,
 * 2026-09-09) — 두 축이 서로 다른 시점 기준을 갖기 때문이다: 이 History는 "언제 편집됐는가"
 * (created_at), Period(Period)History는 "언제부터 효력이 발생하는가"(effective_from)를 남긴다.
 */
@Entity
@Table(name = "billing_plan_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class BillingPlanHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder
    private BillingPlanHistory(BillingPlan billingPlan) {
        this.billingPlan = billingPlan;
        this.name = billingPlan.getName();
    }
}
