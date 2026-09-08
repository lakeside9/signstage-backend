package com.eformworks.signstage.backend.core.money;

import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import java.math.RoundingMode;

/** 거래 시점에 스냅샷할 통화 정규화 규칙. */
public record CurrencyPolicy(String currencyCode, int fractionDigits, RoundingMode roundingMode) {

    public static CurrencyPolicy krw() {
        return new CurrencyPolicy(
                InternationalizationDefaults.CURRENCY_CODE,
                InternationalizationDefaults.CURRENCY_FRACTION_DIGITS,
                RoundingMode.valueOf(InternationalizationDefaults.CURRENCY_ROUNDING_MODE)
        );
    }

    public CurrencyPolicy {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }
        if (fractionDigits < 0 || fractionDigits > 4) {
            throw new IllegalArgumentException("fractionDigits must be between 0 and 4");
        }
        if (roundingMode == null) {
            throw new IllegalArgumentException("roundingMode is required");
        }
    }
}
