package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link BillingPlanPricePeriod}(플랜 판매가격 기간)의 생성/수정/삭제 이력. append-only다 —
 * 수정/삭제 메서드를 두지 않는다. {@code OrganizationBillingPlanDiscountHistory}와 같은
 * 패턴 — 기간 삭제가 실제로 일어나므로, 살아있는 기간 행을 참조하는 대신 {@code billingPlan}
 * (삭제되지 않는 값) 기준으로 스코핑하고 {@code removed}로 "이 시점에 기간이 제거됐다"를
 * 표현한다. 제거 이벤트도 그 직전 값을 그대로 남긴다 — 무엇이 제거됐는지 이력만 보고 알 수
 * 있어야 하기 때문이다. "누가/언제"는 {@link BaseEntity#getCreatedBy()}/
 * {@link BaseEntity#getCreatedAt()}로 충분해 별도 컬럼을 두지 않는다.
 */
@Entity
@Table(name = "billing_plan_price_period_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class BillingPlanPricePeriodHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Embedded
    private CatalogPriceInfo priceInfo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** true면 이 행이 "기간 제거" 이벤트다 — 나머지 필드는 제거 직전 값. */
    @Column(nullable = false)
    private boolean removed;

    @Builder
    private BillingPlanPricePeriodHistory(BillingPlan billingPlan, BillingPlanPricePeriod period, boolean removed) {
        this.billingPlan = billingPlan;
        this.priceInfo = period.getPriceInfo();
        this.active = period.isActive();
        this.effectiveFrom = period.getEffectiveFrom();
        this.effectiveTo = period.getEffectiveTo();
        this.removed = removed;
    }
}
