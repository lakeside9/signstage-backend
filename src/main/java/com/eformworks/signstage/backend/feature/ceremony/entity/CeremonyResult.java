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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서명이 그려진 행사 결과 PDF. 원본 {@link Template}에 {@link StrokeData}를 겹쳐 그려
 * 만든다({@code feature.ceremony.support.SignatureOverlayRenderer}). 생성은 이벤트당
 * 결과물 종류별로 1회다(재생성 없음, 이번 라운드 범위).
 */
@Entity
@Table(name = "ceremony_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyResult extends BaseEntity {

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
    @Column(name = "result_type", nullable = false, length = 20)
    private CeremonyResultType resultType;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    @Column(name = "verification_at")
    private LocalDateTime verificationAt;

    @Builder
    private CeremonyResult(
            CeremonyEvent ceremonyEvent,
            Template template,
            CeremonyResultType resultType,
            String storageKey,
            String originalFilename,
            String storedFilename,
            Long fileSize,
            String checksum
    ) {
        this.ceremonyEvent = ceremonyEvent;
        this.template = template;
        this.resultType = resultType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.isVerified = false;
    }

    /**
     * 위변조 검증 성공 시 호출한다. "몇 번 검증됐는지"가 아니라 "마지막으로 언제 검증됐는지"만
     * 남긴다(재검증 포함, 매번 최신 시각으로 갱신).
     */
    public void markVerified() {
        this.isVerified = true;
        this.verificationAt = LocalDateTime.now();
    }
}
