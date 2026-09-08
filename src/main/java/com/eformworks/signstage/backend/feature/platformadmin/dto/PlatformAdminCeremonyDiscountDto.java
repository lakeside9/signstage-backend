package com.eformworks.signstage.backend.feature.platformadmin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 행사 건별 재량 할인 조직 횡단 목록 화면용. signstage-docs
 * business/discount-management-screen-separation-review.md 참고 — 조직 상세를 거치지 않고
 * 전체 조직의 행사를 한 목록에서 훑을 수 있게 조직명을 같이 내려준다. 실제 값 조회/수정은
 * 기존 {@code PlatformAdminCeremonyController}(조직 하위 중첩) 엔드포인트를 그대로 재사용한다
 * (같은 문서 6장 결정 #2).
 */
public final class PlatformAdminCeremonyDiscountDto {

    private PlatformAdminCeremonyDiscountDto() {
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class CeremonyDiscountSummary {

            private final Long id;
            private final Long organizationId;
            private final String organizationName;
            private final String title;
            private final String status;
            private final String finalDiscountType;
            private final BigDecimal finalDiscountValue;
            private final LocalDateTime createdAt;
        }
    }
}
