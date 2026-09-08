package com.eformworks.signstage.backend.core.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class MoneyCalculatorTest {

    private final MoneyCalculator calculator = new MoneyCalculator();

    @Test
    void krwUsesZeroFractionDigits() {
        assertThat(calculator.calculateExclusiveTax(new BigDecimal("10005"), new BigDecimal("10"), CurrencyPolicy.krw()))
                .isEqualByComparingTo("1001");
    }

    @Test
    void decimalCurrencyKeepsConfiguredFractionDigits() {
        CurrencyPolicy usd = new CurrencyPolicy("USD", 2, RoundingMode.HALF_UP);

        assertThat(calculator.applyDiscount(new BigDecimal("12.34"), DiscountType.PERCENT, new BigDecimal("10"), usd))
                .isEqualByComparingTo("11.11");
    }

    @Test
    void discountCannotMakeAmountNegative() {
        assertThat(calculator.applyDiscount(new BigDecimal("1000"), DiscountType.FIXED_AMOUNT, new BigDecimal("2000"), CurrencyPolicy.krw()))
                .isEqualByComparingTo("0");
    }
}
