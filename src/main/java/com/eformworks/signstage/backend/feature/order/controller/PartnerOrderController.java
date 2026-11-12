package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.order.dto.OrderInspectionDto;
import com.eformworks.signstage.backend.feature.order.dto.PartnerOrderDto;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import com.eformworks.signstage.backend.feature.order.service.PartnerOrderResponseService;
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

/**
 * 파트너가 받은 발주 API(signstage-docs business/platform-order-partner-subcontract-review.md
 * 3장 5단계). ACTION_PARTNER_ORDER_RESPOND 권한(OWNER·ADMIN)이 필요하다. 수락하면 행사가 만들어지고
 * 응답의 ceremonyId로 행사 화면에 들어간다.
 */
@Tag(name = "PartnerOrder", description = "파트너 발주 수락·거절 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/partner-orders")
@RequiredArgsConstructor
public class PartnerOrderController {

    private final PartnerOrderResponseService partnerOrderResponseService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "받은 발주 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<PartnerOrderDto.Response.PartnerOrderSummary>> findReceivedOrders(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @RequestParam(required = false) PartnerOrderStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(partnerOrderResponseService.findReceivedOrders(organizationId, currentUser.userId(), status, pageable)),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "받은 발주 상세 조회")
    @GetMapping("/{partnerOrderId}")
    public ApiResponse<PartnerOrderDto.Response.PartnerOrderDetail> retrieveReceivedOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long partnerOrderId
    ) {
        return ApiResponse.success(
                partnerOrderResponseService.retrieveReceivedOrder(organizationId, partnerOrderId, currentUser.userId()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "발주 수락", description = "플랫폼 수주 행사를 만들고 수락한 사용자를 담당자로 배정한다.")
    @PostMapping("/{partnerOrderId}/accept")
    public ApiResponse<PartnerOrderDto.Response.PartnerOrderDetail> acceptOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long partnerOrderId
    ) {
        return ApiResponse.success(
                partnerOrderResponseService.acceptOrder(organizationId, partnerOrderId, currentUser.userId()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "발주 거절")
    @PostMapping("/{partnerOrderId}/reject")
    public ApiResponse<PartnerOrderDto.Response.PartnerOrderDetail> rejectOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long partnerOrderId,
            @Valid @RequestBody PartnerOrderDto.Request.Reject request
    ) {
        return ApiResponse.success(
                partnerOrderResponseService.rejectOrder(organizationId, partnerOrderId, currentUser.userId(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "감액 이의 제기",
            description = "감액된 검수에 승인일부터 이의 기간(기본 7일) 안에 한 번 제기한다. 이의 중에는 해당 건을 정산하지 않는다."
    )
    @PostMapping("/{partnerOrderId}/inspection/dispute")
    public ApiResponse<PartnerOrderDto.Response.PartnerOrderDetail> disputeInspection(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long partnerOrderId,
            @Valid @RequestBody OrderInspectionDto.Request.Dispute request
    ) {
        return ApiResponse.success(
                partnerOrderResponseService.disputeInspection(organizationId, partnerOrderId, currentUser.userId(), request.getReason()),
                traceIdProvider.getTraceId()
        );
    }
}
