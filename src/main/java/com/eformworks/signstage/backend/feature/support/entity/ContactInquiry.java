package com.eformworks.signstage.backend.feature.support.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객 문의 한 건(2026-11-13) — 로그인 없이 공개 화면에서 접수한다. 답변은 시스템 밖(전화·메일)에서 하고,
 * 플랫폼 담당자는 처리 상태와 내부 메모만 남긴다. 접수 IP는 같은 곳에서 반복 접수하는 스팸을 가려내려고 둔다.
 */
@Entity
@Table(name = "contact_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactInquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 30)
    private ContactInquiryType inquiryType;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "expected_signers")
    private Integer expectedSigners;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "privacy_consented_at", nullable = false)
    private LocalDateTime privacyConsentedAt;

    @Column(name = "requester_ip", length = 45)
    private String requesterIp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactInquiryStatus status;

    @Column(name = "admin_memo", length = 1000)
    private String adminMemo;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Builder
    private ContactInquiry(
            ContactInquiryType inquiryType,
            String companyName,
            String contactName,
            String email,
            String phone,
            LocalDate eventDate,
            Integer expectedSigners,
            String message,
            LocalDateTime privacyConsentedAt,
            String requesterIp
    ) {
        this.inquiryType = inquiryType;
        this.companyName = companyName;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.eventDate = eventDate;
        this.expectedSigners = expectedSigners;
        this.message = message;
        this.privacyConsentedAt = privacyConsentedAt;
        this.requesterIp = requesterIp;
        this.status = ContactInquiryStatus.RECEIVED;
    }

    /** 담당자가 처리 상태와 내부 메모를 기록한다. */
    public void handle(ContactInquiryStatus status, String adminMemo, Long adminUserId, LocalDateTime now) {
        this.status = status;
        this.adminMemo = adminMemo;
        this.handledBy = adminUserId;
        this.handledAt = now;
    }
}
