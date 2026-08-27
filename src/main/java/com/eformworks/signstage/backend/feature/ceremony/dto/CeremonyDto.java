package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CeremonyDto {

    private CeremonyDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateCeremony {

            /** 필수 — signstage-docs business/ceremony-billing-options-review.md 4.10절 결정. */
            @NotNull
            private Long billingPlanId;

            @NotBlank
            private String title;
        }

        /**
         * 행사 수정 화면에서 기본 정보를 바꿀 때 쓴다. 플랜은 여기서 바꾸지 않는다(생성 시점에 고정).
         * title 외에는 전부 선택 입력이다 — 빈 문자열/null 모두 "입력 없음"으로 저장한다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateCeremony {

            @NotBlank
            private String title;

            private String description;

            private String organizingInstitution;

            private String organizingDepartment;

            private String contactName;

            private String contactTitle;

            private String contactPhone;

            @Email
            private String contactEmail;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PurchaseCapacity {

            @NotNull
            private Long capacityAddOnId;

            @NotNull
            @Min(1)
            private Integer quantity;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PurchaseOptionalFeature {

            @NotNull
            private Long optionalFeatureId;
        }

        /** 플랫폼 관리자 전용. IN_PROGRESS/COMPLETED만 허용한다. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UpdateStatus {

            @NotBlank
            private String status;
        }

        /**
         * 플랜 확정 전(DRAFT)에만 허용된다 — signstage-docs
         * business/ceremony-plan-confirmation-review.md 3.2절.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChangePlan {

            @NotNull
            private Long billingPlanId;
        }

        /**
         * 행사 건별 재량 할인. 플랫폼 관리자(PLATFORM_OPS 이상) 전용이고, 플랜이 확정된
         * (IN_PROGRESS) 행사에만 적용할 수 있다 — DRAFT/COMPLETED는 거부된다(signstage-docs
         * business/organization-event-discount-pricing-review.md 4.2/6.2절).
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ApplyFinalDiscount {

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonySummary {

            private final Long id;
            private final Long organizationId;
            private final Long billingPlanId;
            private final String title;
            private final String description;
            private final String status;
            private final String organizingInstitution;
            private final String organizingDepartment;
            private final String contactName;
            private final String contactTitle;
            private final String contactPhone;
            private final String contactEmail;
            private final String finalDiscountType;
            private final BigDecimal finalDiscountValue;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class CapacityPurchaseSummary {

            private final Long id;
            private final Long ceremonyId;
            private final Long capacityAddOnId;
            private final Integer quantity;
            private final Integer purchasedUnitAmount;
            /** 묶음 상품(예: "서명자+태블릿")이었을 때만 값이 있다 — 구매 시점 보조 용량 단가 스냅샷. */
            private final Integer purchasedSecondaryUnitAmount;
            private final BigDecimal purchasedSalePrice;
            private final String purchasedDiscountType;
            private final BigDecimal purchasedDiscountValue;
            private final String status;
            private final String rejectionReason;
            private final LocalDateTime reviewedAt;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeaturePurchaseSummary {

            private final Long id;
            private final Long ceremonyId;
            private final Long optionalFeatureId;
            private final String purchasedName;
            private final BigDecimal purchasedSalePrice;
            private final String purchasedDiscountType;
            private final BigDecimal purchasedDiscountValue;
            private final String status;
            private final String rejectionReason;
            private final LocalDateTime reviewedAt;
            private final LocalDateTime createdAt;
        }

        /**
         * 서명자/문서양식/테스트·본행사 각각의 유효 한도(플랜 기본값 + 승인된 추가구매). 등록 화면
         * 타이틀에 "등록할 수 있는 개수"를 보여주는 데 쓴다. 플랜이 없는 행사는 Integer.MAX_VALUE로
         * 온다(프런트가 "무제한"으로 표시).
         */
        @Getter
        @AllArgsConstructor
        public static class CapacityStatus {

            private final int signerLimit;
            private final int templateLimit;
            private final int testEventLimit;
            private final int rehearsalEventLimit;
            private final int mainEventLimit;
        }

        /**
         * 이 Ceremony의 예상 청구 금액 — 품목 할인 → subtotal → 행사 건별 할인의 2단 순차
         * 차감(signstage-docs business/organization-event-discount-pricing-review.md 4.3절).
         * 실제 결제/청구서 발행은 여전히 범위 밖이다(같은 문서 5장) — 지금 계산하면 얼마인지
         * 보여주는 견적용이다. 각 금액은 승인(APPROVED)된 구매 건만 반영한다.
         */
        @Getter
        @AllArgsConstructor
        public static class EstimatedTotal {

            private final BigDecimal planAppliedPrice;
            private final BigDecimal capacityPurchasesTotal;
            private final BigDecimal optionalFeaturePurchasesTotal;
            private final BigDecimal subtotal;
            private final String finalDiscountType;
            private final BigDecimal finalDiscountValue;
            private final BigDecimal finalTotal;
        }

        /**
         * 플랜 변경 이력 한 행 — 그 변경 시점의 플랜 이름/가격/한도 스냅샷이다(카탈로그가
         * 나중에 바뀌어도 안 바뀜). signstage-docs business/ceremony-plan-confirmation-review.md
         * 3.4절.
         */
        @Getter
        @AllArgsConstructor
        public static class PlanHistorySummary {

            private final Long id;
            private final Long billingPlanId;
            private final String planName;
            private final BigDecimal planSupplyPrice;
            private final BigDecimal planSalePrice;
            private final String planDiscountType;
            private final BigDecimal planDiscountValue;
            private final Integer planMaxSigners;
            private final Integer planMaxTemplates;
            private final Integer planMaxTestEvents;
            private final Integer planMaxRehearsalEvents;
            private final Integer planMaxMainEvents;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
