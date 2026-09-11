package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

        /**
         * 장비/인력(EQUIPMENT/PERSONNEL) 고객 견적 줄 하나 — 파트너가 카탈로그에서 직접 고른
         * 품목·수량·고객 단가(signstage-docs
         * business/unit-product-purchase-self-checkout-review.md 8.5절 결정, 2026-09-11).
         * 승인된 구매 기록에서 역산하던 옛 방식(파생 목록에 가격만 채워 넣는 방식)을 완전히
         * 대체한다 — 이제 품목·수량 자체도 파트너가 자유롭게 정한다(플랫폼이 실물을 커밋하지
         * 않으므로 수량 상한이 없다).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EquipmentPersonnelLine {

            @NotNull
            private Long unitProductId;

            @NotNull
            @Min(1)
            private Integer quantity;

            @NotNull
            private BigDecimal customerUnitAmount;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GenerateQuote {

            /**
             * 실고객에게 청구할 장비/인력 줄 목록 — 비어 있어도 된다(시스템 사용료만으로 견적을
             * 만들 수도 있다). 각 {@code unitProductId}는 {@code UnitProductCategory}가
             * {@code EQUIPMENT}/{@code PERSONNEL}인 상품이어야 한다(그 외는
             * CUSTOMER_QUOTE_ITEM_NOT_EQUIPMENT_PERSONNEL).
             */
            @Valid
            private List<EquipmentPersonnelLine> equipmentPersonnelLines = List.of();
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
