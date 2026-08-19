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
            private final int mainEventLimit;
        }
    }
}
