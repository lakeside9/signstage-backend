package com.eformworks.signstage.backend.core.i18n;

import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTranslatorTest {

    private final MessageTranslator translator = new MessageTranslator(messageSource());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void translatesErrorCodeUsingAcceptLanguageLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(CommonErrorCode.ACCESS_DENIED.getMessageKey()).isEqualTo("error.access.denied");
        assertThat(translator.translate(
                CommonErrorCode.ACCESS_DENIED.getMessageKey(), Map.of(), CommonErrorCode.ACCESS_DENIED.getMessage()))
                .isEqualTo("You do not have permission to perform this action.");
    }

    @Test
    void substitutesNamedArgumentsAndFallsBackToKoreanForUnsupportedLocale() {
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.translate("validation.notblank", Map.of("field", "email"), "fallback"))
                .isEqualTo("email 항목은 필수입니다.");
    }

    /**
     * 메뉴 label_key(예: navigation.dashboard)도 messages.properties에 있어야 한다 —
     * 빠지면 MenuService가 번역 대신 키 문자열 자체를 그대로 내려줘 화면에 "navigation.dashboard"가
     * 노출되는 결함이 났었다(2026-09-05 발견·수정).
     */
    @Test
    void translatesMenuNavigationLabelKeys() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(translator.translate("navigation.dashboard", Map.of(), "navigation.dashboard"))
                .isEqualTo("대시보드");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translator.translate("navigation.dashboard", Map.of(), "navigation.dashboard"))
                .isEqualTo("Dashboard");
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
