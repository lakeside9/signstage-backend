package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조직 구독/계약(요청 → 승인, 중도해지 요청 → 승인) DTO. signstage-docs
 * business/organization-event-discount-pricing-review.md 8장 결정(2026-09-10)을 따른다 —
 * {@link com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationRequestDto}와
 * 같은 요청/승인 패턴.
 */
public final class OrganizationSubscriptionDto {

    private OrganizationSubscriptionDto() {
    }

    public static final class Request {
        private Request() {
        }

        /** 조직(OWNER)이 구독형 플랜을 신청할 때. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CreateSubscription {

            @NotNull
            private Long billingPlanId;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Reject {

            @NotBlank
            private String rejectionReason;
        }

        /** 조직(OWNER)이 사용 중인 구독의 중도 해지를 요청할 때. */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RequestCancellation {

            @NotBlank
            private String cancellationReason;
        }
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class SubscriptionSummary {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String status;
            private final String requesterLoginId;

            // 승인 시점 스냅샷 — 아직 승인 전이면 전부 null.
            private final String planNameSnapshot;
            private final String subscriptionTypeSnapshot;
            private final Integer periodMonthsSnapshot;
            private final Integer allowedCountSnapshot;
            private final Integer usedCount;
            private final Integer remainingCount;
            private final LocalDate startDate;
            private final LocalDate endDate;

            private final String approvalSource;
            private final String reviewerLoginId;
            private final LocalDateTime reviewedAt;
            private final String rejectionReason;
            private final String cancellationReason;
            private final LocalDateTime createdAt;
        }

        @Getter
        @AllArgsConstructor
        public static class SubscriptionHistorySummary {

            private final Long id;
            private final String status;
            private final Long reviewedBy;
            private final String note;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }
    }
}
