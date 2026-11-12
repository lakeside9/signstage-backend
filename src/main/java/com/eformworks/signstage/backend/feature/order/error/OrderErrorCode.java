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
            "파트너가 수락한 발주가 있는 수주는 취소할 수 없습니다. 발주 취소 절차는 아직 지원하지 않습니다."
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
    PAYOUT_PAID_DATE_INVALID("ORDER_PAYOUT_PAID_DATE_INVALID", HttpStatus.BAD_REQUEST, "지급일은 오늘 이후일 수 없습니다.");

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
