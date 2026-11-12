package com.eformworks.signstage.backend.feature.order.entity;

/**
 * 고객 수락 경로 — D02 권장안: 고객은 로그인 없이 견적 링크로 수락한다. 메일·전화로 받은 수락은
 * 플랫폼이 대신 기록한다.
 */
public enum SalesOrderAcceptanceChannel {
    CUSTOMER_LINK,
    PLATFORM_RECORDED
}
