package com.eformworks.signstage.backend.feature.identity.entity;

public enum LoginHistoryStatus {
    SUCCESS,
    FAILED_NOT_FOUND,
    FAILED_INVALID_PASSWORD,
    FAILED_LOCKED,
    FAILED_PENDING_APPROVAL,
    FAILED_DISABLED,
    FAILED_WITHDRAWN
}
