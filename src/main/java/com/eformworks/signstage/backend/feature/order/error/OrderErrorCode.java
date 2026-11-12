package com.eformworks.signstage.backend.feature.order.error;

import com.eformworks.signstage.backend.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {

    CUSTOMER_NOT_FOUND("ORDER_CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
    CUSTOMER_INACTIVE("ORDER_CUSTOMER_INACTIVE", HttpStatus.CONFLICT, "사용 중지된 고객으로는 견적을 만들 수 없습니다."),
    PRICE_NOT_FOUND("ORDER_PRICE_NOT_FOUND", HttpStatus.NOT_FOUND, "가격 기간을 찾을 수 없습니다."),
    PRICE_TARGET_INVALID(
            "ORDER_PRICE_TARGET_INVALID",
            HttpStatus.BAD_REQUEST,
            "고객 가격은 플랜과 단위 상품 중 하나만 지정해야 합니다."
    ),
    PRICE_PERIOD_INVALID("ORDER_PRICE_PERIOD_INVALID", HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다."),
    PRICE_IN_USE(
            "ORDER_PRICE_IN_USE",
            HttpStatus.CONFLICT,
            "견적·수주·발주 계산에 쓰인 가격은 삭제할 수 없습니다. 기간을 종료해주세요."
    ),
    PRICE_PERIOD_OVERLAP(
            "ORDER_PRICE_PERIOD_OVERLAP",
            HttpStatus.CONFLICT,
            "같은 대상에 기간이 겹치는 가격이 있습니다. 기존 가격 기간을 먼저 종료해주세요."
    ),
    CUSTOMER_PRICE_NOT_FOUND(
            "ORDER_CUSTOMER_PRICE_NOT_FOUND",
            HttpStatus.CONFLICT,
            "견적 기준일에 유효한 고객 가격이 없는 플랜 또는 단위 상품이 있습니다."
    ),
    REGION_SURCHARGE_NOT_FOUND(
            "ORDER_REGION_SURCHARGE_NOT_FOUND",
            HttpStatus.CONFLICT,
            "견적 기준일에 유효한 지역 할증이 없습니다."
    ),
    PARTNER_RATE_CARD_NOT_FOUND(
            "ORDER_PARTNER_RATE_CARD_NOT_FOUND",
            HttpStatus.CONFLICT,
            "기준일에 유효한 파트너 단가표가 없습니다."
    ),
    CURRENCY_MISMATCH("ORDER_CURRENCY_MISMATCH", HttpStatus.CONFLICT, "가격표의 통화가 수주 통화와 다릅니다."),
    MARGIN_BELOW_MINIMUM(
            "ORDER_MARGIN_BELOW_MINIMUM",
            HttpStatus.CONFLICT,
            "고객 공급가에서 발주 금액을 뺀 마진이 최소 마진 기준보다 작습니다."
    ),
    OPTION_DUPLICATED("ORDER_OPTION_DUPLICATED", HttpStatus.BAD_REQUEST, "같은 단위 상품을 옵션에 두 번 넣을 수 없습니다."),
    SALES_ORDER_NOT_FOUND("ORDER_SALES_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."),
    SALES_ORDER_NOT_QUOTED(
            "ORDER_SALES_ORDER_NOT_QUOTED",
            HttpStatus.CONFLICT,
            "견적 상태인 수주만 수정·수락할 수 있습니다."
    ),
    SALES_ORDER_NOT_ACCEPTED(
            "ORDER_SALES_ORDER_NOT_ACCEPTED",
            HttpStatus.CONFLICT,
            "고객이 수락한 수주만 발주할 수 있습니다."
    ),
    SALES_QUOTE_EXPIRED("ORDER_SALES_QUOTE_EXPIRED", HttpStatus.CONFLICT, "견적 유효기간이 지났습니다."),
    SALES_ORDER_HAS_ACCEPTED_PARTNER_ORDER(
            "ORDER_SALES_ORDER_HAS_ACCEPTED_PARTNER_ORDER",
            HttpStatus.CONFLICT,
            "파트너가 수락한 발주가 있는 수주는 취소할 수 없습니다. 먼저 수락한 발주를 취소(파트너 교체)하세요."
    ),
    PARTNER_ORDER_NOT_FOUND("ORDER_PARTNER_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "발주를 찾을 수 없습니다."),
    PARTNER_ORDER_ALREADY_ACTIVE(
            "ORDER_PARTNER_ORDER_ALREADY_ACTIVE",
            HttpStatus.CONFLICT,
            "이 수주에 응답 대기 중이거나 수락된 발주가 이미 있습니다."
    ),
    PARTNER_ORDER_NOT_ISSUED(
            "ORDER_PARTNER_ORDER_NOT_ISSUED",
            HttpStatus.CONFLICT,
            "응답 대기 중인 발주만 수락·거절·취소할 수 있습니다."
    ),
    PARTNER_ORDER_EXPIRED("ORDER_PARTNER_ORDER_EXPIRED", HttpStatus.CONFLICT, "응답 기한이 지난 발주입니다."),
    PARTNER_ORDER_NOT_CANCELLABLE(
            "ORDER_PARTNER_ORDER_NOT_CANCELLABLE",
            HttpStatus.CONFLICT,
            "응답 대기 중이거나 수락된 발주만 취소할 수 있습니다."
    ),
    PARTNER_ORDER_CEREMONY_COMPLETED(
            "ORDER_PARTNER_ORDER_CEREMONY_COMPLETED",
            HttpStatus.CONFLICT,
            "행사가 완료된 발주는 취소할 수 없습니다. 검수 감액이나 귀책 상계로 처리합니다."
    ),
    PARTNER_ORDER_DUE_INVALID("ORDER_PARTNER_ORDER_DUE_INVALID", HttpStatus.BAD_REQUEST, "응답 기한은 현재 이후여야 합니다."),
    PARTNER_ADJUSTMENT_REASON_REQUIRED(
            "ORDER_PARTNER_ADJUSTMENT_REASON_REQUIRED",
            HttpStatus.BAD_REQUEST,
            "발주 금액을 조정하면 사유를 입력해야 합니다."
    ),
    PARTNER_NOT_ELIGIBLE(
            "ORDER_PARTNER_NOT_ELIGIBLE",
            HttpStatus.CONFLICT,
            "발주를 받을 수 없는 파트너입니다. 활성 조직이어야 하고 외주 정보(사업자·확인된 정산 계좌·계약 동의)를 모두 채워야 합니다."
    ),
    INSPECTION_NOT_FOUND("ORDER_INSPECTION_NOT_FOUND", HttpStatus.NOT_FOUND, "검수를 찾을 수 없습니다."),
    INSPECTION_NOT_PENDING("ORDER_INSPECTION_NOT_PENDING", HttpStatus.CONFLICT, "검수 대기 중인 건만 승인할 수 있습니다."),
    INSPECTION_DEADLINE_PASSED(
            "ORDER_INSPECTION_DEADLINE_PASSED",
            HttpStatus.CONFLICT,
            "검수 기한이 지나 승인으로 간주된 건입니다. 하자·감액을 기록할 수 없습니다."
    ),
    INSPECTION_DEDUCTION_INVALID(
            "ORDER_INSPECTION_DEDUCTION_INVALID",
            HttpStatus.BAD_REQUEST,
            "감액은 0 이상, 발주 금액 이하여야 합니다."
    ),
    INSPECTION_DEDUCTION_REASON_REQUIRED(
            "ORDER_INSPECTION_DEDUCTION_REASON_REQUIRED",
            HttpStatus.BAD_REQUEST,
            "감액하면 사유를 입력해야 합니다."
    ),
    INSPECTION_DEDUCTION_TYPE_REQUIRED(
            "ORDER_INSPECTION_DEDUCTION_TYPE_REQUIRED",
            HttpStatus.BAD_REQUEST,
            "감액하면 감액 유형(계약 기준표)을 골라야 합니다."
    ),
    INSPECTION_LARGE_DEDUCTION_FORBIDDEN(
            "ORDER_INSPECTION_LARGE_DEDUCTION_FORBIDDEN",
            HttpStatus.FORBIDDEN,
            "발주 금액의 30%를 넘는 감액은 PLATFORM_SUPER만 승인할 수 있습니다."
    ),
    INSPECTION_NOT_DISPUTABLE(
            "ORDER_INSPECTION_NOT_DISPUTABLE",
            HttpStatus.CONFLICT,
            "이의를 제기할 수 없는 검수입니다. 감액된 건에 한해 승인일부터 기간 안에 한 번 제기할 수 있습니다."
    ),
    INSPECTION_DISPUTE_NOT_OPEN(
            "ORDER_INSPECTION_DISPUTE_NOT_OPEN",
            HttpStatus.CONFLICT,
            "이의 제기 중인 검수만 조정할 수 있습니다."
    ),
    OFFSET_NOT_FOUND("ORDER_OFFSET_NOT_FOUND", HttpStatus.NOT_FOUND, "상계를 찾을 수 없습니다."),
    OFFSET_PARTNER_ORDER_INVALID(
            "ORDER_OFFSET_PARTNER_ORDER_INVALID",
            HttpStatus.CONFLICT,
            "파트너가 수락한 발주에만 귀책 상계를 등록할 수 있습니다."
    ),
    OFFSET_AMOUNT_INVALID(
            "ORDER_OFFSET_AMOUNT_INVALID",
            HttpStatus.BAD_REQUEST,
            "상계 금액은 0보다 크고 발주 금액 이하여야 합니다."
    ),
    OFFSET_NOT_CANCELLABLE(
            "ORDER_OFFSET_NOT_CANCELLABLE",
            HttpStatus.CONFLICT,
            "정산에 반영되지 않은 상계만 취소할 수 있습니다. 이월 상계는 이월을 만든 지급 건을 취소하면 함께 취소됩니다."
    ),
    PAYOUT_NOT_FOUND("ORDER_PAYOUT_NOT_FOUND", HttpStatus.NOT_FOUND, "지급 건을 찾을 수 없습니다."),
    PAYOUT_MONTH_NOT_CLOSED(
            "ORDER_PAYOUT_MONTH_NOT_CLOSED",
            HttpStatus.BAD_REQUEST,
            "지난달까지만 정산할 수 있습니다."
    ),
    PAYOUT_NOT_CONFIRMED(
            "ORDER_PAYOUT_NOT_CONFIRMED",
            HttpStatus.CONFLICT,
            "지급 대기 중인 건만 지급 완료·취소할 수 있습니다."
    ),
    PAYOUT_PAID_DATE_INVALID("ORDER_PAYOUT_PAID_DATE_INVALID", HttpStatus.BAD_REQUEST, "지급일은 오늘 이후일 수 없습니다."),
    PAYOUT_APPROVAL_REQUIRED(
            "ORDER_PAYOUT_APPROVAL_REQUIRED",
            HttpStatus.CONFLICT,
            "고액 지급은 PLATFORM_SUPER가 승인한 뒤에 지급 완료로 기록할 수 있습니다."
    ),
    PAYOUT_APPROVAL_NOT_NEEDED(
            "ORDER_PAYOUT_APPROVAL_NOT_NEEDED",
            HttpStatus.CONFLICT,
            "승인이 필요 없거나 이미 승인된 지급 건입니다."
    ),
    PAYOUT_APPROVER_IS_CREATOR(
            "ORDER_PAYOUT_APPROVER_IS_CREATOR",
            HttpStatus.CONFLICT,
            "정산을 만든 사람은 그 지급을 승인할 수 없습니다. 다른 PLATFORM_SUPER가 승인해야 합니다."
    ),
    PAYOUT_CARRY_OVER_APPLIED(
            "ORDER_PAYOUT_CARRY_OVER_APPLIED",
            HttpStatus.CONFLICT,
            "이 지급에서 이월된 상계가 이미 다른 지급에 반영됐습니다. 그 지급을 먼저 취소해주세요."
    ),
    INVOICE_NOT_FOUND("ORDER_INVOICE_NOT_FOUND", HttpStatus.NOT_FOUND, "청구를 찾을 수 없습니다."),
    INVOICE_SALES_ORDER_NOT_ACCEPTED(
            "ORDER_INVOICE_SALES_ORDER_NOT_ACCEPTED",
            HttpStatus.CONFLICT,
            "고객이 수락한 수주만 청구할 수 있습니다."
    ),
    INVOICE_ALREADY_ISSUED("ORDER_INVOICE_ALREADY_ISSUED", HttpStatus.CONFLICT, "이 수주에 같은 종류의 청구가 이미 있습니다."),
    INVOICE_ADVANCE_DISABLED("ORDER_INVOICE_ADVANCE_DISABLED", HttpStatus.CONFLICT, "선금 비율이 0이라 선금을 청구하지 않습니다."),
    INVOICE_ADVANCE_AFTER_BALANCE(
            "ORDER_INVOICE_ADVANCE_AFTER_BALANCE",
            HttpStatus.CONFLICT,
            "잔금을 이미 청구한 수주에는 선금을 청구할 수 없습니다."
    ),
    INVOICE_BALANCE_BEFORE_COMPLETION(
            "ORDER_INVOICE_BALANCE_BEFORE_COMPLETION",
            HttpStatus.CONFLICT,
            "행사가 완료된 뒤에 잔금을 청구할 수 있습니다."
    ),
    INVOICE_NOTHING_TO_BILL("ORDER_INVOICE_NOTHING_TO_BILL", HttpStatus.CONFLICT, "청구할 잔액이 없습니다."),
    INVOICE_NOT_ISSUED("ORDER_INVOICE_NOT_ISSUED", HttpStatus.CONFLICT, "입금 대기 중인 청구만 입금 기록·취소할 수 있습니다."),
    INVOICE_CANCELLED("ORDER_INVOICE_CANCELLED", HttpStatus.CONFLICT, "취소된 청구입니다."),
    INVOICE_BALANCE_EXISTS(
            "ORDER_INVOICE_BALANCE_EXISTS",
            HttpStatus.CONFLICT,
            "잔금 청구가 있어 선금 청구를 취소할 수 없습니다. 잔금 청구를 먼저 취소해주세요."
    ),
    INVOICE_DUE_DATE_INVALID("ORDER_INVOICE_DUE_DATE_INVALID", HttpStatus.BAD_REQUEST, "입금 기한은 오늘 이후여야 합니다."),
    INVOICE_PAID_DATE_INVALID("ORDER_INVOICE_PAID_DATE_INVALID", HttpStatus.BAD_REQUEST, "입금일은 오늘 이후일 수 없습니다."),
    SALES_ORDER_HAS_ACTIVE_INVOICE(
            "ORDER_SALES_ORDER_HAS_ACTIVE_INVOICE",
            HttpStatus.CONFLICT,
            "청구가 있는 수주는 취소할 수 없습니다. 입금 전 청구를 먼저 취소해주세요."
    ),
    SALES_ORDER_CHANGE_NOT_FOUND("ORDER_SALES_ORDER_CHANGE_NOT_FOUND", HttpStatus.NOT_FOUND, "범위 변경을 찾을 수 없습니다."),
    SALES_ORDER_CHANGE_IN_PROGRESS(
            "ORDER_SALES_ORDER_CHANGE_IN_PROGRESS",
            HttpStatus.CONFLICT,
            "진행 중인 범위 변경이 있습니다. 반영되거나 취소된 뒤에 다시 제안하거나 발주하세요."
    ),
    SALES_ORDER_CHANGE_NOT_PROPOSED(
            "ORDER_SALES_ORDER_CHANGE_NOT_PROPOSED",
            HttpStatus.CONFLICT,
            "진행 중인 범위 변경만 수락·동의·거절·취소할 수 있습니다."
    ),
    SALES_ORDER_CHANGE_PARTNER_ORDER_PENDING(
            "ORDER_SALES_ORDER_CHANGE_PARTNER_ORDER_PENDING",
            HttpStatus.CONFLICT,
            "응답 대기 중인 발주가 있습니다. 발주를 취소하거나 응답을 받은 뒤에 범위를 바꾸세요."
    ),
    SALES_ORDER_CHANGE_CEREMONY_COMPLETED(
            "ORDER_SALES_ORDER_CHANGE_CEREMONY_COMPLETED",
            HttpStatus.CONFLICT,
            "행사가 완료된 수주는 범위를 바꿀 수 없습니다. 검수 감액이나 귀책 상계로 처리합니다."
    ),
    SALES_ORDER_CHANGE_BALANCE_INVOICED(
            "ORDER_SALES_ORDER_CHANGE_BALANCE_INVOICED",
            HttpStatus.CONFLICT,
            "잔금을 청구한 수주는 범위를 바꿀 수 없습니다. 잔금 청구를 먼저 취소해주세요."
    ),
    SALES_ORDER_CHANGE_BELOW_INVOICED(
            "ORDER_SALES_ORDER_CHANGE_BELOW_INVOICED",
            HttpStatus.CONFLICT,
            "변경 후 수주 금액이 이미 청구한 금액보다 작습니다. 청구를 먼저 조정해주세요."
    ),
    SALES_ORDER_CHANGE_ALREADY_RESPONDED(
            "ORDER_SALES_ORDER_CHANGE_ALREADY_RESPONDED",
            HttpStatus.CONFLICT,
            "이미 응답한 범위 변경입니다."
    ),
    SALES_ORDER_CHANGE_CUSTOMER_ALREADY_ACCEPTED(
            "ORDER_SALES_ORDER_CHANGE_CUSTOMER_ALREADY_ACCEPTED",
            HttpStatus.CONFLICT,
            "고객 수락을 이미 기록했습니다."
    ),
    SCOPE_REQUEST_NOT_FOUND("ORDER_SCOPE_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "범위 변경 요청을 찾을 수 없습니다."),
    SCOPE_REQUEST_CLOSED("ORDER_SCOPE_REQUEST_CLOSED", HttpStatus.CONFLICT, "이미 처리한 범위 변경 요청입니다."),
    SCOPE_REQUEST_NOT_ALLOWED(
            "ORDER_SCOPE_REQUEST_NOT_ALLOWED",
            HttpStatus.CONFLICT,
            "수락한 발주의 행사가 끝나기 전에만 범위 변경을 요청할 수 있습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    OrderErrorCode(String code, HttpStatus httpStatus, String message) {
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
