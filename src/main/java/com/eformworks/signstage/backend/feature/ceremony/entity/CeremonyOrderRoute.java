package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사의 수주 경로 — signstage-docs business/platform-order-partner-subcontract-review.md
 * D01(2026-11-12 결정: 플랫폼 수주 단독, 파트너 직접 수주 보류)·4장.
 *
 * <p>{@link #PARTNER_DIRECT}는 파트너가 고객과 직접 계약하고 플랫폼 이용료(플랜·구독·추가구매)를
 * 내는 지금까지의 경로다. 보류 중이라 파트너 화면·API가 꺼져 있다(D29). {@link #PLATFORM_ORDER}는
 * 플랫폼이 수주하고 파트너에 발주한 행사로, 발주 수락 시 플랫폼이 만든다 — 플랜은 제공 범위로만
 * 쓰고 파트너는 플랜·추가구매·고객 견적·현장지원 요청을 쓰지 않는다.
 */
public enum CeremonyOrderRoute {
    PARTNER_DIRECT,
    PLATFORM_ORDER
}
