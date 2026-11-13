package com.eformworks.signstage.backend.feature.order.dto;

import com.eformworks.signstage.backend.core.money.DiscountType;
import com.eformworks.signstage.backend.feature.order.entity.ServiceRegion;
import com.eformworks.signstage.backend.feature.order.model.SalesQuoteAdjustment;
import com.eformworks.signstage.backend.feature.order.model.ScopeExtras;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영업 마진 시뮬레이션 — signstage-docs business/platform-order-partner-subcontract-review.md 11장 8단계.
 * 견적 입력으로 고객 금액과 파트너별 발주 금액·마진을 나란히 계산한다. 저장하지 않는다.
 */
public final class MarginSimulationDto {

    private MarginSimulationDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** @param adjustmentAmount 발주 금액에 더할 조정액(음수 가능). 파트너 행에 같게 적용하고 기본 단가표 행에는 넣지 않는다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Simulate {

            @NotNull
            private Long billingPlanId;

            @NotNull
            private ServiceRegion region;

            @Valid
            private List<PlatformAdminSalesOrderDto.Request.Option> options = new ArrayList<>();

            /** 카탈로그에 없는 직접 입력 항목. */
            @Valid
            private List<PlatformAdminSalesOrderDto.Request.CustomItem> customItems = new ArrayList<>();

            /** 영업 할인 방식. 생략하면 정액(FIXED_AMOUNT)이다 — 견적 저장 요청과 같다. */
            private DiscountType discountType;

            @PositiveOrZero
            private BigDecimal discountAmount;

            @DecimalMin("0")
            @DecimalMax("100")
            private BigDecimal discountRatePercent;

            /** 공급가(부가세 별도) 절사 단위 — 절사한 금액에 부가세를 매긴다 — 10·100·1,000·10,000. 생략하면 절사하지 않는다. */
            private Integer truncationUnit;

            private BigDecimal adjustmentAmount;

            /** 예비 태블릿·리허설 별도일 인력·숙박(D23·D24·D26). 생략하면 0이다. */
            @PositiveOrZero
            @Max(999)
            private Integer spareTabletCount;

            @PositiveOrZero
            @Max(999)
            private Integer rehearsalStaffPersonDays;

            @PositiveOrZero
            @Max(999)
            private Integer lodgingNights;

            @PositiveOrZero
            @Max(999)
            private Integer lodgingPersons;

            public ScopeExtras scopeExtras() {
                return ScopeExtras.of(spareTabletCount, rehearsalStaffPersonDays, lodgingNights, lodgingPersons);
            }

            public SalesQuoteAdjustment quoteAdjustment() {
                return SalesQuoteAdjustment.of(discountType, discountAmount, discountRatePercent, truncationUnit);
            }
        }
    }

    public static final class Response {
        private Response() {
        }

        /**
         * @param minimumMarginRatePercent 발주 발급에 필요한 최소 마진율(고객 공급가 대비 %)
         * @param defaultQuote 기본 단가표로 계산한 발주(견적 저장 때 검사하는 예상 발주 금액)
         */
        @Getter
        @AllArgsConstructor
        public static class Simulation {

            private final String currencyCode;
            private final List<PlatformAdminSalesOrderDto.Response.QuoteLine> lines;
            private final BigDecimal supplyAmount;
            private final BigDecimal vatAmount;
            private final BigDecimal totalAmount;
            private final int tabletCount;
            private final int onsiteStaffPersonDays;
            private final int wifiCount;
            private final BigDecimal minimumMarginRatePercent;
            private final PartnerQuote defaultQuote;
            private final List<PartnerQuote> partnerQuotes;
        }

        /**
         * 한 파트너(또는 기본 단가표)의 발주 금액과 마진. 적용할 단가표가 없으면 금액·마진이 null이다.
         *
         * @param partnerRateCardApplied 파트너 전용 단가표로 계산했는지(아니면 기본 단가표)
         * @param evaluation 파트너 평가. 기본 단가표 행은 null
         */
        @Getter
        @AllArgsConstructor
        public static class PartnerQuote {

            private final Long organizationId;
            private final String organizationName;
            private final boolean partnerRateCardApplied;
            private final BigDecimal partnerAmount;
            private final BigDecimal marginAmount;
            private final BigDecimal marginRatePercent;
            private final Boolean marginSufficient;
            private final PartnerEvaluationDto.Response.PartnerEvaluation evaluation;
        }
    }
}
