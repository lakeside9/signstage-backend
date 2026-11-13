package com.eformworks.signstage.backend.feature.support.entity;

/**
 * FAQ·공지사항을 보여줄 대상(2026-11-13). 파트너용은 로그인한 조직 콘솔의 FAQ·공지사항 메뉴에, 고객용은 로그인 없이 보는
 * 공개 화면(홈의 자주 묻는 질문, 공지사항 페이지)에 나온다.
 */
public enum SupportAudience {
    PARTNER,
    CUSTOMER
}
