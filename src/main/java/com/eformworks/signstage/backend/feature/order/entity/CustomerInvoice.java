package com.eformworks.signstage.backend.feature.order.entity;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객 청구 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 7단계, D12·D16.
 *
 * <ul>
 *   <li>수주 하나에 선금(ADVANCE)·잔금(BALANCE)이 각각 하나다(취소된 건 제외). 선금은 수주 금액 × 선금
 *       비율, 잔금은 수주 금액에서 선금을 뺀 나머지다. 공급가·부가세를 나눠 스냅샷한다.</li>
 *   <li>입금은 시스템 밖에서 받고 입금일·입금액을 기록한다. 세금계산서도 시스템 밖에서 발행하고
 *       발행일·승인번호만 기록한다.</li>
 *   <li>입금 전에는 취소해 다시 발행할 수 있다.</li>
 * </ul>
 */
@Entity
@Table(name = "customer_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerInvoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    private CustomerInvoiceType invoiceType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "supply_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplyAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerInvoiceStatus status;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    /** 실제 입금액. 이체 수수료 등으로 청구 금액과 다를 수 있다. */
    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "payment_memo", length = 500)
    private String paymentMemo;

    @Column(name = "payment_recorded_by")
    private Long paymentRecordedBy;

    @Column(name = "tax_invoice_issued_date")
    private LocalDate taxInvoiceIssuedDate;

    @Column(name = "tax_invoice_approval_number", length = 50)
    private String taxInvoiceApprovalNumber;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder
    private CustomerInvoice(
            SalesOrder salesOrder, CustomerInvoiceType invoiceType, BigDecimal supplyAmount, BigDecimal vatAmount,
            LocalDate issueDate, LocalDate dueDate, Long issuedBy
    ) {
        this.salesOrder = salesOrder;
        this.customer = salesOrder.getCustomer();
        this.invoiceType = invoiceType;
        this.currencyCode = salesOrder.getCurrencyCode();
        this.supplyAmount = supplyAmount;
        this.vatAmount = vatAmount;
        this.totalAmount = supplyAmount.add(vatAmount);
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.issuedBy = issuedBy;
        this.status = CustomerInvoiceStatus.ISSUED;
    }

    public boolean isIssued() {
        return status == CustomerInvoiceStatus.ISSUED;
    }

    public boolean isActive() {
        return status != CustomerInvoiceStatus.CANCELLED;
    }

    /** 입금 기한이 지났는데 아직 입금 기록이 없는 청구(미수). */
    public boolean isOverdue(LocalDate today) {
        return isIssued() && today.isAfter(dueDate);
    }

    public void recordPayment(Long recordedBy, LocalDate paidDate, BigDecimal paidAmount, String paymentMemo) {
        this.status = CustomerInvoiceStatus.PAID;
        this.paymentRecordedBy = recordedBy;
        this.paidDate = paidDate;
        this.paidAmount = paidAmount;
        this.paymentMemo = paymentMemo;
    }

    public void recordTaxInvoice(LocalDate issuedDate, String approvalNumber) {
        this.taxInvoiceIssuedDate = issuedDate;
        this.taxInvoiceApprovalNumber = approvalNumber;
    }

    public void cancel(String reason) {
        this.status = CustomerInvoiceStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }
}
