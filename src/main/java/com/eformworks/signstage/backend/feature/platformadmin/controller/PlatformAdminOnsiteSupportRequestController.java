package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.entity.OnsiteSupportRequestStatus;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyOnsiteSupportRequestService;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOnsiteSupportRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현장지원 요청(관리자 견적) 조회/견적 API — signstage-docs
 * business/onsite-support-negotiation-and-billing-classification-review.md 3.2절. 조회는
 * PLATFORM_SUPPORT 이상, 견적 입력은 {@code ACTION_ONSITE_SUPPORT_REQUEST_MANAGE}가 허용된
 * 등급(PLATFORM_OPS 이상)만 서비스에서 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 현장지원 요청 API")
@RestController
@RequestMapping("/api/platform-admin/onsite-support-requests")
@RequiredArgsConstructor
public class PlatformAdminOnsiteSupportRequestController {

    private final CeremonyOnsiteSupportRequestService ceremonyOnsiteSupportRequestService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "현장지원 요청 조직 横단 목록 조회",
            description = "status/organizationId/ceremonyId/requesterKeyword 전부 선택 필터다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary>> findRequests(
            @RequestParam(required = false) OnsiteSupportRequestStatus status,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long ceremonyId,
            @RequestParam(required = false) String requesterKeyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary> response =
                ceremonyOnsiteSupportRequestService.findRequestsAcrossOrganizations(
                        status, organizationId, ceremonyId, requesterKeyword, pageable
                );
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "견적 입력",
            description = "REQUESTED 상태인 요청에만 금액을 매길 수 있다. ACTION_ONSITE_SUPPORT_REQUEST_MANAGE가 " +
                    "허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상)."
    )
    @PutMapping("/{requestId}/quote")
    public ApiResponse<PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary> quoteRequest(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long requestId,
            @Valid @RequestBody PlatformAdminOnsiteSupportRequestDto.Request.Quote request
    ) {
        PlatformAdminOnsiteSupportRequestDto.Response.RequestSummary response = ceremonyOnsiteSupportRequestService.quoteRequest(
                requestId, currentUser.platformRole(), currentUser.userId(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
