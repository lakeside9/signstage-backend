package com.eformworks.signstage.backend.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternationalizationDefaultsTest {

    @Test
    void usesKoreanDefaults() {
        assertThat(InternationalizationDefaults.languageCodeOrDefault(null)).isEqualTo("ko");
        assertThat(InternationalizationDefaults.formatLocaleOrDefault(null)).isEqualTo("ko-KR");
        assertThat(InternationalizationDefaults.timeZoneIdOrDefault(null)).isEqualTo("Asia/Seoul");
        assertThat(InternationalizationDefaults.currencyCodeOrDefault(null)).isEqualTo("KRW");
    }

    @Test
    void rejectsUnknownLanguageAndInvalidTimeZone() {
        assertThatThrownBy(() -> InternationalizationDefaults.languageCodeOrDefault("xx"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternationalizationDefaults.timeZoneIdOrDefault("Seoul"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternationalizationDefaults.currencyCodeOrDefault("GBP"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
