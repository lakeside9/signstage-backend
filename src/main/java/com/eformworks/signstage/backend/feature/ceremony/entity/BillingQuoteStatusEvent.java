package com.eformworks.signstage.backend.feature.ceremony.entity;

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
 * {@link BillingQuote} 상태 전이 이력 — signstage-docs
 * business/currency-tax-internationalization-review.md 9장. append-only다(update/delete
 * 경로 없음). 확정(FINALIZED) 시점에 한 행, 무효화 시 VOID 이벤트 한 행이 추가로 쌓인다 —
 * "현재 상태"는 항상 가장 최근 이벤트로 판정하고, {@link BillingQuote} 행 자체에는 상태
 * 컬럼을 두지 않는다({@code created_by}가 확정 행위자, 이 테이블의 {@code actorId}가 각
 * 이벤트(확정/무효화)의 행위자 — 둘이 다를 수 있다).
 */
@Entity
@Table(name = "billing_quote_status_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingQuoteStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_quote_id", nullable = false)
    private BillingQuote billingQuote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingQuoteStatus status;

    /** VOID 이벤트에서만 채운다 — FINALIZED 이벤트는 항상 null. */
    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    private BillingQuoteStatusEvent(BillingQuote billingQuote, BillingQuoteStatus status, String reason, Long actorId) {
        this.billingQuote = billingQuote;
        this.status = status;
        this.reason = reason;
        this.actorId = actorId;
        this.occurredAt = LocalDateTime.now();
    }
}
