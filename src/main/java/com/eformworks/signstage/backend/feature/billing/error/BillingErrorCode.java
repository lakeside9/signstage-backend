package com.eformworks.signstage.backend.feature.billing.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.billing 업무 오류(과금 카탈로그·조직 할인·구독). 2026-11-11 {@code CeremonyErrorCode}에서
 * 분리했다. 응답 {@code code} 값은 {@code BILLING_} 접두어를 쓴다 — 분리 직후에는 옛
 * {@code CEREMONY_} 값을 유지했다가, 프론트엔드·앱이 이 값으로 분기하지 않는 것을 확인하고
 * 바꿨다(backend-package-structure-consistency-review.md 11.5.3절).
 */
public enum BillingErrorCode implements ErrorCode {

    BILLING_PLAN_NOT_FOUND("BILLING_PLAN_NOT_FOUND", HttpStatus.NOT_FOUND, "과금 플랜을 찾을 수 없습니다."),
    /** 판매 경로(U01)가 맞지 않는 플랜·단위 상품을 쓰려 했다 — 두 경로의 카탈로그는 섞지 않는다. */
    SALES_CHANNEL_MISMATCH(
            "BILLING_SALES_CHANNEL_MISMATCH",
            HttpStatus.CONFLICT,
            "이 기능에서 쓸 수 없는 판매 경로의 플랜 또는 단위 상품입니다."
    ),
    /** 플랫폼 수주 카탈로그에서 허용하지 않는 구성(U02·U05). */
    PLATFORM_ORDER_CATALOG_INVALID(
            "BILLING_PLATFORM_ORDER_CATALOG_INVALID",
            HttpStatus.CONFLICT,
            "플랫폼 수주 카탈로그에서 허용하지 않는 구성입니다. 현장 인력은 하나만, 현장지원 요청형은 등록할 수 없고, 판매 가격·할인 기간과 구독 조건은 두지 않습니다."
    ),
    /** 플랫폼 수주 플랜 구성에 파트너 수행 상품(플랫폼 이용료가 아닌 상품)을 넣으려 함(I06). */
    PLATFORM_ORDER_PLAN_PARTNER_PRODUCT(
            "BILLING_PLATFORM_ORDER_PLAN_PARTNER_PRODUCT",
            HttpStatus.CONFLICT,
            "플랫폼 수주 플랜에는 플랫폼 제공 상품만 넣을 수 있습니다. 태블릿·현장 인력 같은 파트너 수행 상품은 수주 견적의 옵션으로 넣어주세요."
    ),
    BILLING_PLAN_IN_USE(
            "BILLING_PLAN_IN_USE",
            HttpStatus.CONFLICT,
            "행사에 적용되었거나 조직 구독·할인 오버라이드에 사용된 적이 있는 과금 플랜은 삭제할 수 없습니다."
    ),
    TAX_POLICY_NOT_FOUND(
            "BILLING_TAX_POLICY_NOT_FOUND",
            HttpStatus.CONFLICT,
            "거래일에 적용할 수 있는 세금 정책을 찾을 수 없습니다."
    ),
    /**
     * {@code UnitProduct} 통합 카탈로그(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md, 2026-09-10)의 오류 코드 —
     * 옛 {@code OPTIONAL_FEATURE_*}/{@code CAPACITY_ADDON_*} 카탈로그 코드를 대체했다.
     */
    UNIT_PRODUCT_NOT_FOUND("BILLING_UNIT_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "단위 상품을 찾을 수 없습니다."),
    UNIT_PRODUCT_EFFECT_BUNDLE_ONLY(
            "BILLING_UNIT_PRODUCT_EFFECT_BUNDLE_ONLY",
            HttpStatus.CONFLICT,
            "이벤트 효과 목록은 EVENT_EFFECT_BUNDLE 종류의 단위 상품에서만 지정할 수 있습니다."
    ),
    UNIT_PRODUCT_MAX_QUANTITY_NOT_MULTIPLE_OF_SALE_UNIT(
            "BILLING_UNIT_PRODUCT_MAX_QUANTITY_NOT_MULTIPLE_OF_SALE_UNIT",
            HttpStatus.BAD_REQUEST,
            "최대 구매 수량은 판매 단위 수량의 배수여야 합니다."
    ),
    UNIT_PRODUCT_IN_USE(
            "BILLING_UNIT_PRODUCT_IN_USE",
            HttpStatus.CONFLICT,
            "플랜에 포함되었거나 구매·적용된 적이 있는 단위 상품은 삭제할 수 없습니다."
    ),
    DISCOUNT_VALUE_INVALID(
            "BILLING_DISCOUNT_VALUE_INVALID",
            HttpStatus.BAD_REQUEST,
            "할인값은 0 이상이어야 하고, 정률(PERCENT) 할인은 100을 넘을 수 없습니다."
    ),
    CATALOG_PRICE_VALUE_INVALID(
            "BILLING_CATALOG_PRICE_VALUE_INVALID",
            HttpStatus.BAD_REQUEST,
            "공급가/판매가는 0 이상이어야 합니다."
    ),
    DISCOUNT_PERIOD_INVALID(
            "BILLING_DISCOUNT_PERIOD_INVALID",
            HttpStatus.BAD_REQUEST,
            "종료일은 시작일보다 빠를 수 없습니다."
    ),
    DISCOUNT_PERIOD_OVERLAPPING(
            "BILLING_DISCOUNT_PERIOD_OVERLAPPING",
            HttpStatus.CONFLICT,
            "같은 조직×품목에 이미 겹치는 기간의 할인 오버라이드가 있습니다."
    ),
    ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND(
            "BILLING_ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "할인 오버라이드 기간을 찾을 수 없습니다."
    ),
    CATALOG_PRICE_PERIOD_OVERLAPPING(
            "BILLING_CATALOG_PRICE_PERIOD_OVERLAPPING",
            HttpStatus.CONFLICT,
            "같은 상품에 이미 겹치는 기간의 판매가격이 있습니다."
    ),
    CATALOG_PRICE_PERIOD_NOT_FOUND(
            "BILLING_CATALOG_PRICE_PERIOD_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "상품의 해당 판매가격 기간을 찾을 수 없습니다."
    ),
    CATALOG_PRICE_PERIOD_REQUIRED(
            "BILLING_CATALOG_PRICE_PERIOD_REQUIRED",
            HttpStatus.CONFLICT,
            "상품은 최소 하나의 판매가격 기간이 있어야 합니다."
    ),
    /**
     * 조직 구독/계약(signstage-docs business/organization-event-discount-pricing-review.md
     * 8장 결정, 2026-09-10)의 오류 코드.
     */
    SUBSCRIPTION_PLAN_FIELDS_INVALID(
            "BILLING_SUBSCRIPTION_PLAN_FIELDS_INVALID",
            HttpStatus.BAD_REQUEST,
            "구독형 플랜은 구독 유형·허용 횟수가 필수이고, 기간형(PERIOD_AND_COUNT)은 기간(6 또는 12개월)도 " +
                    "필수이며 횟수형(COUNT_ONLY)은 기간을 가질 수 없습니다."
    ),
    SUBSCRIPTION_NOT_FOUND("BILLING_SUBSCRIPTION_NOT_FOUND", HttpStatus.NOT_FOUND, "구독 신청을 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_PENDING(
            "BILLING_SUBSCRIPTION_NOT_PENDING",
            HttpStatus.CONFLICT,
            "이미 심사가 끝난 구독 신청입니다."
    ),
    SUBSCRIPTION_NOT_ACTIVE(
            "BILLING_SUBSCRIPTION_NOT_ACTIVE",
            HttpStatus.CONFLICT,
            "사용 중인 구독만 해지를 요청할 수 있습니다."
    ),
    SUBSCRIPTION_NOT_CANCELLATION_REQUESTED(
            "BILLING_SUBSCRIPTION_NOT_CANCELLATION_REQUESTED",
            HttpStatus.CONFLICT,
            "해지 요청 중인 구독이 아닙니다."
    ),
    SUBSCRIPTION_ALREADY_IN_PROGRESS(
            "BILLING_SUBSCRIPTION_ALREADY_IN_PROGRESS",
            HttpStatus.CONFLICT,
            "이미 심사 중이거나 해지 심사 중인 구독 신청이 있습니다."
    ),
    SUBSCRIPTION_PLAN_NOT_SUBSCRIPTION_TYPE(
            "BILLING_SUBSCRIPTION_PLAN_NOT_SUBSCRIPTION_TYPE",
            HttpStatus.CONFLICT,
            "구독형(SUBSCRIPTION) 플랜만 구독 신청할 수 있습니다."
    ),
    SUBSCRIPTION_REQUIRED(
            "BILLING_SUBSCRIPTION_REQUIRED",
            HttpStatus.CONFLICT,
            "이 구독형 플랜을 사용하려면 먼저 구독 신청이 승인되어야 합니다."
    ),
    SUBSCRIPTION_EXHAUSTED(
            "BILLING_SUBSCRIPTION_EXHAUSTED",
            HttpStatus.CONFLICT,
            "구독으로 만들 수 있는 행사 건수를 모두 사용했습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    BillingErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
