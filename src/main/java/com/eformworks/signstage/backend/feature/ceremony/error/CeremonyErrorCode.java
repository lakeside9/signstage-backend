package com.eformworks.signstage.backend.feature.ceremony.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * feature.ceremony 업무 오류. 과금 카탈로그·조직 할인·구독 코드는 2026-11-11
 * {@code BillingErrorCode}(feature.billing)로 분리했다. 접근 권한 실패는 별도 코드를 두지 않고
 * 기존 MemberService 관례대로 {@code CommonErrorCode.ACCESS_DENIED}를 재사용한다.
 */
public enum CeremonyErrorCode implements ErrorCode {

    TEMPLATE_DISABLED("CEREMONY_TEMPLATE_DISABLED", HttpStatus.BAD_REQUEST, "사용 안함으로 설정된 문서 양식입니다."),
    SIGNER_DISABLED("CEREMONY_SIGNER_DISABLED", HttpStatus.BAD_REQUEST, "사용 안함 서명자는 새로 배치하거나 행사에 연결할 수 없습니다."),

    INVALID_SCHEDULE("CEREMONY_INVALID_SCHEDULE", HttpStatus.BAD_REQUEST, "종료일시는 시작일시보다 빠를 수 없습니다."),

    CURRENCY_MISMATCH(
            "CEREMONY_CURRENCY_MISMATCH",
            HttpStatus.CONFLICT,
            "행사와 과금 상품의 통화가 일치하지 않습니다."
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
    UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID(
            "CEREMONY_UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID",
            HttpStatus.CONFLICT,
            "이 단위 상품은 수량이 아니라 행사당 1회만 담을 수 있습니다."
    ),
    UNIT_PRODUCT_MAX_QUANTITY_EXCEEDED(
            "CEREMONY_UNIT_PRODUCT_MAX_QUANTITY_EXCEEDED",
            HttpStatus.CONFLICT,
            "설정된 최대 구매 수량을 초과했습니다."
    ),
    UNIT_PRODUCT_SALE_UNIT_QUANTITY_INVALID(
            "CEREMONY_UNIT_PRODUCT_SALE_UNIT_QUANTITY_INVALID",
            HttpStatus.CONFLICT,
            "이 단위 상품은 지정된 판매 단위 수량의 배수로만 구매할 수 있습니다."
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
    UNIT_PRODUCT_PURCHASE_NOT_APPROVED(
            "CEREMONY_UNIT_PRODUCT_PURCHASE_NOT_APPROVED",
            HttpStatus.CONFLICT,
            "승인된 구매만 취소할 수 있습니다."
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
            "하위 행사에 매핑된 문서 양식은 수정할 수 없습니다."
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
            "종료(FINISHED) 또는 강제종료(FORCE_FINISHED)된 하위 행사만 결과물을 생성할 수 있습니다."
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
    CEREMONY_CANCELLED(
            "CEREMONY_CANCELLED",
            HttpStatus.CONFLICT,
            "고객 사정으로 취소된 행사는 더 이상 수정할 수 없습니다."
    ),
    CEREMONY_EVENT_STARTED(
            "CEREMONY_EVENT_STARTED",
            HttpStatus.CONFLICT,
            "서명이 진행 중인 하위 행사가 있어 행사를 취소할 수 없습니다. 하위 행사를 먼저 마쳐주세요."
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
    CEREMONY_PLATFORM_ORDER_RESTRICTED(
            "CEREMONY_PLATFORM_ORDER_RESTRICTED",
            HttpStatus.CONFLICT,
            "플랫폼 수주 행사에서는 플랜 변경·추가구매·고객 견적·현장지원 요청을 할 수 없습니다. 범위 변경은 플랫폼에 문의해주세요."
    ),
    UNIT_PRODUCT_PURCHASE_PLATFORM_ORDER_NOT_CANCELLABLE(
            "CEREMONY_UNIT_PRODUCT_PURCHASE_PLATFORM_ORDER_NOT_CANCELLABLE",
            HttpStatus.CONFLICT,
            "플랫폼 수주 행사의 구매는 수주 범위라 취소할 수 없습니다. 범위는 수주 상세의 범위 변경으로, 행사 취소는 발주·수주 취소로 처리해주세요."
    ),
    CEREMONY_PLATFORM_ORDER_CONTRACT_FIELD_LOCKED(
            "CEREMONY_PLATFORM_ORDER_CONTRACT_FIELD_LOCKED",
            HttpStatus.CONFLICT,
            "플랫폼 수주 행사의 제목·장소·일정은 계약 조건이라 플랫폼만 바꿀 수 있습니다."
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
            "진행 중(STARTED)인 행사만 강제종료할 수 있습니다."
    ),
    EVENT_BULK_RESET_NOT_ALLOWED(
            "CEREMONY_EVENT_BULK_RESET_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "본행사는 작성 중 또는 준비 상태에서만, 테스트 또는 리허설 행사는 진행 중까지 서명을 일괄 초기화할 수 있습니다."
    ),
    EVENT_MAPPING_CHECK_NOT_ALLOWED(
            "CEREMONY_EVENT_MAPPING_CHECK_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "본행사는 작성 중 또는 준비 상태에서만, 테스트 또는 리허설 행사는 진행 중까지 서명매핑확인을 실행할 수 있습니다."
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
    CATALOG_ITEM_NOT_ON_SALE(
            "CEREMONY_CATALOG_ITEM_NOT_ON_SALE",
            HttpStatus.CONFLICT,
            "지금은 판매 기간이 아닌 상품입니다."
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
    MARGIN_PERIOD_INVALID("CEREMONY_MARGIN_PERIOD_INVALID", HttpStatus.BAD_REQUEST, "시작일을 입력하고 1000-01-01~9999-12-31 범위로 지정해주세요. 종료일은 시작일보다 빠를 수 없으며 생략 시 9999-12-31입니다."),
    MARGIN_PERIOD_OVERLAP("CEREMONY_MARGIN_PERIOD_OVERLAP", HttpStatus.CONFLICT, "동일 파트너의 마진 적용 기간은 중복될 수 없습니다. 기존 정책의 종료일을 먼저 조정해주세요."),
    MARGIN_PERIOD_START_NOT_AFTER_LAST_END("CEREMONY_MARGIN_PERIOD_START_NOT_AFTER_LAST_END", HttpStatus.CONFLICT,
            "새 정책의 시작일은 기존 정책의 가장 늦은 종료일보다 뒤여야 합니다. 기존 종료일이 9999-12-31이면 먼저 종료일을 조정해주세요."),
    MARGIN_POLICY_NOT_FOUND("CEREMONY_MARGIN_POLICY_NOT_FOUND", HttpStatus.NOT_FOUND, "마진 정책을 찾을 수 없습니다."),
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
            "장비/인력(태블릿·현장지원 등) 카탈로그에 있는 상품만 고객 견적 줄로 담을 수 있습니다."
    ),
    /**
     * 행사별 1:1 문의(CeremonyInquiry) — signstage-docs
     * business/partner-support-center-review.md 5장(2026-09-12). Signer/Template과 같이
     * {@code Ceremony} 직속이라 이 코드에 둔다.
     */
    CEREMONY_INQUIRY_NOT_FOUND("CEREMONY_INQUIRY_NOT_FOUND", HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    CEREMONY_INQUIRY_ALREADY_CLOSED(
            "CEREMONY_INQUIRY_ALREADY_CLOSED",
            HttpStatus.CONFLICT,
            "종료된 문의입니다. 답변이 더 필요하면 새 문의를 등록해주세요."
    ),
    /**
     * 현장지원 요청(관리자 견적) 협상 — signstage-docs
     * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절
     * (2026-09-12). CeremonyInquiry와 같이 Ceremony 직속이라 이 코드에 둔다.
     */
    CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_FOUND("CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "플랫폼 운영사 현장지원 요청을 찾을 수 없습니다."),
    CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_REQUESTED(
            "CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_REQUESTED",
            HttpStatus.CONFLICT,
            "아직 요청 상태가 아니거나 이미 견적을 매긴 요청입니다."
    ),
    CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_QUOTED(
            "CEREMONY_ONSITE_SUPPORT_REQUEST_NOT_QUOTED",
            HttpStatus.CONFLICT,
            "견적이 아직 없거나 이미 응답한 요청입니다."
    ),
    CEREMONY_ONSITE_SUPPORT_REQUEST_ANCHOR_NOT_FOUND(
            "CEREMONY_ONSITE_SUPPORT_REQUEST_ANCHOR_NOT_FOUND",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "플랫폼 운영사 현장지원 요청 전용 단위 상품이 카탈로그에 등록되어 있지 않습니다. 플랫폼 관리자에게 문의해주세요."
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
