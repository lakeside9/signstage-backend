package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.organization.repository.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminOrganizationService;
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
 * PLATFORM_SUPPORT 이상만 호출할 수 있다(SecurityConfig에서 /api/platform-admin/** 전체를 게이트).
 * 조회는 전체 등급, 상태 변경(정지/재개)은 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다.
 * 조직 멤버 강제 조정은 이번 범위 밖(platform-admin-member-management.md 참고).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 조직 조회 API")
@RestController
@RequestMapping("/api/platform-admin/organizations")
@RequiredArgsConstructor
public class PlatformAdminOrganizationController {

    private final PlatformAdminOrganizationService platformAdminOrganizationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "조직 목록 조회",
            description = "name/code는 부분 일치 검색, status는 정확히 일치. 전부 생략하면 전체 목록이다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminOrganizationDto.Response.OrganizationSummary>> findOrganizations(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) OrganizationStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminOrganizationDto.Response.OrganizationSummary> result =
                platformAdminOrganizationService.findOrganizations(name, code, status, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직 상세 조회")
    @GetMapping("/{organizationId}")
    public ApiResponse<PlatformAdminOrganizationDto.Response.OrganizationSummary> retrieveOrganization(
            @PathVariable Long organizationId
    ) {
        PlatformAdminOrganizationDto.Response.OrganizationSummary response =
                platformAdminOrganizationService.retrieveOrganization(organizationId);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 상태 변경(정지/재개)",
            description = "ACTIVE↔SUSPENDED만 다룬다. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/{organizationId}/status")
    public ApiResponse<PlatformAdminOrganizationDto.Response.OrganizationSummary> updateOrganizationStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody PlatformAdminOrganizationDto.Request.UpdateStatus request
    ) {
        PlatformAdminOrganizationDto.Response.OrganizationSummary response = platformAdminOrganizationService
                .updateOrganizationStatus(organizationId, currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
