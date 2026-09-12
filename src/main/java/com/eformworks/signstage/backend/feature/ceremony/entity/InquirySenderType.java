package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * 행사별 1:1 문의 메시지의 작성 편 — signstage-docs
 * business/partner-support-center-review.md 5.1절. 실제 작성자 사용자 id는
 * {@code BaseEntity#createdBy}가 이미 기록하지만, 파트너 쪽은 조직 멤버 여러 명이 같은
 * 스레드에 쓸 수 있어 "어느 편인지"를 화면에서 매번 역추적하지 않도록 이 필드로 고정한다.
 */
public enum InquirySenderType {
    PARTNER,
    PLATFORM_ADMIN
}
