package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        }
    }
}
