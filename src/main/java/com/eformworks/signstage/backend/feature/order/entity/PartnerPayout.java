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
 * 파트너 월 합산 지급 — signstage-docs business/platform-order-partner-subcontract-review.md D11(월 합산),
 * D12(고객 입금과 무관하게 지급). 파트너별로 한 달 동안 검수 승인된 발주의 지급 대상 금액(공급가)을
 * 묶는다. 지급 기한은 승인월 다음 달 말일이되, 가장 이른 승인일부터 60일(하도급법 상한)을 넘지 않는다.
 *
 * <p>정산을 만들 때 정산 계좌를 스냅샷한다. 실제 이체·세금계산서는 시스템 밖에서 하고, 이체 후 지급일·
 * 이체 금액(부가세·원천징수 반영)·메모를 기록한다. 이체 전에는 취소해 검수 건을 다시 정산할 수 있다.
 */
@Entity
@Table(name = "partner_payouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerPayout extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** 검수 승인월(yyyy-MM). */
    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** 지급 대상 공급가 합계. */
    /** 검수 지급 대상 금액 합계(공급가). */
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    /** 이번 지급에서 뺀 귀책 상계(D13). 합계를 넘는 상계는 다음 지급으로 이월한다. */
    @Column(name = "offset_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal offsetAmount;

    /** 지급액(공급가) = 검수 합계 − 상계. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerPayoutStatus status;

    /** 고액 지급(D19) — 기준 금액 이상이면 PLATFORM_SUPER가 승인해야 지급 완료로 기록할 수 있다. */
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    /** 실제 이체 금액(부가세·원천징수 반영). 공급가 합계와 다를 수 있다. */
    @Column(name = "transferred_amount", precision = 19, scale = 4)
    private BigDecimal transferredAmount;

    @Column(name = "payment_memo", length = 500)
    private String paymentMemo;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private PartnerPayout(
            Organization organization, String settlementMonth, String currencyCode, BigDecimal grossAmount,
            BigDecimal offsetAmount, LocalDate dueDate, String bankName, String bankAccountNumber,
            String accountHolderName, boolean approvalRequired
    ) {
        this.organization = organization;
        this.settlementMonth = settlementMonth;
        this.currencyCode = currencyCode;
        this.grossAmount = grossAmount;
        this.offsetAmount = offsetAmount == null ? BigDecimal.ZERO : offsetAmount;
        this.amount = grossAmount.subtract(this.offsetAmount);
        this.approvalRequired = approvalRequired;
        this.dueDate = dueDate;
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
        this.accountHolderName = accountHolderName;
        this.status = PartnerPayoutStatus.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == PartnerPayoutStatus.CONFIRMED;
    }

    /** 고액 지급인데 아직 승인되지 않았다. */
    public boolean isAwaitingApproval() {
        return approvalRequired && approvedAt == null;
    }

    public void approve(Long approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
    }

    public void markPaid(Long paidBy, LocalDate paidDate, BigDecimal transferredAmount, String paymentMemo) {
        this.status = PartnerPayoutStatus.PAID;
        this.paidBy = paidBy;
        this.paidDate = paidDate;
        this.transferredAmount = transferredAmount;
        this.paymentMemo = paymentMemo;
    }

    public void cancel(String reason) {
        this.status = PartnerPayoutStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }
}
