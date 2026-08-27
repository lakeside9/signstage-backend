package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문서 양식(PDF). "행사마다 문서가 다르다"는 이유로 조직 표준 문서가 아니라 Ceremony 직속이다
 * (signstage-docs business/ceremony-feature-migration-review.md 4.2절 결정). 실제 파일은
 * {@code storageKey}로 {@link com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort}를
 * 통해 저장/조회한다.
 */
@Entity
@Table(name = "templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_role", nullable = false, length = 20)
    private TemplateDocumentRole documentRole;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateStatus status;

    /**
     * 문서 양식 목록 화면의 표시 순서(2026-08-27 legacy 포팅) — 위/아래 이동 버튼이 전체 목록을
     * 다시 인덱싱해 저장한다({@code TemplateService#updateTemplateDisplayOrders}). 새로 업로드/
     * 복제되는 문서는 그 시점의 형제 수를 그대로 받아 항상 목록 맨 끝에 붙는다.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder
    private Template(
            Ceremony ceremony,
            String title,
            TemplateDocumentRole documentRole,
            String storageKey,
            String originalFilename,
            String storedFilename,
            Integer displayOrder
    ) {
        this.ceremony = ceremony;
        this.title = title;
        this.documentRole = documentRole;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.status = TemplateStatus.DRAFT;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    /**
     * 문서 양식 수정 화면에서 제목/문서유형만 바꿀 때 쓴다. PDF 파일 자체는 여기서 바꾸지
     * 않는다 — 이미 찍어둔 서명란(TemplateField) 좌표가 파일이 바뀌면 깨지기 때문이다.
     */
    public void updateInfo(String title, TemplateDocumentRole documentRole) {
        this.title = title;
        this.documentRole = documentRole;
    }

    /**
     * 서명란 배치 화면의 "설정 완료" — 이후로는 서명란을 더 이상 바꿀 수 없다(TemplateService/
     * TemplateFieldService가 COMPLETED 상태를 확인해 막는다). 되돌리는 API는 없다.
     */
    public void complete() {
        this.status = TemplateStatus.COMPLETED;
    }

    /** 문서 양식 목록의 위/아래 이동 버튼이 호출한다 — {@code null}이면 바꾸지 않는다. */
    public void updateDisplayOrder(Integer displayOrder) {
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}
