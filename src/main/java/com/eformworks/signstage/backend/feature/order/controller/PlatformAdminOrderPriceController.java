package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.service.OrderPriceService;
import com.eformworks.signstage.backend.feature.order.service.PartnerRateCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 수주 가격표 API — 고객 가격표·지역 할증·파트너 단가표(signstage-docs
 * business/platform-order-partner-subcontract-review.md 6장). 등록·기간 종료·삭제는
 * ACTION_ORDER_PRICE_MANAGE 권한이 필요하다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 수주 가격표 API")
@RestController
@RequestMapping("/api/platform-admin/order-prices")
@RequiredArgsConstructor
public class PlatformAdminOrderPriceController {

    private final OrderPriceService orderPriceService;
    private final PartnerRateCardService partnerRateCardService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "고객 가격 목록 조회")
    @GetMapping("/customer-prices")
    public ApiResponse<List<PlatformAdminOrderPriceDto.Response.AdminCustomerPrice>> findCustomerPrices() {
        return ApiResponse.success(orderPriceService.findCustomerPrices(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "고객 가격 등록", description = "플랜(패키지)과 단위 상품(옵션) 중 하나를 지정한다. 같은 대상에 기간이 겹치면 거부한다.")
    @PostMapping("/customer-prices")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminCustomerPrice> createCustomerPrice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.CreateCustomerPrice request
    ) {
        return ApiResponse.success(
                orderPriceService.createCustomerPrice(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "고객 가격 기간 종료")
    @PutMapping("/customer-prices/{customerPriceId}/close")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminCustomerPrice> closeCustomerPrice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long customerPriceId,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        return ApiResponse.success(
                orderPriceService.closeCustomerPrice(customerPriceId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "고객 가격 삭제", description = "견적 줄(범위 변경 포함)에 쓰인 가격은 삭제할 수 없다(ORDER_PRICE_IN_USE).")
    @DeleteMapping("/customer-prices/{customerPriceId}")
    public ApiResponse<Void> deleteCustomerPrice(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long customerPriceId
    ) {
        orderPriceService.deleteCustomerPrice(customerPriceId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "지역 할증 목록 조회")
    @GetMapping("/region-surcharges")
    public ApiResponse<List<PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge>> findRegionSurcharges() {
        return ApiResponse.success(orderPriceService.findRegionSurcharges(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "지역 할증 등록", description = "할증이 없는 권역도 0원으로 등록해야 견적을 만들 수 있다.")
    @PostMapping("/region-surcharges")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge> createRegionSurcharge(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.CreateRegionSurcharge request
    ) {
        return ApiResponse.success(
                orderPriceService.createRegionSurcharge(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "지역 할증 기간 종료")
    @PutMapping("/region-surcharges/{regionSurchargeId}/close")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminRegionSurcharge> closeRegionSurcharge(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long regionSurchargeId,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        return ApiResponse.success(
                orderPriceService.closeRegionSurcharge(regionSurchargeId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "지역 할증 삭제", description = "수주·범위 변경·발주 계산에 쓰인 할증은 삭제할 수 없다(ORDER_PRICE_IN_USE).")
    @DeleteMapping("/region-surcharges/{regionSurchargeId}")
    public ApiResponse<Void> deleteRegionSurcharge(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long regionSurchargeId
    ) {
        orderPriceService.deleteRegionSurcharge(regionSurchargeId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(summary = "파트너 단가표 목록 조회", description = "organizationId가 없는 행이 기본 단가표다.")
    @GetMapping("/partner-rate-cards")
    public ApiResponse<List<PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard>> findPartnerRateCards() {
        return ApiResponse.success(partnerRateCardService.findPartnerRateCards(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "파트너 단가표 등록", description = "organizationId를 지정하면 그 파트너의 예외 단가표다.")
    @PostMapping("/partner-rate-cards")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard> createPartnerRateCard(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.CreatePartnerRateCard request
    ) {
        return ApiResponse.success(
                partnerRateCardService.createPartnerRateCard(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "파트너 단가표 기간 종료")
    @PutMapping("/partner-rate-cards/{partnerRateCardId}/close")
    public ApiResponse<PlatformAdminOrderPriceDto.Response.AdminPartnerRateCard> closePartnerRateCard(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerRateCardId,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        return ApiResponse.success(
                partnerRateCardService.closePartnerRateCard(partnerRateCardId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "파트너 단가표 삭제", description = "발주 금액 계산에 쓰인 단가표는 삭제할 수 없다(ORDER_PRICE_IN_USE).")
    @DeleteMapping("/partner-rate-cards/{partnerRateCardId}")
    public ApiResponse<Void> deletePartnerRateCard(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerRateCardId
    ) {
        partnerRateCardService.deletePartnerRateCard(partnerRateCardId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
