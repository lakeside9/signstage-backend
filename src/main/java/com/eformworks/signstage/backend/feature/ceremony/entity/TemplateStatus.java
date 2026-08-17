package com.eformworks.signstage.backend.feature.ceremony.entity;

/**
 * Template 라이프사이클. 이번 라운드는 업로드된 문서를 항상 DRAFT로 두고, COMPLETED로의
 * 전환 API는 아직 없다(CeremonyTemplate 매핑이 생기는 다음 라운드에 추가).
 */
public enum TemplateStatus {
    DRAFT,
    COMPLETED
}
