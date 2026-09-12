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
 * 행사별 1:1 문의(파트너 ↔ 플랫폼 관리자) 헤더 한 건 — signstage-docs
 * business/partner-support-center-review.md 5장. {@link Signer}/{@link Template}처럼
 * {@code Ceremony} 직속이다. 실제 대화 내용은 {@link CeremonyInquiryMessage}에 쌓인다 —
 * "헤더 하나 + 하위 행 여러 개"는 {@code CeremonyUnitProductPurchase}(구매)와 모양이 같지만,
 * 구매는 한 번에 통째로 승인/반려되는 반면 문의는 계속 늘어나는 대화라 하위 테이블 이름도
 * "줄"이 아니라 "메시지"다.
 */
@Entity
@Table(name = "ceremony_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyInquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    /** 목록 정렬·"답변 대기" 판단용 — 메시지가 추가될 때마다 갱신한다. */
    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Builder
    private CeremonyInquiry(Ceremony ceremony, String title, LocalDateTime lastMessageAt) {
        this.ceremony = ceremony;
        this.title = title;
        this.status = InquiryStatus.OPEN;
        this.lastMessageAt = lastMessageAt;
    }

    /**
     * 메시지가 추가될 때 서비스가 호출한다 — 파트너가 쓰면 OPEN, 관리자가 쓰면 ANSWERED로
     * 자동 전이한다(사람이 상태를 직접 고르지 않는다, 5.2절). CLOSED 상태에서는 서비스가 이
     * 메서드를 호출하기 전에 이미 막는다({@code checkNotClosed}).
     */
    public void applyMessage(InquirySenderType senderType, LocalDateTime messageCreatedAt) {
        this.status = senderType == InquirySenderType.PARTNER ? InquiryStatus.OPEN : InquiryStatus.ANSWERED;
        this.lastMessageAt = messageCreatedAt;
    }

    /** 종료 — 재오픈하지 않는다(9장 결정 #5). 양쪽 누구든 호출할 수 있다. */
    public void close() {
        this.status = InquiryStatus.CLOSED;
    }
}
