package com.eformworks.signstage.backend.feature.ceremony.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조직×품목 세밀 할인 오버라이드 — 행 하나가 기간 하나(다중 버전, 안 B). signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #4(2026-09-08) 참고.
 */
public final class OrganizationDiscountDto {

    private OrganizationDiscountDto() {
    }

    public static final class Request {

        private Request() {
        }

        /**
         * 기간 하나를 새로 만들거나(POST) 이미 있는 기간 하나를 고칠 때(PUT) 공통으로 쓴다.
         * {@code effectiveFrom}은 결정 #5(오늘 판단 타임존)가 유보라 자동 기본값을 채우지 않고
         * 항상 필수 입력으로 받는다 — signstage-docs
         * business/organization-discount-override-security-and-validity-period-review.md 3.2절.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SetDiscount {

            @NotBlank
            private String discountType;

            @NotNull
            private BigDecimal discountValue;

            @NotNull
            private LocalDate effectiveFrom;

            /** null이면 무기한(그 뒤로 다른 기간이 없는 한). */
            private LocalDate effectiveTo;
        }
    }

    public static final class Response {

        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class BillingPlanDiscountSummary {

            private final Long id;
            private final Long organizationId;
            /** 조직 횡단 목록 화면(discount-management-screen-separation-review.md)이 쓴다 — 조직 상세 안에서는 무시해도 된다. */
            private final String organizationName;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            /** PENDING(예정)/ACTIVE(적용 중)/EXPIRED(만료됨) — 서버가 계산해 내려준다. */
            private final String status;
            private final LocalDateTime createdAt;
        }

        /**
         * 조직별 할인 관리 화면이 한 조직에 걸린 플랜 오버라이드(모든 플랜·모든 기간)를 받는
         * 데 쓴다. 옛 선택옵션/용량추가구매 오버라이드는 폐지됐다(signstage-docs
         * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10, 4장) —
         * 조직별 할인은 이제 플랜에만 있다.
         */
        @Getter
        @AllArgsConstructor
        public static class OrganizationDiscountOverview {

            private final List<BillingPlanDiscountSummary> billingPlanDiscounts;
        }

        /**
         * 조직×플랜 할인 오버라이드 변경 이력 한 행. 설정(생성/수정) 시점마다, 그리고 제거
         * 시점에(removed=true, 그 직전 값) 한 건씩 쌓인다 — 카탈로그의
         * {@code BillingPlanHistorySummary}와 같은 구조다.
         */
        @Getter
        @AllArgsConstructor
        public static class BillingPlanDiscountHistorySummary {

            private final Long id;
            private final Long organizationId;
            private final Long billingPlanId;
            private final String billingPlanName;
            private final String discountType;
            private final BigDecimal discountValue;
            private final LocalDate effectiveFrom;
            private final LocalDate effectiveTo;
            private final boolean removed;
            private final Long createdBy;
            private final LocalDateTime createdAt;
        }

    }
}
