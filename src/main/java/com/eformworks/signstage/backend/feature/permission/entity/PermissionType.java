package com.eformworks.signstage.backend.feature.permission.entity;

/**
 * {@link PermissionDefinition}이 지키는 대상의 종류. MENU는 {@link Menu} 행 하나와 1:1로 짝지어
 * 사이드바 노출 여부를 결정하고, ACTION은 메뉴 트리에 속하지 않는 화면 안 개별 버튼/행위를
 * 결정한다 — signstage-docs business/menu-and-action-permission-management-review.md 8장.
 */
public enum PermissionType {
    MENU,
    ACTION
}
