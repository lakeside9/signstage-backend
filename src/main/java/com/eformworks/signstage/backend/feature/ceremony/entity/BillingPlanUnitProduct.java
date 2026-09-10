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
 * <p><b>{@code purchasable} 컬럼 폐지(2026-09-10, 사용자 지시)</b> — 행이 존재하면(포함 수량이
 * 0이든 N이든) 그 자체로 이 플랜의 행사가 이 단위 상품을 추가구매할 수 있다는 뜻이다. "기본
 * 포함 없이 추가구매만 허용"(옛 {@code BillingPlanCapacityAddOn}의 목적)은
 * {@code includedQuantity=0}인 행으로 표현한다 — 예전엔 이걸 별도 {@code purchasable=true}
 * 플래그로 표현했지만, 실사용 데이터를 확인해보니 항상 "포함 수량이 0이고 purchasable만 true"거나
 * "포함 수량이 N이고 purchasable은 false" 둘 중 하나였다(두 값을 동시에 true로 쓰는 조합이 없었다)
 * — 즉 별도 플래그 없이 행의 존재 여부(+수량)만으로 이미 표현 가능했던 정보였다. {@link UnitProductType#isToggle()}인
 * 타입({@code EVENT_EFFECT_BUNDLE})은 {@code includedQuantity}가 0 또는 1로만 의미가 있다 —
 * {@code BillingPlanService#resolveUnitProducts}가 검증한다.
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

    /**
     * 기본 포함 수량 — 0 이상. 0이면 "기본 미포함, 추가구매로만 확보"(태블릿류 지금 동작과 동일).
     * 행이 존재하는 것 자체가 "이 플랜의 행사가 추가구매할 수 있다"는 뜻이라, 0이어도 삭제하지
     * 않고 남겨둔다.
     */
    @Column(name = "included_quantity", nullable = false)
    private Integer includedQuantity;

    @Builder
    private BillingPlanUnitProduct(
            BillingPlan billingPlan,
            UnitProduct unitProduct,
            Integer includedQuantity
    ) {
        this.billingPlan = billingPlan;
        this.unitProduct = unitProduct;
        this.includedQuantity = includedQuantity;
    }
}
