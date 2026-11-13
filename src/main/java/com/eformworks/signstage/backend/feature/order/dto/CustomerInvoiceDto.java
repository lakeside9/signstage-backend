package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import com.eformworks.signstage.backend.feature.order.entity.CustomerRefund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 고객 청구·수납 DTO(플랫폼 관리자). */
public final class CustomerInvoiceDto {

    private CustomerInvoiceDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Issue {

            @NotNull
            private CustomerInvoiceType invoiceType;

            @NotNull
            private LocalDate dueDate;

            /**
             * 선금 금액 방식 — PERCENT면 {@code advanceValue}가 수주 금액의 퍼센트(0 초과 100 미만), FIXED_AMOUNT면
             * 부가세 포함 청구 총액이다. 비우면 기본 선금 비율을 쓴다. 잔금 청구에서는 무시한다.
             */
            private DiscountType advanceAmountType;

            @Positive
            private BigDecimal advanceValue;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecordPayment {

            @NotNull
            private LocalDate paidDate;

            @NotNull
            @PositiveOrZero
            private BigDecimal paidAmount;

            @Size(max = 500)
            private String paymentMemo;
        }

        /** 세금계산서는 시스템 밖에서 발행하고 발행일·승인번호만 기록한다(D16). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecordTaxInvoice {

            @NotNull
            private LocalDate issuedDate;

            @NotBlank
            @Size(max = 50)
            private String approvalNumber;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Cancel {

            @NotBlank
            @Size(max = 500)
            private String reason;
        }

        /** 시스템 밖에서 고객에게 돌려준 뒤 환불일을 기록한다(C03). */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RecordRefund {

            @NotNull
            private LocalDate refundedDate;

            @Size(max = 500)
            private String refundMemo;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** {@code overdue}는 입금 기한이 지났는데 입금 기록이 없는 청구(미수)다. */
        @Getter
        @AllArgsConstructor
        public static class InvoiceSummary {

            private final Long id;
            private final Long salesOrderId;
            private final String salesOrderTitle;
            private final Long customerId;
            private final String customerName;
            private final String invoiceType;
            private final String currencyCode;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final LocalDate issueDate;
            private final LocalDate dueDate;
            private final String status;
            private final boolean overdue;
            private final LocalDate paidDate;
            private final BigDecimal paidAmount;
            private final String paymentMemo;
            private final LocalDate taxInvoiceIssuedDate;
            private final String taxInvoiceApprovalNumber;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;

            public static InvoiceSummary from(CustomerInvoice invoice, LocalDate today) {
                return new InvoiceSummary(
                        invoice.getId(),
                        invoice.getSalesOrder().getId(),
                        invoice.getSalesOrder().getTitle(),
                        invoice.getCustomer().getId(),
                        invoice.getCustomer().getName(),
                        invoice.getInvoiceType().name(),
                        invoice.getCurrencyCode(),
                        invoice.getSupplyAmount(),
                        invoice.getVatAmount(),
                        invoice.getTotalAmount(),
                        invoice.getIssueDate(),
                        invoice.getDueDate(),
                        invoice.getStatus().name(),
                        invoice.isOverdue(today),
                        invoice.getPaidDate(),
                        invoice.getPaidAmount(),
                        invoice.getPaymentMemo(),
                        invoice.getTaxInvoiceIssuedDate(),
                        invoice.getTaxInvoiceApprovalNumber(),
                        invoice.getCancelledAt(),
                        invoice.getCancellationReason()
                );
            }
        }

        /**
         * 수주의 청구 현황. 금액은 부가세 포함 합계 기준이고, {@code outstandingAmount}는 청구했지만 아직
         * 입금 기록이 없는 금액이다. {@code advanceIssuable}·{@code balanceIssuable}은 지금 발행할 수 있는지다.
         */
        @Getter
        @AllArgsConstructor
        public static class SalesOrderBilling {

            private final Long salesOrderId;
            private final String currencyCode;
            private final BigDecimal advancePaymentRate;
            private final BigDecimal contractAmount;
            private final BigDecimal billedAmount;
            private final BigDecimal paidAmount;
            private final BigDecimal outstandingAmount;
            private final boolean eventCompleted;
            private final boolean advanceIssuable;
            private final boolean balanceIssuable;
            private final List<InvoiceSummary> invoices;
            /** 고객 취소(C02·C03)면 true와 적용한 취소 수수료 비율·금액, 그리고 생긴 환불. */
            private final boolean cancelledByCustomer;
            private final BigDecimal cancellationFeeRatePercent;
            private final BigDecimal cancellationFeeAmount;
            private final List<RefundSummary> refunds;
        }

        @Getter
        @AllArgsConstructor
        public static class RefundSummary {

            private final Long id;
            private final BigDecimal amount;
            private final String reason;
            private final String status;
            private final LocalDate refundedDate;
            private final String refundMemo;

            public static RefundSummary from(CustomerRefund refund) {
                return new RefundSummary(
                        refund.getId(), refund.getAmount(), refund.getReason(), refund.getStatus().name(),
                        refund.getRefundedDate(), refund.getRefundMemo()
                );
            }
        }
    }
}
