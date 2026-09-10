package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationSubscriptionDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.OrganizationSubscriptionStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
 * 플랫폼 관리자의 조직 구독/계약 승인 큐 — signstage-docs
 * business/organization-event-discount-pricing-review.md 8장 결정(2026-09-10).
 * {@code PlatformAdminOrganizationRequestController}(조직 생성 요청)와 같은 구조. 조회는
 * /api/platform-admin/** 게이트(PLATFORM_SUPPORT 이상)로 충분하고, 승인/반려/해지승인/해지반려는
 * 서비스가 {@code ACTION_SUBSCRIPTION_REQUEST_REVIEW}(PLATFORM_OPS 이상)로 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 조직 구독/계약 승인 API")
@RestController
@RequestMapping("/api/platform-admin/subscriptions")
@RequiredArgsConstructor
public class PlatformAdminOrganizationSubscriptionController {

    private final OrganizationSubscriptionService organizationSubscriptionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "구독 신청/해지 요청 목록 조회", description = "status를 생략하면 전체를 반환한다.")
    @GetMapping
    public ApiResponse<PageResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary>> findRequests(
            @RequestParam(required = false) OrganizationSubscriptionStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OrganizationSubscriptionDto.Response.SubscriptionSummary> response =
                organizationSubscriptionService.findRequests(status, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "구독 상태 전이 이력 조회", description = "최신순.")
    @GetMapping("/{subscriptionId}/history")
    public ApiResponse<List<OrganizationSubscriptionDto.Response.SubscriptionHistorySummary>> findHistory(
            @PathVariable Long subscriptionId
    ) {
        return ApiResponse.success(organizationSubscriptionService.findHistory(subscriptionId), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "구독 신청 승인",
            description = "PENDING만 승인할 수 있다. 이미 사용 중(ACTIVE)인 구독이 있으면 그 건은 SUPERSEDED로 대체된다(재계약)."
    )
    @PostMapping("/{subscriptionId}/approve")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> approve(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long subscriptionId
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response =
                organizationSubscriptionService.approve(subscriptionId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "구독 신청 반려", description = "PENDING만 반려할 수 있다.")
    @PostMapping("/{subscriptionId}/reject")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> reject(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody OrganizationSubscriptionDto.Request.Reject request
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response = organizationSubscriptionService.reject(
                subscriptionId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "중도 해지 요청 승인", description = "CANCELLATION_REQUESTED만 승인할 수 있다. 승인되면 구독이 즉시 CANCELLED로 종료된다.")
    @PostMapping("/{subscriptionId}/cancellation/approve")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> approveCancellation(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long subscriptionId
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response = organizationSubscriptionService
                .approveCancellation(subscriptionId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "중도 해지 요청 반려", description = "CANCELLATION_REQUESTED만 반려할 수 있다. 반려되면 구독은 ACTIVE로 되돌아간다.")
    @PostMapping("/{subscriptionId}/cancellation/reject")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> rejectCancellation(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody OrganizationSubscriptionDto.Request.Reject request
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response = organizationSubscriptionService
                .rejectCancellation(subscriptionId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
