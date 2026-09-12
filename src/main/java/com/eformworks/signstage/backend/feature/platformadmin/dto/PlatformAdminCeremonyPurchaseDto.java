package com.eformworks.signstage.backend.feature.platformadmin.dto;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플랫폼 관리자의 행사 단위 상품 추가구매 요청 승인/반려 API용 — 옛
 * {@code CapacityPurchaseRequestSummary}/{@code OptionalFeaturePurchaseRequestSummary} 통합
 * (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
 * 2026-09-10). 승인은 입력할 값이 없어 별도 Request 클래스가 없다
 * ({@code PlatformAdminOrganizationRequestDto.Request.Approve}는 조직 코드를 입력받지만,
 * 이 기능은 이미 존재하는 PENDING 요청의 상태만 바꾸면 된다).
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

        /**
         * 이미 승인(APPROVED)된 구매를 취소할 때 — signstage-docs
         * business/ceremony-unit-product-purchase-cancellation-review.md 결정(2026-09-12).
         * {@link Reject}와 같은 모양(사유 필수)이다.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Cancel {

            @NotBlank
            private String cancellationReason;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class UnitProductPurchaseRequestSummary {

            private final Long id;
            private final Long requesterId;
            private final String requesterLoginId;
            private final Long organizationId;
            private final Long ceremonyId;
            private final String ceremonyTitle;
            private final List<CeremonyDto.Response.UnitProductPurchaseLineSummary> lines;
            private final String status;
            private final String rejectionReason;
            private final String reviewerLoginId;
            private final LocalDateTime reviewedAt;
            /** 취소한 관리자 — 승인/반려한 사람({@code reviewerLoginId})과 별도 컬럼이다(3.1절). */
            private final String cancellerLoginId;
            private final LocalDateTime cancelledAt;
            private final String cancellationReason;
            /**
             * 이 행사의 하위 행사(TEST/REHEARSAL/MAIN) 진행상태 — signstage-docs
             * business/ceremony-unit-product-purchase-cancellation-review.md 3.6절(2026-09-12
             * 추가). 관리자가 취소(특히 이벤트 효과 묶음)를 판단할 근거로 쓴다. 개별 행사를
             * 그대로 나열한 목록이고, 타입별 압축 표시는 프런트가 한다.
             */
            private final List<CeremonyEventStatusSummary> ceremonyEvents;
            private final LocalDateTime createdAt;
        }

        /**
         * 하위 행사 하나의 진행상태 — 3.6절. {@code CeremonyEventDto}의 전체 필드(accessKey 등)를
         * 그대로 내려주지 않고 이 화면에 필요한 최소 필드만 담는다.
         */
        @Getter
        @AllArgsConstructor
        public static class CeremonyEventStatusSummary {

            private final String eventType;
            private final String name;
            private final String status;
            private final LocalDateTime scheduledStartAt;
            private final LocalDateTime actualStartAt;
        }
    }
}
