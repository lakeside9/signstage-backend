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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 고객 환불 — signstage-docs business/platform-order-pending-items.md C03(2026-11-13). 고객 취소 때 받은 금액이 취소
 * 수수료보다 많으면 그 차액을 환불 대기로 남긴다. 환불은 시스템 밖에서 하고 환불일·메모만 기록한다(D16과 같은 방식).
 */
@Entity
@Table(name = "customer_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRefund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerRefundStatus status;

    @Column(name = "refunded_date")
    private LocalDate refundedDate;

    @Column(name = "refund_memo", length = 500)
    private String refundMemo;

    @Column(name = "refunded_by")
    private Long refundedBy;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Builder
    private CustomerRefund(SalesOrder salesOrder, BigDecimal amount, String reason) {
        this.salesOrder = salesOrder;
        this.customer = salesOrder.getCustomer();
        this.currencyCode = salesOrder.getCurrencyCode();
        this.amount = amount;
        this.reason = reason;
        this.status = CustomerRefundStatus.PENDING;
    }

    public void recordRefund(Long recordedBy, LocalDate refundedDate, String refundMemo) {
        this.status = CustomerRefundStatus.REFUNDED;
        this.refundedBy = recordedBy;
        this.refundedDate = refundedDate;
        this.refundMemo = refundMemo;
        this.recordedAt = LocalDateTime.now();
    }
}
