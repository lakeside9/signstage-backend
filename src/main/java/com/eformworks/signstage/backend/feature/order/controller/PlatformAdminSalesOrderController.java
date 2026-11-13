package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.CustomerCancellationDto;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminPartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminSalesOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import com.eformworks.signstage.backend.feature.order.service.CustomerCancellationService;
import com.eformworks.signstage.backend.feature.order.service.PartnerOrderService;
import com.eformworks.signstage.backend.feature.order.service.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 수주(고객 견적·계약)와 그 수주에 대한 발주 발급 API(signstage-docs
 * business/platform-order-partner-subcontract-review.md 3장 1~4단계). 수주 쓰기는
 * ACTION_SALES_ORDER_MANAGE, 발주 발급은 ACTION_PARTNER_ORDER_ISSUE 권한이 필요하다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 수주·발주 API")
@RestController
@RequestMapping("/api/platform-admin/sales-orders")
@RequiredArgsConstructor
public class PlatformAdminSalesOrderController {

    private final SalesOrderService salesOrderService;
    private final CustomerCancellationService customerCancellationService;
    private final PartnerOrderService partnerOrderService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "수주 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderSummary>> findSalesOrders(
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(salesOrderService.findSalesOrders(status, customerId, pageable)), traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "수주 상세 조회", description = "견적 줄, 고객 견적 링크 토큰, 발주 이력을 함께 돌려준다.")
    @GetMapping("/{salesOrderId}")
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> retrieveSalesOrder(
            @PathVariable Long salesOrderId
    ) {
        return ApiResponse.success(salesOrderService.retrieveSalesOrder(salesOrderId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "수주 견적 작성",
            description = "고객 가격표로 금액을 계산하고, 기본 파트너 단가표로 계산한 예상 발주 금액이 최소 마진을 넘는지 검사한다."
    )
    @PostMapping
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> createSalesOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminSalesOrderDto.Request.CreateSalesOrder request
    ) {
        return ApiResponse.success(
                salesOrderService.createSalesOrder(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "수주 견적 수정", description = "견적(QUOTED) 상태에서만 수정한다. 전체 내용을 다시 보낸다.")
    @PutMapping("/{salesOrderId}/quote")
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> updateQuote(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminSalesOrderDto.Request.SaveQuote request
    ) {
        return ApiResponse.success(
                salesOrderService.updateQuote(salesOrderId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "고객 수락 대리 기록", description = "메일·전화로 받은 고객 수락을 기록한다. 고객은 견적 링크로 직접 수락할 수도 있다.")
    @PostMapping("/{salesOrderId}/acceptance")
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> recordAcceptance(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminSalesOrderDto.Request.RecordAcceptance request
    ) {
        return ApiResponse.success(
                salesOrderService.recordAcceptance(
                        salesOrderId, currentUser.userId(), currentUser.platformRole(), request.getAcceptedByName()
                ),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "고객 취소 미리보기",
            description = "고객이 수락한 수주를 고객 사정으로 취소하면 생길 취소 수수료·환불·취소 수수료 청구·파트너 취소 보상·함께 닫을 행사를 돌려준다. 저장하지 않는다."
    )
    @GetMapping("/{salesOrderId}/customer-cancellation")
    public ApiResponse<CustomerCancellationDto.Response.Preview> previewCustomerCancellation(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId
    ) {
        return ApiResponse.success(
                customerCancellationService.preview(salesOrderId, currentUser.platformRole()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "고객 취소",
            description = "고객이 수락한 수주를 고객 사정으로 취소한다. 발주·범위 변경·행사·입금 전 청구를 함께 정리하고, 취소 수수료와 받은 금액의 차액을 환불 대기 또는 취소 수수료 청구로 남기며, 수락한 파트너에게 취소 보상을 남긴다."
    )
    @PostMapping("/{salesOrderId}/customer-cancellation")
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> cancelByCustomer(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminSalesOrderDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                customerCancellationService.cancelByCustomer(
                        salesOrderId, currentUser.userId(), currentUser.platformRole(), request.getReason()
                ),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "수주 취소", description = "견적 단계(QUOTED) 수주만 취소한다. 고객이 수락한 수주는 고객 취소로 처리한다.")
    @PostMapping("/{salesOrderId}/cancel")
    public ApiResponse<PlatformAdminSalesOrderDto.Response.AdminSalesOrderDetail> cancelSalesOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminSalesOrderDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                salesOrderService.cancelSalesOrder(salesOrderId, currentUser.userId(), currentUser.platformRole(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "발주 금액 미리보기", description = "파트너 단가표로 계산한 발주 금액, 마진, 파트너 발주 요건 충족 여부를 돌려준다. 저장하지 않는다.")
    @PostMapping("/{salesOrderId}/partner-orders/preview")
    public ApiResponse<PlatformAdminPartnerOrderDto.Response.PartnerOrderPreview> previewPartnerOrder(
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminPartnerOrderDto.Request.PreviewPartnerOrder request
    ) {
        return ApiResponse.success(partnerOrderService.previewPartnerOrder(salesOrderId, request), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "발주 발급",
            description = "고객이 수락한 수주에 파트너를 지정해 발주한다. 응답 대기·수락 발주가 이미 있으면 거부한다. "
                    + "응답 기한(responseDueAt)은 Asia/Seoul 기준 현지 시각이다."
    )
    @PostMapping("/{salesOrderId}/partner-orders")
    public ApiResponse<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> issuePartnerOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody PlatformAdminPartnerOrderDto.Request.IssuePartnerOrder request
    ) {
        return ApiResponse.success(
                partnerOrderService.issuePartnerOrder(salesOrderId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }
}
