package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 파트너 → 실고객 고객 견적서 DTO — signstage-docs
 * business/partner-customer-quote-design-review.md 결정(2026-09-11 구현). 마진(조직
 * 기본값/행사별 override)과 견적서 생성/조회를 함께 다룬다.
 */
public final class CustomerQuoteDto {

    private CustomerQuoteDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 조직 기본 마진/행사별 마진 override 설정 공용 — PERCENT|FIXED_AMOUNT. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateMargin {

            @NotNull
            private String marginType;

            @NotNull
            private BigDecimal marginValue;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EquipmentPersonnelPrice {

            @NotNull
            private Long unitProductId;

            @NotNull
            private BigDecimal customerUnitAmount;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GenerateQuote {

            /**
             * 이 행사에서 승인된 장비/인력(EQUIPMENT/PERSONNEL) 단위 상품마다 실고객에게 청구할
             * 단가 — {@link com.eformworks.signstage.backend.feature.ceremony.service
             * .CustomerQuoteService#retrievePricingInputs}가 알려주는 unitProductId 전부를
             * 빠짐없이 담아야 한다(누락 시 CUSTOMER_QUOTE_PRICE_REQUIRED).
             */
            @NotEmpty
            @Valid
            private List<EquipmentPersonnelPrice> equipmentPersonnelPrices;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** marginType/marginValue가 둘 다 null이면 "설정되지 않음"이다. */
        @Getter
        @AllArgsConstructor
        public static class MarginPolicy {

            private final String marginType;
            private final BigDecimal marginValue;
        }

        /** CEREMONY_OVERRIDE | ORGANIZATION_DEFAULT | NONE. */
        @Getter
        @AllArgsConstructor
        public static class EffectiveMargin {

            private final String marginType;
            private final BigDecimal marginValue;
            private final String source;
        }

        /** 이 행사에서 승인된 장비/인력 단위 상품 하나 — 고객 단가 입력 화면이 이 목록을 그대로 폼으로 그린다. */
        @Getter
        @AllArgsConstructor
        public static class PricingInput {

            private final Long unitProductId;
            private final String itemName;
            private final Integer quantity;
            private final BigDecimal referenceCostUnitAmount;
            private final BigDecimal referenceCostAmount;
        }

        @Getter
        @AllArgsConstructor
        public static class QuoteSummary {

            private final Long id;
            private final Integer version;
            private final String currencyCode;
            private final Short currencyFractionDigits;
            private final BigDecimal systemUsageCostAmount;
            private final String marginType;
            private final BigDecimal marginValue;
            private final BigDecimal systemUsageMarginAmount;
            private final BigDecimal systemUsageCustomerAmount;
            private final BigDecimal equipmentPersonnelCustomerAmount;
            private final BigDecimal totalCustomerAmount;
            private final LocalDateTime pricingCalculatedAt;
            private final String createdByLoginId;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class QuoteDetail {

            private final QuoteSummary summary;
            private final List<QuoteLineSummary> lines;
        }

        @Getter
        @AllArgsConstructor
        public static class QuoteLineSummary {

            private final String lineType;
            private final Long itemId;
            private final String itemName;
            private final Integer quantity;
            private final BigDecimal referenceCostUnitAmount;
            private final BigDecimal customerUnitAmount;
            private final BigDecimal customerAmount;
        }
    }
}
