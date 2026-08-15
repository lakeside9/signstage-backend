package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminUserDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminUserService;
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
 * 상태 변경(승인/거절)은 PLATFORM_OPS 이상만 가능하도록 서비스에서 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 회원 관리 API")
@RestController
@RequestMapping("/api/platform-admin/users")
@RequiredArgsConstructor
public class PlatformAdminUserController {

    private final PlatformAdminUserService platformAdminUserService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "회원 목록 조회", description = "status로 필터링할 수 있다(예: PENDING으로 승인 대기 목록 조회).")
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminUserDto.Response.UserSummary>> findUsers(
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminUserDto.Response.UserSummary> result = platformAdminUserService.findUsers(status, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{userId}")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> retrieveUser(@PathVariable Long userId) {
        PlatformAdminUserDto.Response.UserSummary response = platformAdminUserService.retrieveUser(userId);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "회원 상태 변경(승인/거절)",
            description = "PENDING→ACTIVE는 가입 승인, PENDING/ACTIVE→DISABLED는 거절 또는 계정 비활성화다. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/{userId}/status")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> updateUserStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long userId,
            @Valid @RequestBody PlatformAdminUserDto.Request.UpdateStatus request
    ) {
        PlatformAdminUserDto.Response.UserSummary response =
                platformAdminUserService.updateUserStatus(userId, currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
