package com.eformworks.signstage.backend.core.web;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;
    private final String traceId;

    private ApiResponse(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("SUCCESS", "성공", data, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data, String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }
}
