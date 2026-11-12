package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminPartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.service.PartnerOrderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 발주 목록·취소 API. 발급은 수주 하위 경로({@link PlatformAdminSalesOrderController})에 있다. */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 발주 API")
@RestController
@RequestMapping("/api/platform-admin/partner-orders")
@RequiredArgsConstructor
public class PlatformAdminPartnerOrderController {

    private final PartnerOrderService partnerOrderService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "발주 목록 조회", description = "status 필터는 저장된 상태 기준이다. 응답의 status는 기한이 지난 응답 대기 발주를 EXPIRED로 보여준다.")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary>> findPartnerOrders(
            @RequestParam(required = false) PartnerOrderStatus status,
            @RequestParam(required = false) Long organizationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(partnerOrderService.findPartnerOrders(status, organizationId, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "발주 취소", description = "응답 대기 발주만 취소한다. ACTION_PARTNER_ORDER_ISSUE 권한이 필요하다.")
    @PostMapping("/{partnerOrderId}/cancel")
    public ApiResponse<PlatformAdminPartnerOrderDto.Response.AdminPartnerOrderSummary> cancelPartnerOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long partnerOrderId,
            @Valid @RequestBody PlatformAdminPartnerOrderDto.Request.Cancel request
    ) {
        return ApiResponse.success(
                partnerOrderService.cancelPartnerOrder(partnerOrderId, currentUser.userId(), currentUser.platformRole(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }
}
