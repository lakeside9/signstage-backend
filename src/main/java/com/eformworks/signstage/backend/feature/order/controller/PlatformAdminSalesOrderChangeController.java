package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.order.dto.SalesOrderChangeDto;
import com.eformworks.signstage.backend.feature.order.service.SalesOrderChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수주 범위 변경 API(signstage-docs business/platform-order-partner-subcontract-review.md D08). */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 수주 범위 변경 API")
@RestController
@RequestMapping("/api/platform-admin/sales-orders/{salesOrderId}")
@RequiredArgsConstructor
public class PlatformAdminSalesOrderChangeController {

    private final SalesOrderChangeService salesOrderChangeService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "범위 변경·파트너 요청 목록 조회")
    @GetMapping("/changes")
    public ApiResponse<SalesOrderChangeDto.Response.AdminOverview> findChanges(@PathVariable Long salesOrderId) {
        return ApiResponse.success(salesOrderChangeService.findChanges(salesOrderId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "범위 변경 제안",
            description = "수락된 수주의 옵션 수량·영업 할인·장소·일정을 바꾸는 변경을 제안한다. 고객 금액과 발주 금액을 다시 "
                    + "계산하고 최소 마진을 검사한다. 고객 수락과 파트너 동의(수락된 발주가 있을 때)를 받으면 반영된다."
    )
    @PostMapping("/changes")
    public ApiResponse<SalesOrderChangeDto.Response.AdminChange> proposeChange(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @Valid @RequestBody SalesOrderChangeDto.Request.Propose request
    ) {
        return ApiResponse.success(
                salesOrderChangeService.proposeChange(salesOrderId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "범위 변경 고객 수락 기록", description = "메일·전화로 받은 고객 수락을 기록한다. 파트너 동의도 받았으면 바로 반영된다.")
    @PostMapping("/changes/{changeId}/customer-acceptance")
    public ApiResponse<SalesOrderChangeDto.Response.AdminChange> recordCustomerAcceptance(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @PathVariable Long changeId,
            @Valid @RequestBody SalesOrderChangeDto.Request.RecordCustomerAcceptance request
    ) {
        return ApiResponse.success(
                salesOrderChangeService.recordCustomerAcceptance(
                        salesOrderId, changeId, currentUser.userId(), currentUser.platformRole(), request.getAcceptedByName()
                ),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "범위 변경 취소", description = "반영 전인 변경만 취소한다.")
    @PostMapping("/changes/{changeId}/cancel")
    public ApiResponse<SalesOrderChangeDto.Response.AdminChange> cancelChange(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @PathVariable Long changeId,
            @Valid @RequestBody SalesOrderChangeDto.Request.Reason request
    ) {
        return ApiResponse.success(
                salesOrderChangeService.cancelChange(
                        salesOrderId, changeId, currentUser.userId(), currentUser.platformRole(), request.getReason()
                ),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "파트너 범위 변경 요청 닫기", description = "변경을 제안하지 않고 요청을 처리할 때 메모를 남기고 닫는다.")
    @PostMapping("/scope-requests/{requestId}/close")
    public ApiResponse<SalesOrderChangeDto.Response.ScopeRequestSummary> closeScopeRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long salesOrderId,
            @PathVariable Long requestId,
            @Valid @RequestBody SalesOrderChangeDto.Request.CloseScopeRequest request
    ) {
        return ApiResponse.success(
                salesOrderChangeService.closeScopeRequest(
                        salesOrderId, requestId, currentUser.userId(), currentUser.platformRole(), request.getNote()
                ),
                traceIdProvider.getTraceId()
        );
    }
}
