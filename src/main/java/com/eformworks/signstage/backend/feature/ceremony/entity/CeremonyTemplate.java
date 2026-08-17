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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Template ↔ CeremonyEvent 매핑. {@code documentRole}은 매핑 시점에 지정하는 값으로
 * {@link Template#getDocumentRole()}과 별개다(레거시 원본 모델을 그대로 이식). READY 전이
 * 조건(CONTRACT/EXHIBITION 각 1개 이상 매핑)은 이 엔티티 기준으로 판정한다.
 */
@Entity
@Table(
        name = "ceremony_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cet_event_template",
                columnNames = {"ceremony_event_id", "template_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_event_id", nullable = false)
    private CeremonyEvent ceremonyEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_role", nullable = false, length = 20)
    private TemplateDocumentRole documentRole;

    @Builder
    private CeremonyTemplate(CeremonyEvent ceremonyEvent, Template template, TemplateDocumentRole documentRole) {
        this.ceremonyEvent = ceremonyEvent;
        this.template = template;
        this.documentRole = documentRole;
    }
}
