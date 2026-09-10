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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랜이 포함하는 단위 상품 구성(조인) — 기존 {@code BillingPlanCapacity}(한도 5종 기본 포함) +
 * {@code BillingPlanOptionalFeature}(선택옵션 무료 포함) + {@code BillingPlanCapacityAddOn}
 * (구매 가능 큐레이션) 3개를 하나로 합쳤다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 3.3절).
 *
 * <p>{@code includedQuantity}(기본 포함 수량, 0 이상)와 {@code purchasable}(추가구매 후보로 고를
 * 수 있는지) 두 컬럼으로 옛 3개 조인의 의미를 전부 표현한다 — 옛 {@code BillingPlanOptionalFeature}
 * (무료 포함)는 {@code includedQuantity=1}인 행으로, 옛 {@code BillingPlanCapacity}(한도 5종)는
 * {@code includedQuantity=N}인 행으로, 옛 {@code BillingPlanCapacityAddOn}(구매 가능 큐레이션)은
 * {@code purchasable=true}(포함 여부와 무관한 별도 플래그)로 표현된다. 두 값은 독립적이다 — 예를
 * 들어 서명자를 100명 기본 포함하면서 동시에 추가구매도 허용하려면 {@code includedQuantity=100,
 * purchasable=true}인 행 하나면 된다.
 *
 * <p>플랜 소계 계산({@code CeremonyService#calculateEstimatedTotal})은 이 구성 전체를 순회하며
 * {@code unitProduct.effectivePrice(오늘) × includedQuantity}를 더한다 — 단위 상품 자체는 할인이
 * 없으므로({@link ProductPriceInfo}) 이 합계에 플랜 자체 할인({@link BillingPlanDiscountPeriod})을
 * 한 번만 적용한다.
 */
@Entity
@Table(
        name = "billing_plan_unit_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bpup_plan_product",
                columnNames = {"billing_plan_id", "unit_product_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanUnitProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    /** 기본 포함 수량 — 0 이상. 0이면 "기본 미포함, 추가구매로만 확보"(태블릿류 지금 동작과 동일). */
    @Column(name = "included_quantity", nullable = false)
    private Integer includedQuantity;

    /** 이 플랜을 쓰는 행사가 이 단위 상품을 추가구매 후보로 고를 수 있는지(안 A 큐레이션). */
    @Column(nullable = false)
    private boolean purchasable;

    @Builder
    private BillingPlanUnitProduct(
            BillingPlan billingPlan,
            UnitProduct unitProduct,
            Integer includedQuantity,
            boolean purchasable
    ) {
        this.billingPlan = billingPlan;
        this.unitProduct = unitProduct;
        this.includedQuantity = includedQuantity;
        this.purchasable = purchasable;
    }
}
