package com.eformworks.signstage.backend.core.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageTranslator {

    private final MessageSource messageSource;

    public String translate(String messageKey, Map<String, Object> messageArgs, String fallback) {
        Locale locale = supportedLocale(LocaleContextHolder.getLocale());
        try {
            String message = messageSource.getMessage(messageKey, null, locale);
            if (messageArgs == null) {
                return message;
            }
            for (Map.Entry<String, Object> entry : messageArgs.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            return message;
        } catch (NoSuchMessageException ignored) {
            return fallback;
        }
    }

    private Locale supportedLocale(Locale locale) {
        return "en".equalsIgnoreCase(locale.getLanguage()) ? Locale.ENGLISH : Locale.KOREAN;
    }
}
