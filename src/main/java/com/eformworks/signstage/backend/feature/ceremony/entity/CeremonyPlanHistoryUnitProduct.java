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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link CeremonyPlanHistory} 스냅샷 시점에 그 플랜이 포함하던 단위 상품 구성 — 옛
 * {@code CeremonyPlanHistoryCapacity}+{@code CeremonyPlanHistoryOptionalFeature}+
 * {@code CeremonyPlanHistoryCapacityAddOn} 3개를 통합한다(signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 5장).
 * append-only다 — Ceremony 생성(최초 플랜 선택)/{@code CeremonyService#changePlan} 시점마다
 * 그 순간의 {@link BillingPlanUnitProduct} 구성을 그대로 복사한다.
 *
 * <p>예전엔 "포함 수량"만 스냅샷하면 됐다(한도 5종은 가격이 없었으므로) — 이제 각 단위 상품이
 * 자기 가격을 갖고 그 가격이 시간에 따라 바뀔 수 있어({@link UnitProductPricePeriod}),
 * **포함 수량 × 그 순간 단가**까지 잠가둬야 나중에 카탈로그 가격이 바뀌어도 이미 확정된 행사가
 * 흔들리지 않는다 — 그래서 {@code currencyCode}/{@code snapshotSalePrice}/{@code snapshotTaxCode}가
 * 새로 추가됐다. {@code CeremonyService#calculateEstimatedTotal}이 이 스냅샷 값으로 플랜
 * 소계를 다시 계산한다.
 */
@Entity
@Table(name = "ceremony_plan_history_unit_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class CeremonyPlanHistoryUnitProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_plan_history_id", nullable = false)
    private CeremonyPlanHistory ceremonyPlanHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Column(name = "included_quantity", nullable = false)
    private Integer includedQuantity;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "snapshot_sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal snapshotSalePrice;

    @Column(name = "snapshot_tax_code", nullable = false, length = 50)
    private String snapshotTaxCode;

    /**
     * {@code currencyCode}/{@code snapshotSalePrice}/{@code snapshotTaxCode}는 호출부
     * ({@code CeremonyService#recordPlanHistory})가 그 순간 유효한 {@link UnitProductPricePeriod}를
     * {@code findEffective}로 조회해 넘긴다 — 이 엔티티는 DB 조회를 하지 않는다(옛
     * {@code CeremonyPlanHistory}와 같은 원칙).
     */
    @Builder
    private CeremonyPlanHistoryUnitProduct(
            CeremonyPlanHistory ceremonyPlanHistory,
            UnitProduct unitProduct,
            Integer includedQuantity,
            String currencyCode,
            BigDecimal snapshotSalePrice,
            String snapshotTaxCode
    ) {
        this.ceremonyPlanHistory = ceremonyPlanHistory;
        this.unitProduct = unitProduct;
        this.includedQuantity = includedQuantity;
        this.currencyCode = currencyCode;
        this.snapshotSalePrice = snapshotSalePrice;
        this.snapshotTaxCode = snapshotTaxCode;
    }
}
