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
 * {@link BillingPlanDiscountPeriod}(플랜 할인 기간)의 생성/수정/삭제 이력. append-only다 —
 * {@code BillingPlanPricePeriodHistory}와 같은 패턴.
 */
@Entity
@Table(name = "billing_plan_discount_period_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class BillingPlanDiscountPeriodHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Embedded
    private DiscountInfo discount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean removed;

    @Builder
    private BillingPlanDiscountPeriodHistory(BillingPlan billingPlan, BillingPlanDiscountPeriod period, boolean removed) {
        this.billingPlan = billingPlan;
        this.discount = period.getDiscount();
        this.active = period.isActive();
        this.effectiveFrom = period.getEffectiveFrom();
        this.effectiveTo = period.getEffectiveTo();
        this.removed = removed;
    }
}
