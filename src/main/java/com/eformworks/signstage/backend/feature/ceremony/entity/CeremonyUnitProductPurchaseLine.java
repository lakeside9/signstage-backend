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

/**
 * {@link CeremonyUnitProductPurchase} 요청 한 줄 — 단위 상품 하나 + 수량. 승인/반려 상태는
 * 이 줄이 아니라 헤더({@code purchase.status})가 갖는다.
 *
 * <p>{@code purchased*} 필드는 전부 구매 시점 스냅샷이다(옛 두 구매 엔티티와 같은 원칙) —
 * 카탈로그 가격이 나중에 바뀌어도 이미 제출한 구매 내역은 바뀌지 않는다. **할인 필드가 없다**
 * (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
 * 2026-09-10, 3.5절) — 추가구매 라인은 항상 정가로 청구된다, 할인은 오직 {@code BillingPlan}
 * 소계에만 한 번 적용된다. 옛 {@code CapacityAddOn.secondaryUnitAmount}(묶음 상품의 보조
 * 수량) 필드도 없다 — 묶음은 폐지됐고(3.7절), 여러 종류를 같이 사고 싶으면 이 줄을 여러 개
 * 만들어 한 {@link CeremonyUnitProductPurchase}에 담으면 된다.
 */
@Entity
@Table(name = "ceremony_unit_product_purchase_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyUnitProductPurchaseLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private CeremonyUnitProductPurchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "purchased_name", nullable = false, length = 100)
    private String purchasedName;

    @Column(name = "purchased_sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasedSalePrice;

    @Column(name = "purchased_tax_code", nullable = false, length = 50)
    private String purchasedTaxCode;

    /**
     * {@code currencyCode}/{@code purchasedName}/{@code purchasedTaxCode}는 호출부
     * ({@code CeremonyService#purchaseUnitProducts})가 그 순간 유효한
     * {@link UnitProductPricePeriod}를 {@code findEffective}로 조회해 넘긴다 — 이 엔티티는
     * DB 조회를 하지 않는다.
     */
    @Builder
    private CeremonyUnitProductPurchaseLine(
            CeremonyUnitProductPurchase purchase,
            UnitProduct unitProduct,
            Integer quantity,
            String currencyCode,
            String purchasedName,
            BigDecimal purchasedSalePrice,
            String purchasedTaxCode
    ) {
        this.purchase = purchase;
        this.unitProduct = unitProduct;
        this.quantity = quantity;
        this.currencyCode = currencyCode;
        this.purchasedName = purchasedName;
        this.purchasedSalePrice = purchasedSalePrice;
        this.purchasedTaxCode = purchasedTaxCode;
    }
}
