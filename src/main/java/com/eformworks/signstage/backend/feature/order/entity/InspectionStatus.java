package com.eformworks.signstage.backend.feature.order.entity;

/** 검수 상태 — 행사 완료 후 PENDING으로 만들고, 플랫폼 승인 또는 기한 경과(승인 간주)로 APPROVED가 된다. */
public enum InspectionStatus {
    PENDING,
    APPROVED
}
