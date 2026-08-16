package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminMemberDto;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자의 조직 멤버 강제 조정 API다(signstage-docs
 * business/platform-admin-member-management.md 4.2절). 조회는 PLATFORM_SUPPORT 이상,
 * 추가/역할 강제 변경/강제 제거는 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다. 호출자가
 * 그 조직의 멤버일 필요가 없다 — 조직 스코핑을 우회해 접근한다(6장).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 조직 멤버 강제 조정 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/members")
@RequiredArgsConstructor
public class PlatformAdminMemberController {

    private final PlatformAdminMemberService platformAdminMemberService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "조직 멤버 목록 조회", description = "REMOVED 상태는 제외한다.")
    @GetMapping
    public ApiResponse<List<PlatformAdminMemberDto.Response.MemberSummary>> findMembers(
            @PathVariable Long organizationId
    ) {
        List<PlatformAdminMemberDto.Response.MemberSummary> response = platformAdminMemberService.findMembers(organizationId);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 강제 추가",
            description = "호출자가 그 조직의 멤버가 아니어도 된다. PLATFORM_OPS 이상만 호출할 수 있고, "
                    + "role=OWNER 지정 제한이 없다(관리자는 조직 내부 위계를 우회한다). loginId는 이미 "
                    + "가입된 사용자여야 한다. 1인 1조직 제한은 그대로 적용된다."
    )
    @PostMapping
    public ApiResponse<PlatformAdminMemberDto.Response.MemberSummary> forceAddMember(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody PlatformAdminMemberDto.Request.AddMember request
    ) {
        PlatformAdminMemberDto.Response.MemberSummary response = platformAdminMemberService.forceAddMember(
                organizationId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 역할 강제 변경",
            description = "호출자가 그 조직의 멤버가 아니어도 된다. PLATFORM_OPS 이상만 호출할 수 있고, "
                    + "마지막 OWNER는 강등할 수 없다(최소 1 OWNER 규칙 — 관리자 강제 조정도 예외 없이 지킨다)."
    )
    @PutMapping("/{memberId}/role")
    public ApiResponse<PlatformAdminMemberDto.Response.MemberSummary> forceUpdateMemberRole(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long memberId,
            @Valid @RequestBody PlatformAdminMemberDto.Request.ChangeRole request
    ) {
        PlatformAdminMemberDto.Response.MemberSummary response = platformAdminMemberService.forceUpdateMemberRole(
                organizationId, memberId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 강제 제거",
            description = "호출자가 그 조직의 멤버가 아니어도 된다. PLATFORM_OPS 이상만 호출할 수 있고, "
                    + "마지막 OWNER는 제거할 수 없다(최소 1 OWNER 규칙)."
    )
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> forceRemoveMember(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long memberId
    ) {
        platformAdminMemberService.forceRemoveMember(organizationId, memberId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
