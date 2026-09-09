package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카탈로그 품목(플랜/선택옵션/용량 추가구매)의 가격정보 값 객체 —
 * currencyCode/supplyPrice/salePrice/discount(할인)/taxCode를 한데 묶는다. {@link BillingPlan}/
 * {@link OptionalFeature}/{@link CapacityAddOn}과 각각의 {@code *History}(변경 이력) 6곳에
 * 개별 필드로 반복되던 걸 하나로 모았다 — signstage-docs
 * business/billing-catalog-zero-base-schema-redesign-review.md 결정 #1(2026-09-08, 항목 A).
 *
 * <p>통화코드 정규화({@link InternationalizationDefaults#currencyCodeOrDefault})는 여기서
 * 한 번만 한다 — 이전에는 {@code BillingPlan}/{@code OptionalFeature}/{@code CapacityAddOn}
 * 세 엔티티의 생성자·{@code updateInfo}까지 총 6곳에서 같은 줄이 반복됐다. taxCode의 "생성 시
 * 기본값(KR_VAT_STANDARD) vs 수정 시 빈 값이면 기존 값 유지" 규칙은 생성/수정 시점에 따라
 * 달라(entity별로 지금 갖고 있는 값을 알아야 함) 여기서 흡수하지 않고, 호출부(각 엔티티의
 * 생성자/{@code updateInfo})가 여전히 계산해서 넘긴다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogPriceInfo {

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * 원가(내부 전용, 마진 계산용) — 계산식(할인·청구액)에는 전혀 관여하지 않는다. nullable이다
     * — "원가 미상"을 표현할 수 있어야 한다는 이유로 NOT NULL 제약을 풀었다(signstage-docs
     * business/billing-catalog-zero-base-schema-redesign-review.md 결정, 2026-09-08, 항목 G).
     */
    @Column(name = "supply_price", precision = 19, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Embedded
    private DiscountInfo discount;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    private CatalogPriceInfo(
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountInfo discount,
            String taxCode
    ) {
        // 하한 검증(음수 금지)을 이 생성자 한 곳에서 강제한다 — DiscountInfo의 할인값 검증과 같은
        // 원칙(signstage-docs business/billing-catalog-pricing-input-validation-review.md 3.1절,
        // 2026-09-09 구현). supplyPrice는 nullable("원가 미상")이라 null이면 검증을 건너뛴다.
        if (supplyPrice != null && supplyPrice.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
        }
        if (salePrice != null && salePrice.signum() < 0) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
        }
        this.currencyCode = InternationalizationDefaults.currencyCodeOrDefault(currencyCode);
        this.supplyPrice = supplyPrice;
        this.salePrice = salePrice;
        this.discount = discount;
        this.taxCode = taxCode;
    }

    /**
     * 카탈로그 엔티티(플랜/선택옵션/용량추가구매)의 생성자·{@code updateInfo}가 공통으로 쓰는
     * 팩토리 — 통화코드 정규화를 여기서 한 번만 한다. {@code taxCode}는 이미 각 엔티티가
     * 생성/수정 규칙(기본값 적용 또는 기존 값 유지)을 적용해 확정한 값을 그대로 받는다.
     */
    public static CatalogPriceInfo of(
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode
    ) {
        return new CatalogPriceInfo(currencyCode, supplyPrice, salePrice, new DiscountInfo(discountType, discountValue), taxCode);
    }
}
