package com.eformworks.signstage.backend.core.error;

import lombok.Getter;

import java.util.Map;

@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> messageArgs;

    public ApplicationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.messageArgs = Map.of();
    }

    public ApplicationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.messageArgs = Map.of();
    }

    public ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.messageArgs = Map.of();
    }

    public ApplicationException(ErrorCode errorCode, Map<String, Object> messageArgs) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.messageArgs = messageArgs == null ? Map.of() : Map.copyOf(messageArgs);
    }
}
