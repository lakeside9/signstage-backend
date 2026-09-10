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
 * {@link BillingPlanHistory} 스냅샷 시점의 {@link BillingPlanUnitProduct} 구성 전체 — append-only다.
 * 옛 {@code BillingPlanHistoryCapacity}(한도 5종만 스냅샷하고 선택옵션/용량추가구매 구성 변경은
 * 이력화하지 않던 결함)를 대체하며 일반화한다 — {@code BillingPlanService#recordPlanHistory}가
 * 생성/수정 시점마다 그 순간의 {@code BillingPlanUnitProduct} 행 전체(포함 수량이든 구매 가능
 * 큐레이션이든 전부)를 복사해 저장한다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10).
 */
@Entity
@Table(name = "billing_plan_history_unit_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class BillingPlanHistoryUnitProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_history_id", nullable = false)
    private BillingPlanHistory billingPlanHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Column(name = "included_quantity", nullable = false)
    private Integer includedQuantity;

    @Column(nullable = false)
    private boolean purchasable;

    @Builder
    private BillingPlanHistoryUnitProduct(BillingPlanHistory billingPlanHistory, BillingPlanUnitProduct source) {
        this.billingPlanHistory = billingPlanHistory;
        this.unitProduct = source.getUnitProduct();
        this.includedQuantity = source.getIncludedQuantity();
        this.purchasable = source.isPurchasable();
    }
}
