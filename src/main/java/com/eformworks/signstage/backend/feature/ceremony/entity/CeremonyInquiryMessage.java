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
 * 행사별 1:1 문의의 메시지 한 건 — signstage-docs
 * business/partner-support-center-review.md 5.1절. 실제 작성자는
 * {@link BaseEntity#getCreatedBy()}가 이미 기록한다 — {@link #senderType}은 "어느 편인지"만
 * 구분한다. 파일 첨부는 v1 범위 밖(같은 문서 6장) — 필요해지면 storageKey류 컬럼을 추가한다.
 */
@Entity
@Table(name = "ceremony_inquiry_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyInquiryMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private CeremonyInquiry inquiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private InquirySenderType senderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private CeremonyInquiryMessage(CeremonyInquiry inquiry, InquirySenderType senderType, String content) {
        this.inquiry = inquiry;
        this.senderType = senderType;
        this.content = content;
    }
}
