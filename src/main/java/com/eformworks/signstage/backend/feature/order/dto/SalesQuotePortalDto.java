package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고객 견적 링크 DTO(signstage-docs business/platform-order-partner-subcontract-review.md D02).
 * 고객에게는 견적 내용과 금액만 보여준다 — 예상 발주 금액·메모 같은 내부 정보는 빼고 보낸다.
 */
public final class SalesQuotePortalDto {

    private SalesQuotePortalDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AcceptQuote {

            @NotBlank
            @Size(max = 100)
            private String acceptedByName;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class SalesQuote {

            private final String customerName;
            private final String title;
            private final String billingPlanName;
            private final String location;
            private final LocalDateTime startsAt;
            private final LocalDateTime endsAt;
            private final String currencyCode;
            private final Short currencyFractionDigits;
            private final List<Line> lines;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final LocalDate quoteValidUntil;
            private final String status;
            private final LocalDateTime acceptedAt;
            private final String acceptedByName;
            /** 견적서 "제공 서비스" 본문. null이면 그 칸이 없다. */
            private final String serviceDescription;

            public static SalesQuote of(SalesOrder salesOrder, List<SalesOrderLine> lines) {
                return new SalesQuote(
                        salesOrder.getCustomer().getName(),
                        salesOrder.getTitle(),
                        salesOrder.getBillingPlan().getName(),
                        salesOrder.getLocation(),
                        salesOrder.getStartsAt(),
                        salesOrder.getEndsAt(),
                        salesOrder.getCurrencyCode(),
                        salesOrder.getCurrencyFractionDigits(),
                        lines.stream().map(Line::from).toList(),
                        salesOrder.getSupplyAmount(),
                        salesOrder.getVatAmount(),
                        salesOrder.getTotalAmount(),
                        salesOrder.getQuoteValidUntil(),
                        salesOrder.getStatus().name(),
                        salesOrder.getAcceptedAt(),
                        salesOrder.getAcceptedByName(),
                        salesOrder.getServiceDescription()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class Line {

            private final String lineType;
            private final String name;
            private final int quantity;
            private final BigDecimal unitPrice;
            private final BigDecimal amount;
            /** 줄 이름 아래 보이는 설명(패키지는 기본 포함 구성, 옵션은 상품 견적 설명). */
            private final String description;

            public static Line from(SalesOrderLine line) {
                return new Line(
                        line.getLineType().name(), line.getName(), line.getQuantity(), line.getUnitPrice(), line.getAmount(),
                        line.getDescription()
                );
            }
        }
    }
}
