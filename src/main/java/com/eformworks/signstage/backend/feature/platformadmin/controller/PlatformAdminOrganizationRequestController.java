package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequestStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationRequestDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminOrganizationRequestService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자의 조직 생성 요청 승인/반려 API다(signstage-docs
 * business/organization-creation-approval-review.md). 조회는 PLATFORM_SUPPORT 이상,
 * 승인/반려는 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 조직 생성 요청 승인/반려 API")
@RestController
@RequestMapping("/api/platform-admin/organization-requests")
@RequiredArgsConstructor
public class PlatformAdminOrganizationRequestController {

    private final PlatformAdminOrganizationRequestService platformAdminOrganizationRequestService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "조직 생성 요청 목록 조회", description = "status로 필터링할 수 있다. 생략하면 전체 상태를 최신순으로 반환한다.")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminOrganizationRequestDto.Response.RequestSummary>> findRequests(
            @RequestParam(required = false) OrganizationCreationRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminOrganizationRequestDto.Response.RequestSummary> result =
                platformAdminOrganizationRequestService.findRequests(status, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 생성 요청 승인",
            description = "관리자 대행 등록과 같은 저장 로직을 탄다. 요청은 코드를 담지 않으므로 이 API에서 관리자가 "
                    + "코드를 정한다. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PostMapping("/{requestId}/approve")
    public ApiResponse<PlatformAdminOrganizationRequestDto.Response.RequestSummary> approve(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long requestId,
            @Valid @RequestBody PlatformAdminOrganizationRequestDto.Request.Approve request
    ) {
        PlatformAdminOrganizationRequestDto.Response.RequestSummary response = platformAdminOrganizationRequestService
                .approve(requestId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직 생성 요청 반려", description = "반려 사유를 남긴다. PLATFORM_OPS 이상만 호출할 수 있다.")
    @PutMapping("/{requestId}/reject")
    public ApiResponse<PlatformAdminOrganizationRequestDto.Response.RequestSummary> reject(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long requestId,
            @Valid @RequestBody PlatformAdminOrganizationRequestDto.Request.Reject request
    ) {
        PlatformAdminOrganizationRequestDto.Response.RequestSummary response = platformAdminOrganizationRequestService
                .reject(requestId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
