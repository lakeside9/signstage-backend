package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단위 상품({@link UnitProduct})의 가격정보 값 객체 — 옛 카탈로그 가격정보({@code CatalogPriceInfo},
 * 지금은 삭제됨)와 거의 같지만 {@code discount}(할인)가 없다. signstage-docs
 * business/billing-catalog-unit-product-model-redesign-review.md 결정(2026-09-10, 3.5절) —
 * "단위 상품은 할인을 갖지 않는다. 할인이라는 개념은 오직 {@code BillingPlan}에만 존재한다."
 * 플랜 소계(포함 단위 상품 판매가 합)에 플랜 자체 할인을 한 번만 적용하고, 추가구매 라인은
 * 정가 그대로 청구한다 — 예전에 {@code OptionalFeature}/{@code CapacityAddOn}가 각자
 * {@code discountType}/{@code discountValue}를 갖던 것과 다른 지점이다.
 *
 * <p>하한 검증(음수 금지)은 옛 {@code CatalogPriceInfo}와 같은 원칙으로 이 생성자 한 곳에서
 * 강제한다(signstage-docs business/billing-catalog-pricing-input-validation-review.md 3.1절).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPriceInfo {

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** 원가(내부 전용, 마진 계산용) — 계산식에는 관여하지 않는다. nullable("원가 미상"). */
    @Column(name = "supply_price", precision = 19, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    private ProductPriceInfo(String currencyCode, BigDecimal supplyPrice, BigDecimal salePrice, String taxCode) {
        if (supplyPrice != null && supplyPrice.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
        }
        if (salePrice != null && salePrice.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
        }
        this.currencyCode = InternationalizationDefaults.currencyCodeOrDefault(currencyCode);
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.taxCode = taxCode;
    }

    public static ProductPriceInfo of(String currencyCode, BigDecimal supplyPrice, BigDecimal salePrice, String taxCode) {
        return new ProductPriceInfo(currencyCode, supplyPrice, salePrice, taxCode);
    }
}
