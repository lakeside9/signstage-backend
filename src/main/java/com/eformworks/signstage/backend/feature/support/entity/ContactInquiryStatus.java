package com.eformworks.signstage.backend.feature.support.entity;

/** 고객 문의 처리 상태 — 답변은 시스템 밖에서 하므로 담당자가 진행 상황만 기록한다. */
public enum ContactInquiryStatus {
    /** 접수 */
    RECEIVED,
    /** 연락·상담 중 */
    IN_PROGRESS,
    /** 처리 완료 */
    DONE
}
