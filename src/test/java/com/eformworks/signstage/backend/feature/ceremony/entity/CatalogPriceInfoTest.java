package com.eformworks.signstage.backend.feature.ceremony.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CatalogPriceInfo} 하한 검증 단위 테스트 — signstage-docs
 * business/billing-catalog-pricing-input-validation-review.md 3.1절(2026-09-09 구현). 할인값
 * (음수/PERCENT 100 초과) 검증은 이미 {@link DiscountInfo}가 담당하고 있어(같은 3.1절, 실제로는
 * organization-discount-override-security-and-validity-period-review.md 결정 #1로 먼저
 * 구현됨) 여기서는 이번에 새로 추가한 supplyPrice/salePrice 하한 검증만 다룬다.
 */
class CatalogPriceInfoTest {

    private static final DiscountType DISCOUNT_TYPE = DiscountType.PERCENT;
    private static final BigDecimal DISCOUNT_VALUE = BigDecimal.TEN;

    @Test
    @DisplayName("음수 판매가는 거부된다")
    void of_negativeSalePrice_rejected() {
        assertThatThrownBy(() -> CatalogPriceInfo.of(
                "KRW", new BigDecimal("1000"), new BigDecimal("-1"), DISCOUNT_TYPE, DISCOUNT_VALUE, "KR_VAT_STANDARD"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
    }

    @Test
    @DisplayName("음수 공급가는 거부된다")
    void of_negativeSupplyPrice_rejected() {
        assertThatThrownBy(() -> CatalogPriceInfo.of(
                "KRW", new BigDecimal("-1"), new BigDecimal("1000"), DISCOUNT_TYPE, DISCOUNT_VALUE, "KR_VAT_STANDARD"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
    }

    @Test
    @DisplayName("공급가는 nullable이라 null이면 하한 검증을 건너뛴다(\"원가 미상\")")
    void of_nullSupplyPrice_skipsValidation() {
        CatalogPriceInfo priceInfo = CatalogPriceInfo.of(
                "KRW", null, new BigDecimal("1000"), DISCOUNT_TYPE, DISCOUNT_VALUE, "KR_VAT_STANDARD"
        );

        assertThat(priceInfo.getSupplyPrice()).isNull();
        assertThat(priceInfo.getSalePrice()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("0원과 양수 판매가/공급가는 그대로 통과한다")
    void of_zeroOrPositivePrices_accepted() {
        CatalogPriceInfo priceInfo = CatalogPriceInfo.of(
                "KRW", BigDecimal.ZERO, BigDecimal.ZERO, DISCOUNT_TYPE, DISCOUNT_VALUE, "KR_VAT_STANDARD"
        );

        assertThat(priceInfo.getSupplyPrice()).isEqualByComparingTo("0");
        assertThat(priceInfo.getSalePrice()).isEqualByComparingTo("0");
    }
}
