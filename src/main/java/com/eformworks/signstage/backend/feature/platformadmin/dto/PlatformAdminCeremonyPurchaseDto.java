package com.eformworks.signstage.backend.feature.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자의 행사 용량/선택옵션 추가구매 요청 승인/반려 API용. 승인은 입력할 값이 없어
 * 별도 Request 클래스가 없다({@code PlatformAdminOrganizationRequestDto.Request.Approve}는
 * 조직 코드를 입력받지만, 이 기능은 이미 존재하는 PENDING 행의 상태만 바꾸면 된다).
 */
public final class PlatformAdminCeremonyPurchaseDto {

    private PlatformAdminCeremonyPurchaseDto() {
    }

    public static final class Request {

        private Request() {
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reject {

            @NotBlank
            private String rejectionReason;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CapacityPurchaseRequestSummary {

            private final Long id;
            private final Long requesterId;
            private final String requesterLoginId;
            private final Long organizationId;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final Long capacityAddOnId;
            private final Integer quantity;
            private final BigDecimal purchasedSalePrice;
            private final String status;
            private final String rejectionReason;
            private final String reviewerLoginId;
            private final LocalDateTime reviewedAt;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class OptionalFeaturePurchaseRequestSummary {

            private final Long id;
            private final Long requesterId;
            private final String requesterLoginId;
            private final Long organizationId;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final Long optionalFeatureId;
            private final BigDecimal purchasedSalePrice;
            private final String status;
            private final String rejectionReason;
            private final String reviewerLoginId;
            private final LocalDateTime reviewedAt;
            private final LocalDateTime createdAt;
        }
    }
}
