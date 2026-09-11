package com.eformworks.signstage.backend.feature.ceremony.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.ceremony 업무 오류. 1라운드(과금 카탈로그) 코드에 2라운드(Ceremony/CeremonyEvent 본체)
 * 코드를 추가했다. 접근 권한 실패는 별도 코드를 두지 않고 기존 MemberService 관례대로
 * {@code CommonErrorCode.ACCESS_DENIED}를 재사용한다.
 */
public enum CeremonyErrorCode implements ErrorCode {

    BILLING_PLAN_NOT_FOUND("CEREMONY_BILLING_PLAN_NOT_FOUND", HttpStatus.NOT_FOUND, "과금 플랜을 찾을 수 없습니다."),
    BILLING_PLAN_IN_USE(
            "CEREMONY_BILLING_PLAN_IN_USE",
            HttpStatus.CONFLICT,
            "행사에 적용되었거나 조직 구독·할인 오버라이드에 사용된 적이 있는 과금 플랜은 삭제할 수 없습니다."
    ),
    CURRENCY_MISMATCH(
            "CEREMONY_CURRENCY_MISMATCH",
            HttpStatus.CONFLICT,
            "행사와 과금 상품의 통화가 일치하지 않습니다."
    ),
    TAX_POLICY_NOT_FOUND(
            "CEREMONY_TAX_POLICY_NOT_FOUND",
            HttpStatus.CONFLICT,
            "거래일에 적용할 수 있는 세금 정책을 찾을 수 없습니다."
    ),
    /**
     * {@code UnitProduct} 통합 카탈로그(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md, 2026-09-10)의 오류 코드 —
     * 옛 {@code OPTIONAL_FEATURE_*}/{@code CAPACITY_ADDON_*} 카탈로그 코드를 대체했다.
     */
    UNIT_PRODUCT_NOT_FOUND("CEREMONY_UNIT_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "단위 상품을 찾을 수 없습니다."),
    UNIT_PRODUCT_EFFECT_BUNDLE_ONLY(
            "CEREMONY_UNIT_PRODUCT_EFFECT_BUNDLE_ONLY",
            HttpStatus.CONFLICT,
            "이벤트 효과 목록은 EVENT_EFFECT_BUNDLE 종류의 단위 상품에서만 지정할 수 있습니다."
    ),
    CEREMONY_NOT_FOUND("CEREMONY_NOT_FOUND", HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."),
    CEREMONY_EVENT_NOT_FOUND("CEREMONY_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "하위 행사를 찾을 수 없습니다."),
    CEREMONY_EVENT_LIMIT_EXCEEDED(
            "CEREMONY_EVENT_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "이 유형의 하위 행사 생성 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    UNIT_PRODUCT_NOT_PURCHASED(
            "CEREMONY_UNIT_PRODUCT_NOT_PURCHASED",
            HttpStatus.CONFLICT,
            "구매하지 않은 단위 상품은 하위 행사에 적용할 수 없습니다."
    ),
    UNIT_PRODUCT_ALREADY_PURCHASED(
            "CEREMONY_UNIT_PRODUCT_ALREADY_PURCHASED",
            HttpStatus.CONFLICT,
            "이미 구매를 요청했거나 승인된 단위 상품입니다."
    ),
    UNIT_PRODUCT_GROUP_CONFLICT(
            "CEREMONY_UNIT_PRODUCT_GROUP_CONFLICT",
            HttpStatus.CONFLICT,
            "같은 배타 그룹의 단위 상품은 하나만 적용할 수 있습니다."
    ),
    UNIT_PRODUCT_PURCHASE_NOT_FOUND(
            "CEREMONY_UNIT_PRODUCT_PURCHASE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "단위 상품 추가구매 요청을 찾을 수 없습니다."
    ),
    UNIT_PRODUCT_PURCHASE_NOT_PENDING(
            "CEREMONY_UNIT_PRODUCT_PURCHASE_NOT_PENDING",
            HttpStatus.CONFLICT,
            "이미 처리된 단위 상품 추가구매 요청입니다."
    ),
    SIGNER_NOT_FOUND("CEREMONY_SIGNER_NOT_FOUND", HttpStatus.NOT_FOUND, "서명자를 찾을 수 없습니다."),
    CEREMONY_SIGNER_LIMIT_EXCEEDED(
            "CEREMONY_SIGNER_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "서명자 등록 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    SIGNER_IN_USE(
            "CEREMONY_SIGNER_IN_USE",
            HttpStatus.CONFLICT,
            "서명란에 배정됐거나 서명·감사 기록이 남아 있는 서명자는 삭제할 수 없습니다."
    ),
    SIGNER_LOCKED_BY_EVENT(
            "CEREMONY_SIGNER_LOCKED_BY_EVENT",
            HttpStatus.CONFLICT,
            "시작되었거나 종료된 하위 행사에 배정된 서명자는 수정할 수 없습니다."
    ),
    TEMPLATE_NOT_FOUND("CEREMONY_TEMPLATE_NOT_FOUND", HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다."),
    CEREMONY_TEMPLATE_LIMIT_EXCEEDED(
            "CEREMONY_TEMPLATE_LIMIT_EXCEEDED",
            HttpStatus.CONFLICT,
            "템플릿 업로드 한도를 초과했습니다. 플랜을 올리거나 용량을 추가구매해주세요."
    ),
    TEMPLATE_FIELD_NOT_FOUND("CEREMONY_TEMPLATE_FIELD_NOT_FOUND", HttpStatus.NOT_FOUND, "서명란을 찾을 수 없습니다."),
    TEMPLATE_STORAGE_FAILED(
            "CEREMONY_TEMPLATE_STORAGE_FAILED",
            HttpStatus.BAD_GATEWAY,
            "문서 파일 저장에 실패했습니다."
    ),
    TEMPLATE_NOT_IN_CEREMONY(
            "CEREMONY_TEMPLATE_NOT_IN_CEREMONY",
            HttpStatus.CONFLICT,
            "이 행사에 속하지 않은 템플릿은 매핑할 수 없습니다."
    ),
    TEMPLATE_ALREADY_MAPPED(
            "CEREMONY_TEMPLATE_ALREADY_MAPPED",
            HttpStatus.CONFLICT,
            "이미 이 하위 행사에 매핑된 템플릿입니다."
    ),
    TEMPLATE_NOT_COMPLETED(
            "CEREMONY_TEMPLATE_NOT_COMPLETED",
            HttpStatus.CONFLICT,
            "설정 완료되지 않은 문서 양식은 매핑할 수 없습니다."
    ),
    TEMPLATE_MAPPING_NOT_FOUND(
            "CEREMONY_TEMPLATE_MAPPING_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "문서 매핑을 찾을 수 없습니다."
    ),
    TEMPLATE_IN_USE(
            "CEREMONY_TEMPLATE_IN_USE",
            HttpStatus.CONFLICT,
            "이미 하위 행사에 매핑된 문서 양식은 삭제할 수 없습니다."
    ),
    TEMPLATE_LOCKED_BY_EVENT(
            "CEREMONY_TEMPLATE_LOCKED_BY_EVENT",
            HttpStatus.CONFLICT,
            "시작되었거나 종료된 하위 행사에 매핑된 문서 양식은 수정할 수 없습니다."
    ),
    TEMPLATE_LOCKED(
            "CEREMONY_TEMPLATE_LOCKED",
            HttpStatus.CONFLICT,
            "설정 완료된 문서 양식은 서명란을 수정할 수 없습니다."
    ),
    TEMPLATE_DOCUMENT_ROLE_MISMATCH(
            "CEREMONY_TEMPLATE_DOCUMENT_ROLE_MISMATCH",
            HttpStatus.BAD_REQUEST,
            "같은 유형(전시용/서명용)의 문서에서만 서명란을 복제할 수 있습니다."
    ),
    EVENT_LOCKED(
            "CEREMONY_EVENT_LOCKED",
            HttpStatus.CONFLICT,
            "시작되었거나 종료된 하위 행사는 문서 매핑/기본 정보를 바꾸거나 삭제할 수 없습니다."
    ),
    EVENT_INVALID_STATUS_TRANSITION(
            "CEREMONY_EVENT_INVALID_STATUS_TRANSITION",
            HttpStatus.CONFLICT,
            "지금 상태에서는 이 전이를 할 수 없습니다."
    ),
    EVENT_MISSING_DOCUMENT_ROLE(
            "CEREMONY_EVENT_MISSING_DOCUMENT_ROLE",
            HttpStatus.CONFLICT,
            "CONTRACT/EXHIBITION 문서가 각각 1개 이상 매핑돼야 합니다."
    ),
    EVENT_REQUIRED_FIELD_UNASSIGNED(
            "CEREMONY_EVENT_REQUIRED_FIELD_UNASSIGNED",
            HttpStatus.CONFLICT,
            "필수 서명란에 서명자가 배정되지 않았습니다."
    ),
    EVENT_SIGNER_MAPPING_MISMATCH(
            "CEREMONY_EVENT_SIGNER_MAPPING_MISMATCH",
            HttpStatus.CONFLICT,
            "CONTRACT와 EXHIBITION 문서의 필수 서명자 구성이 일치하지 않습니다."
    ),
    PORTAL_EVENT_NOT_FOUND("CEREMONY_PORTAL_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "행사 접속 정보를 찾을 수 없습니다."),
    PORTAL_SIGNER_NOT_FOUND("CEREMONY_PORTAL_SIGNER_NOT_FOUND", HttpStatus.NOT_FOUND, "서명자 접속 정보를 찾을 수 없습니다."),
    PORTAL_FIELD_NOT_MAPPED_TO_EVENT(
            "CEREMONY_PORTAL_FIELD_NOT_MAPPED_TO_EVENT",
            HttpStatus.CONFLICT,
            "이 서명란의 문서는 이 하위 행사에 매핑돼 있지 않습니다."
    ),
    PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER(
            "CEREMONY_PORTAL_FIELD_NOT_ASSIGNED_TO_SIGNER",
            HttpStatus.FORBIDDEN,
            "본인에게 배정되지 않은 서명란입니다."
    ),
    SIGNATURE_INCOMPLETE(
            "CEREMONY_SIGNATURE_INCOMPLETE",
            HttpStatus.CONFLICT,
            "아직 서명하지 않은 필수 서명란이 있습니다."
    ),
    SIGNATURE_ALREADY_COMPLETED(
            "CEREMONY_SIGNATURE_ALREADY_COMPLETED",
            HttpStatus.CONFLICT,
            "이미 서명을 완료했습니다."
    ),
    EVENT_FINISH_CONDITION_NOT_MET(
            "CEREMONY_EVENT_FINISH_CONDITION_NOT_MET",
            HttpStatus.CONFLICT,
            "아직 서명을 완료하지 않은 서명자가 있습니다."
    ),
    EVENT_NOT_FINISHED(
            "CEREMONY_EVENT_NOT_FINISHED",
            HttpStatus.CONFLICT,
            "종료(FINISHED)된 하위 행사만 결과물을 생성할 수 있습니다."
    ),
    RESULTS_ALREADY_GENERATED(
            "CEREMONY_RESULTS_ALREADY_GENERATED",
            HttpStatus.CONFLICT,
            "이미 결과물이 생성됐습니다."
    ),
    RESULT_NOT_FOUND("CEREMONY_RESULT_NOT_FOUND", HttpStatus.NOT_FOUND, "결과물을 찾을 수 없습니다."),
    RESULT_GENERATION_FAILED(
            "CEREMONY_RESULT_GENERATION_FAILED",
            HttpStatus.BAD_GATEWAY,
            "결과물 생성에 실패했습니다."
    ),
    EVENT_NOT_IN_PROGRESS(
            "CEREMONY_EVENT_NOT_IN_PROGRESS",
            HttpStatus.CONFLICT,
            "서명 진행 중(STARTED)인 하위 행사에서만 가능합니다."
    ),
    CEREMONY_ALREADY_COMPLETED(
            "CEREMONY_ALREADY_COMPLETED",
            HttpStatus.CONFLICT,
            "완료된 행사는 더 이상 수정할 수 없습니다."
    ),
    CEREMONY_PLAN_ALREADY_CONFIRMED(
            "CEREMONY_PLAN_ALREADY_CONFIRMED",
            HttpStatus.CONFLICT,
            "플랜이 이미 확정된 행사는 플랜을 바꿀 수 없습니다."
    ),
    CEREMONY_PLAN_NOT_CONFIRMED(
            "CEREMONY_PLAN_NOT_CONFIRMED",
            HttpStatus.CONFLICT,
            "플랜이 확정되지 않은 행사에는 등록할 수 없습니다. 먼저 플랜을 확정해주세요."
    ),
    CEREMONY_PLAN_NOT_SELECTED(
            "CEREMONY_PLAN_NOT_SELECTED",
            HttpStatus.CONFLICT,
            "행사에 과금 플랜을 먼저 선택해주세요."
    ),
    CEREMONY_NOT_DELETABLE(
            "CEREMONY_NOT_DELETABLE",
            HttpStatus.CONFLICT,
            "플랜이 확정됐거나 추가구매 요청·확정 견적이 있는 행사는 삭제할 수 없습니다."
    ),
    BILLING_PLAN_INACTIVE(
            "CEREMONY_BILLING_PLAN_INACTIVE",
            HttpStatus.CONFLICT,
            "사용 중지된 플랜은 선택할 수 없습니다."
    ),
    UNIT_PRODUCT_INACTIVE(
            "CEREMONY_UNIT_PRODUCT_INACTIVE",
            HttpStatus.CONFLICT,
            "사용 중지된 단위 상품은 구매할 수 없습니다."
    ),
    UNIT_PRODUCT_NOT_AVAILABLE_FOR_PLAN(
            "CEREMONY_UNIT_PRODUCT_NOT_AVAILABLE_FOR_PLAN",
            HttpStatus.CONFLICT,
            "이 행사의 플랜에서는 구매할 수 없는 단위 상품입니다."
    ),
    CART_LINE_NOT_FOUND(
            "CEREMONY_CART_LINE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "장바구니에 담긴 항목이 아닙니다."
    ),
    CART_EMPTY(
            "CEREMONY_CART_EMPTY",
            HttpStatus.CONFLICT,
            "장바구니가 비어 있습니다. 먼저 항목을 담아주세요."
    ),
    UNIT_PRODUCT_IN_USE(
            "CEREMONY_UNIT_PRODUCT_IN_USE",
            HttpStatus.CONFLICT,
            "플랜에 포함되었거나 구매·적용된 적이 있는 단위 상품은 삭제할 수 없습니다."
    ),
    SIGNER_EXCEL_INVALID_FORMAT(
            "CEREMONY_SIGNER_EXCEL_INVALID_FORMAT",
            HttpStatus.BAD_REQUEST,
            "엑셀 파일(.xlsx)만 업로드할 수 있습니다."
    ),
    SIGNER_EXCEL_PARSE_FAILED(
            "CEREMONY_SIGNER_EXCEL_PARSE_FAILED",
            HttpStatus.BAD_REQUEST,
            "엑셀 파일을 읽을 수 없습니다. 다운로드한 양식을 사용해주세요."
    ),
    SIGNER_EXCEL_NO_VALID_ROWS(
            "CEREMONY_SIGNER_EXCEL_NO_VALID_ROWS",
            HttpStatus.BAD_REQUEST,
            "업로드할 서명자가 없습니다. 이름이 입력됐는지 확인해주세요."
    ),
    EVENT_FORCE_FINISH_NOT_ALLOWED(
            "CEREMONY_EVENT_FORCE_FINISH_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "진행 중(STARTED)인 테스트 또는 리허설 행사만 강제종료할 수 있습니다."
    ),
    EVENT_BULK_RESET_NOT_ALLOWED(
            "CEREMONY_EVENT_BULK_RESET_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "진행 중(STARTED)인 테스트 또는 리허설 행사만 서명을 일괄 초기화할 수 있습니다."
    ),
    EVENT_MAPPING_CHECK_NOT_ALLOWED(
            "CEREMONY_EVENT_MAPPING_CHECK_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "진행 중(STARTED)인 테스트 또는 리허설 행사만 서명매핑확인을 실행할 수 있습니다."
    ),
    EFFECT_DEFINITION_NOT_FOUND(
            "CEREMONY_EFFECT_DEFINITION_NOT_FOUND", HttpStatus.NOT_FOUND, "이벤트 효과 정의를 찾을 수 없습니다."
    ),
    EFFECT_DEFINITION_CODE_DUPLICATE(
            "CEREMONY_EFFECT_DEFINITION_CODE_DUPLICATE",
            HttpStatus.CONFLICT,
            "이미 등록된 이벤트 효과 코드입니다."
    ),
    EFFECT_DEFINITION_ORDER_GROUP_MISMATCH(
            "CEREMONY_EFFECT_DEFINITION_ORDER_GROUP_MISMATCH",
            HttpStatus.CONFLICT,
            "같은 분류(target, trigger) 안에서만 표시 순서를 바꿀 수 있습니다."
    ),
    EFFECT_DEFINITION_INACTIVE(
            "CEREMONY_EFFECT_DEFINITION_INACTIVE",
            HttpStatus.CONFLICT,
            "비활성화된 이벤트 효과는 선택할 수 없습니다."
    ),
    EFFECT_SELECTION_CLASSIFICATION_MISMATCH(
            "CEREMONY_EFFECT_SELECTION_CLASSIFICATION_MISMATCH",
            HttpStatus.CONFLICT,
            "선택한 효과의 분류(target, trigger)가 요청과 일치하지 않습니다."
    ),
    EFFECT_SELECTION_OPTIONAL_FEATURE_NOT_APPLIED(
            "CEREMONY_EFFECT_SELECTION_OPTIONAL_FEATURE_NOT_APPLIED",
            HttpStatus.CONFLICT,
            "이 효과가 필요로 하는 선택옵션이 이 하위 행사에 적용되어 있지 않습니다."
    ),
    EFFECT_SETTING_NOT_FOUND(
            "CEREMONY_EFFECT_SETTING_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "이 분류(target, trigger)에 선택된 이벤트 효과가 없습니다."
    ),
    EFFECT_RUNTIME_DISABLED(
            "CEREMONY_EFFECT_RUNTIME_DISABLED",
            HttpStatus.CONFLICT,
            "이 효과는 지금 꺼져 있어(runtime OFF) 실행할 수 없습니다."
    ),
    EFFECT_NOT_MANUALLY_TRIGGERABLE(
            "CEREMONY_EFFECT_NOT_MANUALLY_TRIGGERABLE",
            HttpStatus.CONFLICT,
            "이 효과는 수동 실행을 지원하지 않습니다."
    ),
    EFFECT_MANUAL_TRIGGER_RATE_LIMITED(
            "CEREMONY_EFFECT_MANUAL_TRIGGER_RATE_LIMITED",
            HttpStatus.TOO_MANY_REQUESTS,
            "효과 수동 실행이 너무 잦습니다. 잠시 후 다시 시도해주세요."
    ),
    DISCOUNT_VALUE_INVALID(
            "CEREMONY_DISCOUNT_VALUE_INVALID",
            HttpStatus.BAD_REQUEST,
            "할인값은 0 이상이어야 하고, 정률(PERCENT) 할인은 100을 넘을 수 없습니다."
    ),
    CATALOG_PRICE_VALUE_INVALID(
            "CEREMONY_CATALOG_PRICE_VALUE_INVALID",
            HttpStatus.BAD_REQUEST,
            "공급가/판매가는 0 이상이어야 합니다."
    ),
    DISCOUNT_PERIOD_INVALID(
            "CEREMONY_DISCOUNT_PERIOD_INVALID",
            HttpStatus.BAD_REQUEST,
            "종료일은 시작일보다 빠를 수 없습니다."
    ),
    DISCOUNT_PERIOD_OVERLAPPING(
            "CEREMONY_DISCOUNT_PERIOD_OVERLAPPING",
            HttpStatus.CONFLICT,
            "같은 조직×품목에 이미 겹치는 기간의 할인 오버라이드가 있습니다."
    ),
    ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND(
            "CEREMONY_ORGANIZATION_DISCOUNT_PERIOD_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "할인 오버라이드 기간을 찾을 수 없습니다."
    ),
    CATALOG_PRICE_PERIOD_OVERLAPPING(
            "CEREMONY_CATALOG_PRICE_PERIOD_OVERLAPPING",
            HttpStatus.CONFLICT,
            "같은 상품에 이미 겹치는 기간의 판매가격이 있습니다."
    ),
    CATALOG_PRICE_PERIOD_NOT_FOUND(
            "CEREMONY_CATALOG_PRICE_PERIOD_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "상품의 해당 판매가격 기간을 찾을 수 없습니다."
    ),
    CATALOG_PRICE_PERIOD_REQUIRED(
            "CEREMONY_CATALOG_PRICE_PERIOD_REQUIRED",
            HttpStatus.CONFLICT,
            "상품은 최소 하나의 판매가격 기간이 있어야 합니다."
    ),
    CATALOG_ITEM_NOT_ON_SALE(
            "CEREMONY_CATALOG_ITEM_NOT_ON_SALE",
            HttpStatus.CONFLICT,
            "지금은 판매 기간이 아닌 상품입니다."
    ),
    /**
     * 조직 구독/계약(signstage-docs business/organization-event-discount-pricing-review.md
     * 8장 결정, 2026-09-10)의 오류 코드.
     */
    SUBSCRIPTION_PLAN_FIELDS_INVALID(
            "CEREMONY_SUBSCRIPTION_PLAN_FIELDS_INVALID",
            HttpStatus.BAD_REQUEST,
            "구독형 플랜은 구독 유형·허용 횟수가 필수이고, 기간형(PERIOD_AND_COUNT)은 기간(6 또는 12개월)도 " +
                    "필수이며 횟수형(COUNT_ONLY)은 기간을 가질 수 없습니다."
    ),
    SUBSCRIPTION_NOT_FOUND("CEREMONY_SUBSCRIPTION_NOT_FOUND", HttpStatus.NOT_FOUND, "구독 신청을 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_PENDING(
            "CEREMONY_SUBSCRIPTION_NOT_PENDING",
            HttpStatus.CONFLICT,
            "이미 심사가 끝난 구독 신청입니다."
    ),
    SUBSCRIPTION_NOT_ACTIVE(
            "CEREMONY_SUBSCRIPTION_NOT_ACTIVE",
            HttpStatus.CONFLICT,
            "사용 중인 구독만 해지를 요청할 수 있습니다."
    ),
    SUBSCRIPTION_NOT_CANCELLATION_REQUESTED(
            "CEREMONY_SUBSCRIPTION_NOT_CANCELLATION_REQUESTED",
            HttpStatus.CONFLICT,
            "해지 요청 중인 구독이 아닙니다."
    ),
    SUBSCRIPTION_ALREADY_IN_PROGRESS(
            "CEREMONY_SUBSCRIPTION_ALREADY_IN_PROGRESS",
            HttpStatus.CONFLICT,
            "이미 심사 중이거나 해지 심사 중인 구독 신청이 있습니다."
    ),
    SUBSCRIPTION_PLAN_NOT_SUBSCRIPTION_TYPE(
            "CEREMONY_SUBSCRIPTION_PLAN_NOT_SUBSCRIPTION_TYPE",
            HttpStatus.CONFLICT,
            "구독형(SUBSCRIPTION) 플랜만 구독 신청할 수 있습니다."
    ),
    SUBSCRIPTION_REQUIRED(
            "CEREMONY_SUBSCRIPTION_REQUIRED",
            HttpStatus.CONFLICT,
            "이 구독형 플랜을 사용하려면 먼저 구독 신청이 승인되어야 합니다."
    ),
    SUBSCRIPTION_EXHAUSTED(
            "CEREMONY_SUBSCRIPTION_EXHAUSTED",
            HttpStatus.CONFLICT,
            "구독으로 만들 수 있는 행사 건수를 모두 사용했습니다."
    ),
    /**
     * 확정 견적(signstage-docs business/currency-tax-internationalization-review.md 9/10장,
     * 2026-09-10 구현)의 오류 코드.
     */
    QUOTE_NOT_FOUND("CEREMONY_QUOTE_NOT_FOUND", HttpStatus.NOT_FOUND, "확정 견적을 찾을 수 없습니다."),
    QUOTE_ALREADY_VOID(
            "CEREMONY_QUOTE_ALREADY_VOID",
            HttpStatus.CONFLICT,
            "이미 무효화된 견적입니다."
    ),
    QUOTE_EMPTY(
            "CEREMONY_QUOTE_EMPTY",
            HttpStatus.CONFLICT,
            "플랜이 없거나 청구할 항목이 없는 행사는 견적을 확정할 수 없습니다."
    ),

    /**
     * 파트너→실고객 고객 견적서(signstage-docs
     * business/partner-customer-quote-design-review.md, 2026-09-11 구현)의 오류 코드.
     */
    MARGIN_VALUE_INVALID(
            "CEREMONY_MARGIN_VALUE_INVALID",
            HttpStatus.BAD_REQUEST,
            "마진 값이 올바르지 않습니다. 0 이상이어야 합니다."
    ),
    MARGIN_NOT_SET(
            "CEREMONY_MARGIN_NOT_SET",
            HttpStatus.CONFLICT,
            "마진이 설정되지 않았습니다. 조직 기본 마진 또는 행사별 마진을 먼저 설정해주세요."
    ),
    CUSTOMER_MARGIN_OVERRIDE_NOT_SET(
            "CEREMONY_CUSTOMER_MARGIN_OVERRIDE_NOT_SET",
            HttpStatus.NOT_FOUND,
            "이 행사에 설정된 마진 override가 없습니다."
    ),
    CUSTOMER_QUOTE_NOT_FOUND("CEREMONY_CUSTOMER_QUOTE_NOT_FOUND", HttpStatus.NOT_FOUND, "고객 견적서를 찾을 수 없습니다."),
    CUSTOMER_QUOTE_EMPTY(
            "CEREMONY_CUSTOMER_QUOTE_EMPTY",
            HttpStatus.CONFLICT,
            "청구할 항목이 없는 행사는 고객 견적서를 만들 수 없습니다."
    ),
    CUSTOMER_QUOTE_PRICE_INVALID(
            "CEREMONY_CUSTOMER_QUOTE_PRICE_INVALID",
            HttpStatus.BAD_REQUEST,
            "실고객 청구 단가가 올바르지 않습니다. 0 이상이어야 합니다."
    ),
    CUSTOMER_QUOTE_ITEM_NOT_EQUIPMENT_PERSONNEL(
            "CEREMONY_CUSTOMER_QUOTE_ITEM_NOT_EQUIPMENT_PERSONNEL",
            HttpStatus.BAD_REQUEST,
            "장비/인력(태블릿·현장지원 등) 카탈로그에 있는 상품만 고객 정산 줄로 담을 수 있습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    CeremonyErrorCode(String code, HttpStatus httpStatus, String message) {
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
