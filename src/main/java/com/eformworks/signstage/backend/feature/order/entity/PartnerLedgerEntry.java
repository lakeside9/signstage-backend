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
import java.text.DecimalFormat;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 파트너 매입채무 원장 — signstage-docs business/platform-order-partner-subcontract-review.md 5장 "원장".
 * append-only다(수정·삭제 없음). 금액은 공급가 기준이고, 검수 승인 때 지급 대상 금액을 +로, 지급
 * 완료 때 지급 공급가 합계를 −로 남긴다. 파트너별 합계가 미지급 잔액이다.
 */
@Entity
@Table(name = "partner_ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class PartnerLedgerEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private PartnerLedgerEntryType entryType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_order_id")
    private PartnerOrder partnerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_payout_id")
    private PartnerPayout partnerPayout;

    @Column(length = 500)
    private String memo;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Builder
    private PartnerLedgerEntry(
            Organization organization, PartnerLedgerEntryType entryType, String currencyCode, BigDecimal amount,
            PartnerOrder partnerOrder, PartnerPayout partnerPayout, String memo, LocalDate occurredOn
    ) {
        this.organization = organization;
        this.entryType = entryType;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.partnerOrder = partnerOrder;
        this.partnerPayout = partnerPayout;
        this.memo = memo;
        this.occurredOn = occurredOn;
    }

    public static PartnerLedgerEntry payable(OrderInspection inspection) {
        PartnerOrder partnerOrder = inspection.getPartnerOrder();
        String memo = inspection.getDeductionAmount().signum() > 0
                ? "검수 승인(감액 " + new DecimalFormat("#,##0.####").format(inspection.getDeductionAmount()) + ": "
                        + inspection.getDeductionReason() + ")"
                : inspection.getApprovalType() == InspectionApprovalType.DEEMED ? "검수 승인 간주" : "검수 승인";
        return PartnerLedgerEntry.builder()
                .organization(inspection.getOrganization())
                .entryType(PartnerLedgerEntryType.PAYABLE)
                .currencyCode(partnerOrder.getCurrencyCode())
                .amount(inspection.getPayableAmount())
                .partnerOrder(partnerOrder)
                .memo(memo)
                .occurredOn(inspection.getApprovedDate())
                .build();
    }

    public static PartnerLedgerEntry payment(PartnerPayout payout) {
        return PartnerLedgerEntry.builder()
                .organization(payout.getOrganization())
                .entryType(PartnerLedgerEntryType.PAYMENT)
                .currencyCode(payout.getCurrencyCode())
                .amount(payout.getAmount().negate())
                .partnerPayout(payout)
                .memo(payout.getSettlementMonth() + " 정산 지급")
                .occurredOn(payout.getPaidDate())
                .build();
    }

    /** 귀책 상계 등록(−) — 파트너에게 줄 금액이 그만큼 줄어든다. */
    public static PartnerLedgerEntry offset(PartnerOffset offset) {
        return offsetEntry(offset, offset.getAmount().negate(), "귀책 상계: " + offset.getReason(), offset.getOccurredOn());
    }

    /** 귀책 상계 취소(+). */
    public static PartnerLedgerEntry offsetCancelled(PartnerOffset offset, LocalDate occurredOn) {
        return offsetEntry(offset, offset.getAmount(), "귀책 상계 취소: " + offset.getCancellationReason(), occurredOn);
    }

    /** 이의 조정으로 지급 대상 금액이 바뀐 만큼(±). */
    public static PartnerLedgerEntry adjustment(OrderInspection inspection, BigDecimal difference, LocalDate occurredOn) {
        return PartnerLedgerEntry.builder()
                .organization(inspection.getOrganization())
                .entryType(PartnerLedgerEntryType.ADJUSTMENT)
                .currencyCode(inspection.getPartnerOrder().getCurrencyCode())
                .amount(difference)
                .partnerOrder(inspection.getPartnerOrder())
                .memo("감액 이의 조정: " + inspection.getDisputeResolutionNote())
                .occurredOn(occurredOn)
                .build();
    }

    /** 고객 취소 보상(+) — 다음 정산에서 지급액에 더한다(C04). */
    public static PartnerLedgerEntry compensation(PartnerCompensation compensation) {
        return PartnerLedgerEntry.builder()
                .organization(compensation.getOrganization())
                .entryType(PartnerLedgerEntryType.COMPENSATION)
                .currencyCode(compensation.getCurrencyCode())
                .amount(compensation.getAmount())
                .partnerOrder(compensation.getPartnerOrder())
                .memo("고객 취소 보상: " + compensation.getReason())
                .occurredOn(compensation.getOccurredOn())
                .build();
    }

    private static PartnerLedgerEntry offsetEntry(PartnerOffset offset, BigDecimal amount, String memo, LocalDate occurredOn) {
        return PartnerLedgerEntry.builder()
                .organization(offset.getOrganization())
                .entryType(PartnerLedgerEntryType.OFFSET)
                .currencyCode(offset.getCurrencyCode())
                .amount(amount)
                .partnerOrder(offset.getPartnerOrder())
                .memo(memo.length() > 500 ? memo.substring(0, 500) : memo)
                .occurredOn(occurredOn)
                .build();
    }
}
