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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link BillingPlan}의 할인 기간별 버전 — {@code BillingPlanPricePeriod} 대체(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.3절).
 * 플랜은 더 이상 자기 가격(salePrice/supplyPrice/taxCode)을 갖지 않는다 — "오늘 가격"은
 * {@code Σ(BillingPlanUnitProduct.unitProduct.effectivePrice × includedQuantity)}로 조회
 * 시점에 계산되고, 이 엔티티는 그 합계에 적용할 **할인 하나**만 기간별로 관리한다.
 *
 * <p>행 하나 = 기간 하나(다중 버전, 옛 {@code BillingPlanPricePeriod}와 같은 패턴). 기간이
 * 겹치지 않게 막는 것은 DB 제약이 아니라 {@code BillingPlanService}의 서비스 레이어 검증이다.
 */
@Entity
@Table(
        name = "billing_plan_discount_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bpdp_plan_period",
                columnNames = {"billing_plan_id", "effective_from"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanDiscountPeriod extends BaseEntity {

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

    @Builder
    private BillingPlanDiscountPeriod(
            BillingPlan billingPlan,
            DiscountType discountType,
            BigDecimal discountValue,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.billingPlan = billingPlan;
        this.discount = new DiscountInfo(discountType, discountValue);
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** 플랫폼 관리자가 이미 있는 기간 하나(할인/사용여부/기간 자체)를 고칠 때 쓴다. */
    public void update(
            DiscountType discountType,
            BigDecimal discountValue,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.discount = new DiscountInfo(discountType, discountValue);
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
