package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.OrganizationSubscriptionDto;
import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationSubscriptionService;
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

/**
 * 조직의 구독/계약 셀프서비스 API — signstage-docs
 * business/organization-event-discount-pricing-review.md 8장 결정(2026-09-10). 신청/중도해지
 * 요청은 OWNER만(서비스 레이어에서 검사), 현재 구독 조회는 조직 멤버 누구나 가능하다. 플랫폼
 * 관리자의 승인/반려는 {@code PlatformAdminOrganizationSubscriptionController}가 담당한다.
 */
@Tag(name = "OrganizationSubscription", description = "조직 구독/계약 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/subscriptions")
@RequiredArgsConstructor
public class OrganizationSubscriptionController {

    private final OrganizationSubscriptionService organizationSubscriptionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "구독 신청",
            description = "구독형(SUBSCRIPTION) 플랜만 신청할 수 있다. 조직에 이미 진행 중이거나 사용 중인 "
                    + "구독이 있으면 거부된다(재계약은 됨 — 사용 중이어도 새로 신청/승인하면 기존 건을 대체한다). "
                    + "OWNER만 신청할 수 있다."
    )
    @PostMapping
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> requestSubscription(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationSubscriptionDto.Request.CreateSubscription request
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response =
                organizationSubscriptionService.requestSubscription(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "중도 해지 요청",
            description = "사용 중(ACTIVE)인 구독만 해지를 요청할 수 있다. 관리자 승인 전까지는 계속 사용할 수 있다. OWNER만 요청할 수 있다."
    )
    @PostMapping("/cancellation-request")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> requestCancellation(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationSubscriptionDto.Request.RequestCancellation request
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response =
                organizationSubscriptionService.requestCancellation(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "현재 구독 상태 조회",
            description = "진행 중(PENDING)이거나 사용 중(ACTIVE)이거나 해지 심사 중(CANCELLATION_REQUESTED)인 "
                    + "구독 하나를 돌려준다. 없으면 null. 조직 멤버 누구나 조회할 수 있다."
    )
    @GetMapping("/current")
    public ApiResponse<OrganizationSubscriptionDto.Response.SubscriptionSummary> findCurrentSubscription(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        OrganizationSubscriptionDto.Response.SubscriptionSummary response =
                organizationSubscriptionService.findCurrentSubscription(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
