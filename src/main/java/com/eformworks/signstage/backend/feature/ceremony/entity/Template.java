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

    @Builder
    private Template(
            Ceremony ceremony,
            String title,
            TemplateDocumentRole documentRole,
            String storageKey,
            String originalFilename,
            String storedFilename
    ) {
        this.ceremony = ceremony;
        this.title = title;
        this.documentRole = documentRole;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.status = TemplateStatus.DRAFT;
    }
}
