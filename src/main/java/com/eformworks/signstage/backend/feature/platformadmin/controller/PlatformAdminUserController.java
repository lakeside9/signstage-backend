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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLATFORM_SUPPORT 이상만 호출할 수 있다(SecurityConfig에서 /api/platform-admin/** 전체를 게이트).
 * 조회 외 제어 기능(상태 변경/잠금 해제/강제 비밀번호 재설정)은 PLATFORM_OPS 이상만 가능하도록
 * 서비스에서 한 번 더 검사하고, 본인 계정은 대상으로 지정할 수 없다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 회원 관리 API")
@RestController
@RequestMapping("/api/platform-admin/users")
@RequiredArgsConstructor
public class PlatformAdminUserController {

    private final PlatformAdminUserService platformAdminUserService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "회원 생성",
            description = "관리자가 직접 회원 계정을 만든다. 임시 비밀번호는 서버가 생성해 응답에 한 번만 담아 반환하며 "
                    + "저장하지 않으므로, 이 응답을 놓치면 다시 조회할 수 없다(강제 비밀번호 재설정으로 새로 발급해야 한다). "
                    + "승인 절차 없이 즉시 ACTIVE로 생성된다(관리자가 만든다는 것 자체가 승인). "
                    + "PLATFORM_OPS 이상만 호출할 수 있고, platform_role은 이 API로 설정할 수 없다."
    )
    @PostMapping
    public ApiResponse<PlatformAdminUserDto.Response.CreatedUser> createUser(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody PlatformAdminUserDto.Request.CreateUser request
    ) {
        PlatformAdminUserDto.Response.CreatedUser response =
                platformAdminUserService.createUser(currentUser.userId(), currentUser.platformRole(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "회원 목록 조회",
            description = "loginId/name/email은 부분 일치 검색, status는 정확히 일치(예: PENDING으로 승인 대기 목록 조회). 전부 생략하면 전체 목록이다."
    )
    @GetMapping
    public ApiResponse<PageResponse<PlatformAdminUserDto.Response.UserSummary>> findUsers(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PlatformAdminUserDto.Response.UserSummary> result =
                platformAdminUserService.findUsers(loginId, name, email, status, pageable);
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
            description = "PENDING→ACTIVE는 가입 승인, PENDING/ACTIVE→DISABLED는 거절 또는 계정 비활성화다. "
                    + "PLATFORM_OPS 이상만 호출할 수 있고, 본인 계정은 대상으로 지정할 수 없다."
    )
    @PutMapping("/{userId}/status")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> updateUserStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long userId,
            @Valid @RequestBody PlatformAdminUserDto.Request.UpdateStatus request
    ) {
        PlatformAdminUserDto.Response.UserSummary response = platformAdminUserService.updateUserStatus(
                userId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "계정 잠금 즉시 해제",
            description = "연속 로그인 실패로 잠긴 계정의 실패 카운트/잠금을 초기화한다. "
                    + "PLATFORM_OPS 이상만 호출할 수 있고, 본인 계정은 대상으로 지정할 수 없다."
    )
    @PostMapping("/{userId}/unlock")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> unlockUser(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long userId
    ) {
        PlatformAdminUserDto.Response.UserSummary response =
                platformAdminUserService.unlockUser(userId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "강제 비밀번호 재설정 요청",
            description = "다음 로그인 시 비밀번호 변경을 강제한다(is_password_reset_required=TRUE). "
                    + "관리자가 비밀번호를 직접 조회/설정하지는 않는다. "
                    + "PLATFORM_OPS 이상만 호출할 수 있고, 본인 계정은 대상으로 지정할 수 없다."
    )
    @PostMapping("/{userId}/force-password-reset")
    public ApiResponse<PlatformAdminUserDto.Response.UserSummary> forcePasswordReset(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long userId
    ) {
        PlatformAdminUserDto.Response.UserSummary response =
                platformAdminUserService.forcePasswordReset(userId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
