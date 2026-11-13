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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객 취소 보상 — signstage-docs business/platform-order-pending-items.md C04(2026-11-13). 파트너가 수락한 뒤 고객
 * 사정으로 수주가 취소되면 발주 금액 × 취소 시점별 보상 비율을 파트너에게 준다. 생길 때 원장에 +로 남기고,
 * 다음 월 정산에서 검수 지급 대상과 함께 지급 건에 더한다.
 */
@Entity
@Table(name = "partner_compensations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerCompensation extends BaseEntity {

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

    /** 보상 금액(공급가, 양수). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** 적용한 보상 비율(%). */
    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerCompensationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_payout_id")
    private PartnerPayout partnerPayout;

    @Builder
    private PartnerCompensation(PartnerOrder partnerOrder, BigDecimal amount, BigDecimal ratePercent, String reason, LocalDate occurredOn) {
        this.organization = partnerOrder.getOrganization();
        this.partnerOrder = partnerOrder;
        this.currencyCode = partnerOrder.getCurrencyCode();
        this.amount = amount;
        this.ratePercent = ratePercent;
        this.reason = reason;
        this.occurredOn = occurredOn;
        this.status = PartnerCompensationStatus.PENDING;
    }

    public void applyTo(PartnerPayout payout) {
        this.status = PartnerCompensationStatus.APPLIED;
        this.partnerPayout = payout;
    }

    /** 지급 건을 취소해 보상을 다시 대기로 돌린다. */
    public void release() {
        this.status = PartnerCompensationStatus.PENDING;
        this.partnerPayout = null;
    }
}
