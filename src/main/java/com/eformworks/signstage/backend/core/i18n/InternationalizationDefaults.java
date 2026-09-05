package com.eformworks.signstage.backend.core.i18n;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

/** 국제화 기본값과 외부 입력 검증을 한곳에서 관리한다. */
public final class InternationalizationDefaults {

    public static final String LANGUAGE_CODE = "ko";
    public static final String FORMAT_LOCALE = "ko-KR";
    public static final String TIME_ZONE_ID = "Asia/Seoul";
    public static final String CURRENCY_CODE = "KRW";
    public static final int CURRENCY_FRACTION_DIGITS = 0;
    public static final String CURRENCY_ROUNDING_MODE = "HALF_UP";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ko", "en");
    private static final Set<String> SUPPORTED_FORMAT_LOCALES = Set.of("ko-KR", "en-US");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("KRW", "USD", "EUR", "JPY");

    private InternationalizationDefaults() {
    }

    public static String languageCodeOrDefault(String value) {
        String candidate = value == null || value.isBlank() ? LANGUAGE_CODE : value;
        if (!SUPPORTED_LANGUAGES.contains(candidate)) {
            throw new IllegalArgumentException("Unsupported language code: " + candidate);
        }
        return candidate;
    }

    public static String formatLocaleOrDefault(String value) {
        String candidate = value == null || value.isBlank() ? FORMAT_LOCALE : value;
        if (!SUPPORTED_FORMAT_LOCALES.contains(candidate)
                || Locale.forLanguageTag(candidate).getLanguage().isBlank()) {
            throw new IllegalArgumentException("Unsupported format locale: " + candidate);
        }
        return candidate;
    }

    public static String timeZoneIdOrDefault(String value) {
        String candidate = value == null || value.isBlank() ? TIME_ZONE_ID : value;
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid IANA time zone: " + candidate, e);
        }
    }

    public static String currencyCodeOrDefault(String value) {
        String candidate = value == null || value.isBlank() ? CURRENCY_CODE : value.toUpperCase(Locale.ROOT);
        try {
            String currencyCode = Currency.getInstance(candidate).getCurrencyCode();
            if (!SUPPORTED_CURRENCIES.contains(currencyCode)) {
                throw new IllegalArgumentException("Unsupported currency code: " + currencyCode);
            }
            return currencyCode;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + candidate, e);
        }
    }
}
