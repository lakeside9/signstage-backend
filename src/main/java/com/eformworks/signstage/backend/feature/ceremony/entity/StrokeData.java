package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 서명 획(펜 경로) 원본 데이터. 레거시는 {@code eventId}/{@code signerId}/
 * {@code templateFieldId}를 순수 컬럼(관계 아님)으로 뒀지만, 이 프로젝트는 지금까지 전부
 * 실제 FK 관계를 써왔고 별도로 끊어야 한다는 결정이 없어 FK 관계로 만든다.
 */
@Entity
@Table(name = "stroke_data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrokeData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_event_id", nullable = false)
    private CeremonyEvent ceremonyEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id", nullable = false)
    private Signer signer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_field_id", nullable = false)
    private TemplateField templateField;

    @Column(name = "stroke_seq", nullable = false)
    private Integer strokeSeq;

    // @Lob를 안 쓴다 — Hibernate가 length 미지정 CLOB을 TINYTEXT로 기대해 스키마 검증에 실패한다.
    // columnDefinition으로 실제 DB 컬럼 타입(LONGTEXT)을 명시한다.
    @Column(name = "raw_data", nullable = false, columnDefinition = "LONGTEXT")
    private String rawData;

    @Builder
    private StrokeData(CeremonyEvent ceremonyEvent, Signer signer, TemplateField templateField, Integer strokeSeq, String rawData) {
        this.ceremonyEvent = ceremonyEvent;
        this.signer = signer;
        this.templateField = templateField;
        this.strokeSeq = strokeSeq;
        this.rawData = rawData;
    }
}
