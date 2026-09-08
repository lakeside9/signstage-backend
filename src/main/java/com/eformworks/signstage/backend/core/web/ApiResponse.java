package com.eformworks.signstage.backend.core.web;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final String messageKey;
    private final Map<String, Object> messageArgs;
    private final List<FieldError> fieldErrors;
    private final T data;
    private final String traceId;

    private ApiResponse(
            String code,
            String message,
            String messageKey,
            Map<String, Object> messageArgs,
            List<FieldError> fieldErrors,
            T data,
            String traceId
    ) {
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? Map.of() : Map.copyOf(messageArgs);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("SUCCESS", "성공", "common.success", Map.of(), List.of(), data, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, Map.of(), List.of(), null, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data, String traceId) {
        return new ApiResponse<>(code, message, null, Map.of(), List.of(), data, traceId);
    }

    public static <T> ApiResponse<T> error(
            String code,
            String message,
            String messageKey,
            Map<String, Object> messageArgs,
            List<FieldError> fieldErrors,
            String traceId
    ) {
        return new ApiResponse<>(code, message, messageKey, messageArgs, fieldErrors, null, traceId);
    }

    public record FieldError(
            String field,
            String code,
            String messageKey,
            Map<String, Object> messageArgs,
            String message
    ) {
        public FieldError {
            messageArgs = messageArgs == null ? Map.of() : Map.copyOf(messageArgs);
        }
    }
}
