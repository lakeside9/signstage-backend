package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminAuditLogDto;
import com.eformworks.signstage.backend.feature.platformadmin.repository.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLATFORM_SUPPORT 이상 전체가 조회할 수 있다(SecurityConfig에서 /api/platform-admin/**
 * 전체를 게이트). signstage-docs business/user-organization-design.md 7.4절 참고.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 감사 로그 조회 API")
@RestController
@RequestMapping("/api/platform-admin/audit-logs")
@RequiredArgsConstructor
public class PlatformAdminAuditLogController {

    private final PlatformAdminAuditLogService platformAdminAuditLogService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "감사 로그 조회", description = "action으로 필터링할 수 있다. 최신순으로 정렬된다.")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminAuditLogDto.Response.AuditLogEntry>> findAuditLogs(
            @RequestParam(required = false) PlatformAdminAction action,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PlatformAdminAuditLogDto.Response.AuditLogEntry> result =
                platformAdminAuditLogService.findAuditLogs(action, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }
}
