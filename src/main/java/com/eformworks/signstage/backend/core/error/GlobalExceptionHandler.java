package com.eformworks.signstage.backend.core.error;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.i18n.MessageTranslator;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TraceIdProvider traceIdProvider;
    private final MessageTranslator messageTranslator;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException e) {
        ErrorCode errorCode = e.getErrorCode();
        String fallback = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage() : errorCode.getMessage();
        String message = messageTranslator.translate(errorCode.getMessageKey(), e.getMessageArgs(), fallback);
        log.error("ApplicationException: code={}, message={}", errorCode.getCode(), message, e);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, message, e.getMessageArgs(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException", e);
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;

        List<ApiResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> {
                    String validationCode = fieldError.getCode() == null ? "invalid" : fieldError.getCode().toLowerCase();
                    String messageKey = "validation." + validationCode;
                    Map<String, Object> args = Map.of("field", fieldError.getField());
                    String message = messageTranslator.translate(messageKey, args, errorCode.getMessage());
                    return new ApiResponse.FieldError(
                            fieldError.getField(), validationCode.toUpperCase(Locale.ROOT), messageKey, args, message);
                })
                .toList();
        String message = translate(errorCode);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, message, Map.of(), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException: parameter={}, value={}", e.getName(), e.getValue());
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("HttpRequestMethodNotSupportedException", e);
        ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.error("AccessDeniedException", e);
        ErrorCode errorCode = CommonErrorCode.ACCESS_DENIED;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid internationalization or domain value: {}", e.getMessage());
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    /**
     * DB 제약(유니크·외래키 등) 위반으로 저장이 실패했을 때 잡는다 — 이 핸들러가 없으면
     * {@link #handleException}으로 떨어져 500 "서버 오류가 발생했습니다."로 뭉뚱그려진다
     * (2026-09-10, {@code billing_plan_unit_products.uq_bpup_plan_product} 위반 사례로 발견).
     * 근본 원인(어떤 제약을 왜 위반했는지)은 로그의 {@code getMostSpecificCause()}로 남기고,
     * 클라이언트에는 어떤 제약인지 노출하지 않는 일반적인 충돌 메시지만 돌려준다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("DataIntegrityViolationException: {}", e.getMostSpecificCause().getMessage(), e);
        ErrorCode errorCode = CommonErrorCode.DATA_CONFLICT;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Internal Server Error", e);
        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(errorResponse(errorCode, translate(errorCode), Map.of(), List.of()));
    }

    private String translate(ErrorCode errorCode) {
        return messageTranslator.translate(errorCode.getMessageKey(), Map.of(), errorCode.getMessage());
    }

    private ApiResponse<Void> errorResponse(
            ErrorCode errorCode,
            String message,
            Map<String, Object> messageArgs,
            List<ApiResponse.FieldError> fieldErrors
    ) {
        return ApiResponse.error(
                errorCode.getCode(), message, errorCode.getMessageKey(), messageArgs, fieldErrors,
                traceIdProvider.getTraceId());
    }
}
