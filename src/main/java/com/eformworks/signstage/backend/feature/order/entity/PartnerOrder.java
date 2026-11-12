package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너 발주 — signstage-docs business/platform-order-partner-subcontract-review.md 3장 4~5단계,
 * D04(플랫폼 지정 → 파트너 수락·거절, 분할 발주 없음), D05·D28(단가표 자동 계산 + 사유 있는 조정).
 * 발급할 때 단가와 금액을 스냅샷해 발주서 금액을 고정한다. 수주 하나에 응답 대기·수락 발주는
 * 하나뿐이고, 거절·만료·취소되면 다른 파트너에 다시 발급한다.
 *
 * <p>수락하면 플랫폼 수주 행사를 만들어 {@code ceremony}에 연결한다(D03 안 A).
 */
@Entity
@Table(name = "partner_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "base_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseFee;

    @Column(name = "tablet_count", nullable = false)
    private int tabletCount;

    @Column(name = "tablet_unit_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal tabletUnitRate;

    @Column(name = "onsite_staff_person_days", nullable = false)
    private int onsiteStaffPersonDays;

    @Column(name = "onsite_staff_day_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal onsiteStaffDayRate;

    @Column(name = "region_surcharge", nullable = false, precision = 19, scale = 4)
    private BigDecimal regionSurcharge;

    @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustmentAmount;

    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    /** 공급가(세금 별도). 파트너 세금계산서·지급은 다음 단계(검수·지급)에서 다룬다. */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerOrderStatus status;

    @Column(name = "response_due_at", nullable = false)
    private LocalDateTime responseDueAt;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "responded_by")
    private Long respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id")
    private Ceremony ceremony;

    @Builder
    private PartnerOrder(
            SalesOrder salesOrder, Organization organization, String currencyCode, BigDecimal baseFee,
            int tabletCount, BigDecimal tabletUnitRate, int onsiteStaffPersonDays, BigDecimal onsiteStaffDayRate,
            BigDecimal regionSurcharge, BigDecimal adjustmentAmount, String adjustmentReason, BigDecimal totalAmount,
            LocalDateTime responseDueAt, Long issuedBy
    ) {
        this.salesOrder = salesOrder;
        this.organization = organization;
        this.currencyCode = currencyCode;
        this.baseFee = baseFee;
        this.tabletCount = tabletCount;
        this.tabletUnitRate = tabletUnitRate;
        this.onsiteStaffPersonDays = onsiteStaffPersonDays;
        this.onsiteStaffDayRate = onsiteStaffDayRate;
        this.regionSurcharge = regionSurcharge;
        this.adjustmentAmount = adjustmentAmount;
        this.adjustmentReason = adjustmentReason;
        this.totalAmount = totalAmount;
        this.responseDueAt = responseDueAt;
        this.issuedBy = issuedBy;
        this.issuedAt = LocalDateTime.now();
        this.status = PartnerOrderStatus.ISSUED;
    }

    public boolean isOverdue(LocalDateTime now) {
        return status == PartnerOrderStatus.ISSUED && now.isAfter(responseDueAt);
    }

    /** 응답 대기 중이지만 기한이 지났으면 EXPIRED로 보여준다(저장 상태는 바꾸지 않는다). */
    public PartnerOrderStatus effectiveStatus(LocalDateTime now) {
        return isOverdue(now) ? PartnerOrderStatus.EXPIRED : status;
    }

    /** 응답 대기(기한 안) 또는 수락 — 같은 수주에 새 발주를 낼 수 없는 상태. */
    public boolean isActive(LocalDateTime now) {
        PartnerOrderStatus effective = effectiveStatus(now);
        return effective == PartnerOrderStatus.ISSUED || effective == PartnerOrderStatus.ACCEPTED;
    }

    public void accept(Long respondedBy, Ceremony ceremony) {
        this.status = PartnerOrderStatus.ACCEPTED;
        this.respondedBy = respondedBy;
        this.respondedAt = LocalDateTime.now();
        this.ceremony = ceremony;
    }

    public void reject(Long respondedBy, String rejectionReason) {
        this.status = PartnerOrderStatus.REJECTED;
        this.respondedBy = respondedBy;
        this.respondedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }

    public void expire() {
        this.status = PartnerOrderStatus.EXPIRED;
    }

    public void cancel(String cancellationReason) {
        this.status = PartnerOrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = cancellationReason;
    }
}
