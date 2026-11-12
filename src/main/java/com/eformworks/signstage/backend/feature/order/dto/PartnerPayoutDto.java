package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

/** 파트너 월 합산 지급·원장 DTO — 플랫폼 관리자와 파트너(자기 조직) 화면에서 같이 쓴다. */
public final class PartnerPayoutDto {

    private PartnerPayoutDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreatePayouts {

            /** 검수 승인월(yyyy-MM). 지난달까지만 된다. */
            @NotBlank
            @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")
            private String settlementMonth;
        }

        /** 실제 이체 기록 — 이체 금액은 부가세·원천징수를 반영한 금액이라 공급가 합계와 다를 수 있다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MarkPaid {

            @NotNull
            private LocalDate paidDate;

            @NotNull
            @PositiveOrZero
            private BigDecimal transferredAmount;

            @Size(max = 500)
            private String paymentMemo;
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

        @Getter
        @AllArgsConstructor
        public static class PayoutSummary {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final String settlementMonth;
            private final String currencyCode;
            private final BigDecimal amount;
            private final LocalDate dueDate;
            private final String status;
            private final String bankName;
            private final String bankAccountNumber;
            private final String accountHolderName;
            private final LocalDate paidDate;
            private final BigDecimal transferredAmount;
            private final String paymentMemo;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            private final LocalDateTime createdAt;

            public static PayoutSummary from(PartnerPayout payout) {
                return new PayoutSummary(
                        payout.getId(),
                        payout.getOrganization().getId(),
                        payout.getOrganization().getName(),
                        payout.getSettlementMonth(),
                        payout.getCurrencyCode(),
                        payout.getAmount(),
                        payout.getDueDate(),
                        payout.getStatus().name(),
                        payout.getBankName(),
                        payout.getBankAccountNumber(),
                        payout.getAccountHolderName(),
                        payout.getPaidDate(),
                        payout.getTransferredAmount(),
                        payout.getPaymentMemo(),
                        payout.getCancelledAt(),
                        payout.getCancellationReason(),
                        payout.getCreatedAt()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class PayoutDetail {

            private final PayoutSummary summary;
            private final List<OrderInspectionDto.Response.InspectionSummary> items;
        }

        @Getter
        @AllArgsConstructor
        public static class LedgerEntryItem {

            private final Long id;
            private final String entryType;
            private final String currencyCode;
            private final BigDecimal amount;
            private final Long partnerOrderId;
            private final Long partnerPayoutId;
            private final String memo;
            private final LocalDate occurredOn;

            public static LedgerEntryItem from(PartnerLedgerEntry entry) {
                return new LedgerEntryItem(
                        entry.getId(),
                        entry.getEntryType().name(),
                        entry.getCurrencyCode(),
                        entry.getAmount(),
                        entry.getPartnerOrder() != null ? entry.getPartnerOrder().getId() : null,
                        entry.getPartnerPayout() != null ? entry.getPartnerPayout().getId() : null,
                        entry.getMemo(),
                        entry.getOccurredOn()
                );
            }
        }

        /** 원장 — {@code balance}는 미지급 잔액(공급가)이다. */
        @Getter
        @AllArgsConstructor
        public static class Ledger {

            private final BigDecimal balance;
            private final PageResponse<LedgerEntryItem> entries;
        }
    }
}
