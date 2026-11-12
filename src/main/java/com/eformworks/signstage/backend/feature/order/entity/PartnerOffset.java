package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너 귀책 상계 — signstage-docs business/platform-order-partner-subcontract-review.md D13(2026-11-12 결정).
 * 고객 환불 중 파트너 귀책분처럼 파트너에게 돌려받을 금액을 발주에 묶어 등록한다. 다음 정산에서
 * 지급액에서 빼고(APPLIED), 그 달 지급액을 넘는 만큼은 이월 상계({@code carriedOverFromPayout})로 남겨
 * 다음 정산에서 뺀다. 등록·취소는 원장에 남기고, 이월은 같은 채무를 옮기는 것이라 원장에 남기지 않는다.
 */
@Entity
@Table(name = "partner_offsets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerOffset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id", nullable = false)
    private PartnerOrder partnerOrder;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** 상계 금액(공급가, 양수). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerOffsetStatus status;

    /** 상계를 뺀 지급 건. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_payout_id")
    private PartnerPayout partnerPayout;

    /** 이월 상계면, 지급액을 넘어 이월을 만든 지급 건. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carried_over_from_payout_id")
    private PartnerPayout carriedOverFromPayout;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private PartnerOffset(
            PartnerOrder partnerOrder, BigDecimal amount, String reason, LocalDate occurredOn,
            PartnerPayout carriedOverFromPayout
    ) {
        this.organization = partnerOrder.getOrganization();
        this.partnerOrder = partnerOrder;
        this.currencyCode = partnerOrder.getCurrencyCode();
        this.amount = amount;
        this.reason = reason;
        this.occurredOn = occurredOn;
        this.carriedOverFromPayout = carriedOverFromPayout;
        this.status = PartnerOffsetStatus.PENDING;
    }

    /** 지급액을 넘은 상계를 다음 정산으로 옮긴다. 원래 상계의 발주·사유를 따른다. */
    public PartnerOffset carryOver(BigDecimal remaining, PartnerPayout fromPayout, LocalDate occurredOn) {
        return PartnerOffset.builder()
                .partnerOrder(partnerOrder)
                .amount(remaining)
                .reason("이월: " + (reason.startsWith("이월: ") ? reason.substring(4) : reason))
                .occurredOn(occurredOn)
                .carriedOverFromPayout(fromPayout)
                .build();
    }

    public boolean isPending() {
        return status == PartnerOffsetStatus.PENDING;
    }

    public boolean isCarryOver() {
        return carriedOverFromPayout != null;
    }

    public void applyTo(PartnerPayout payout) {
        this.status = PartnerOffsetStatus.APPLIED;
        this.partnerPayout = payout;
    }

    /** 지급 건을 취소해 상계를 다시 대기로 돌린다. */
    public void release() {
        this.status = PartnerOffsetStatus.PENDING;
        this.partnerPayout = null;
    }

    public void cancel(String reason) {
        this.status = PartnerOffsetStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }
}
