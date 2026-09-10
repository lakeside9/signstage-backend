package com.eformworks.signstage.backend.feature.ceremony.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ProductPriceInfo} 하한 검증 단위 테스트 — signstage-docs
 * business/billing-catalog-pricing-input-validation-review.md 3.1절(2026-09-09 구현)의
 * supplyPrice/salePrice 하한 검증을 옛 {@code CatalogPriceInfo}에서 {@link ProductPriceInfo}로
 * 옮겨왔다(signstage-docs business/billing-catalog-unit-product-model-redesign-review.md
 * 결정, 2026-09-10) — 단위 상품은 할인을 갖지 않으므로 이 클래스엔 discountType/discountValue
 * 인자가 없다.
 */
class ProductPriceInfoTest {

    @Test
    @DisplayName("음수 판매가는 거부된다")
    void of_negativeSalePrice_rejected() {
        assertThatThrownBy(() -> ProductPriceInfo.of("KRW", new BigDecimal("1000"), new BigDecimal("-1"), "KR_VAT_STANDARD"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
    }

    @Test
    @DisplayName("음수 공급가는 거부된다")
    void of_negativeSupplyPrice_rejected() {
        assertThatThrownBy(() -> ProductPriceInfo.of("KRW", new BigDecimal("-1"), new BigDecimal("1000"), "KR_VAT_STANDARD"))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CATALOG_PRICE_VALUE_INVALID);
    }

    @Test
    @DisplayName("공급가는 nullable이라 null이면 하한 검증을 건너뛴다(\"원가 미상\")")
    void of_nullSupplyPrice_skipsValidation() {
        ProductPriceInfo priceInfo = ProductPriceInfo.of("KRW", null, new BigDecimal("1000"), "KR_VAT_STANDARD");

        assertThat(priceInfo.getSupplyPrice()).isNull();
        assertThat(priceInfo.getSalePrice()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("0원과 양수 판매가/공급가는 그대로 통과한다")
    void of_zeroOrPositivePrices_accepted() {
        ProductPriceInfo priceInfo = ProductPriceInfo.of("KRW", BigDecimal.ZERO, BigDecimal.ZERO, "KR_VAT_STANDARD");

        assertThat(priceInfo.getSupplyPrice()).isEqualByComparingTo("0");
        assertThat(priceInfo.getSalePrice()).isEqualByComparingTo("0");
    }
}
