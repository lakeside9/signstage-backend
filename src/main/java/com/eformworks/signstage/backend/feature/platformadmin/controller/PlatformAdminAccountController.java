package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminAccountDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자 계정(platform_role 보유 User) 관리 전용 API다(signstage-docs
 * business/user-organization-design.md 7.2절). URL 레벨({@code SecurityConfig})에서
 * platform_role 보유자만 도달하고, 생성/해제는 서비스에서 PLATFORM_SUPER만 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 계정 관리 API")
@RestController
@RequestMapping("/api/platform-admin/accounts")
@RequiredArgsConstructor
public class PlatformAdminAccountController {

    private final PlatformAdminUserService platformAdminUserService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "플랫폼 관리자 계정 목록 조회",
            description = "loginId/name/email은 부분 일치 검색, platformRole은 정확히 일치. 전부 생략하면 전체 관리자 계정이다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminUserDto.Response.UserSummary>> findAccounts(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) PlatformRole platformRole,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminUserDto.Response.UserSummary> result =
                platformAdminUserService.findAccounts(loginId, name, email, platformRole, pageable);
        return ApiResponse.success(PageResponse.from(result), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랫폼 관리자 계정 생성",
            description = "일반 회원가입/초대 API로는 platform_role을 설정할 수 없다 — 이 API가 유일한 경로다. "
                    + "PLATFORM_SUPER만 호출할 수 있다. 임시 비밀번호는 응답에 한 번만 담긴다."
    )
    @PostMapping
    public ApiResponse<PlatformAdminUserDto.Response.CreatedUser> createAccount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminAccountDto.Request.CreateAccount request
    ) {
        PlatformAdminUserDto.Response.CreatedUser response =
                platformAdminUserService.createAccount(currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "플랫폼 관리자 권한 해제",
            description = "platform_role만 비워 일반 사용자로 되돌린다(계정 자체는 유지). "
                    + "PLATFORM_SUPER만 호출할 수 있고, 본인 계정은 대상으로 지정할 수 없다."
    )
    @PutMapping("/{userId}/revoke")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> revokeAccount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long userId
    ) {
        PlatformAdminUserDto.Response.UserSummary response =
                platformAdminUserService.revokeAccount(userId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
