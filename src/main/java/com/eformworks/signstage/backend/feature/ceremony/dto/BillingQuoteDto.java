package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 확정 견적(billing quote) DTO — signstage-docs
 * business/currency-tax-internationalization-review.md 9/10장(2026-09-10 구현). "예상 청구
 * 금액"(draft, {@link CeremonyDto.Response.EstimatedTotal})과 계산 로직은 완전히 같고, 이
 * DTO는 그 계산 결과를 스냅샷으로 고정한 뒤 조회/무효화하는 데만 쓴다 — 새로 계산하는
 * 파라미터는 없다(확정 시점에 Ceremony의 현재 상태를 그대로 스냅샷한다).
 */
public final class BillingQuoteDto {

    private BillingQuoteDto() {
    }

    public static final class Request {
        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VoidQuote {

            @NotBlank
            private String reason;
        }
    }

    public static final class Response {
        private Response() {
        }

        /** 견적 목록 화면 한 행 — 라인 상세 없이 헤더 합계 + 현재 상태만. */
        @Getter
        @AllArgsConstructor
        public static class QuoteSummary {

            private final Long id;
            private final Integer version;
            /** FINALIZED | VOID — 가장 최근 상태 이벤트로 판정. */
            private final String status;
            private final String currencyCode;
            private final Short currencyFractionDigits;
            private final BigDecimal netAmount;
            private final BigDecimal discountAmount;
            private final BigDecimal taxAmount;
            private final BigDecimal grossAmount;
            private final LocalDateTime pricingCalculatedAt;
            private final LocalDate taxPointDate;
            private final String createdByLoginId;
            private final LocalDateTime createdAt;
            /** VOID 상태일 때만 값이 있다. */
            private final String voidReason;
        }

        /** 견적 상세 — 요약 + 줄 단위 내역. */
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
            /** ESSENTIAL/APPLICATION(시스템 사용료) 또는 EQUIPMENT/PERSONNEL(실물·인력 대금) — 매출 갈래 리포팅용 스냅샷. */
            private final String category;
            private final Integer quantity;
            private final BigDecimal unitListAmount;
            private final BigDecimal listAmount;
            private final BigDecimal itemDiscountAmount;
            private final BigDecimal ceremonyDiscountAmount;
            private final BigDecimal netAmount;
            private final String taxCode;
            private final String taxCategory;
            private final BigDecimal taxRatePercent;
            private final String priceInclusion;
            private final BigDecimal taxAmount;
            private final BigDecimal grossAmount;
        }
    }
}
