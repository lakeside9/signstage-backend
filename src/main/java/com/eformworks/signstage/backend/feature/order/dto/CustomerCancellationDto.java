package com.eformworks.signstage.backend.feature.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 수주 고객 취소 미리보기(플랫폼 관리자) — signstage-docs business/platform-order-pending-items.md C02~C04.
 * 취소 요청 본문은 {@link PlatformAdminSalesOrderDto.Request.Cancel}(사유)을 같이 쓴다.
 */
public final class CustomerCancellationDto {

    private CustomerCancellationDto() {
    }

    public static final class Response {
        private Response() {
        }

        /**
         * 취소하면 생길 일. {@code refundAmount}와 {@code additionalFeeAmount} 중 하나만 0보다 크다 — 받은 금액이
         * 취소 수수료보다 많으면 차액을 환불 대기로, 적으면 차액을 취소 수수료 청구로 만든다.
         */
        @Getter
        @AllArgsConstructor
        public static class Preview {

            private final Long salesOrderId;
            private final String currencyCode;
            private final LocalDate eventDate;
            /** 행사일까지 남은 일수. 행사일이 없으면 null(수수료·보상 0%). */
            private final Long daysBeforeEvent;
            private final BigDecimal feeRatePercent;
            /** 취소 수수료 — 계약 공급가 × 비율. 부가세는 붙이지 않는다(위약금, 세무 확인 필요). */
            private final BigDecimal feeAmount;
            /** 입금 기록이 있는 청구의 입금액 합계. */
            private final BigDecimal paidAmount;
            private final BigDecimal refundAmount;
            private final BigDecimal additionalFeeAmount;
            /** 입금 전이라 함께 취소할 청구 수. */
            private final int unpaidInvoiceCount;
            private final List<PartnerImpact> partnerOrders;
            /** 함께 닫을 행사 id(파트너 교체로 넘겨받기를 기다리던 행사 포함). */
            private final List<Long> ceremonyIds;
            private final boolean hasProposedChange;
        }

        /** 함께 취소할 발주와 취소 보상. 응답 대기 발주는 보상이 없다. */
        @Getter
        @AllArgsConstructor
        public static class PartnerImpact {

            private final Long partnerOrderId;
            private final String organizationName;
            private final String status;
            private final BigDecimal partnerOrderAmount;
            private final BigDecimal compensationRatePercent;
            private final BigDecimal compensationAmount;
        }
    }
}
