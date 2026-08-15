package com.eformworks.signstage.backend.feature.organization.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.organization.dto.MemberDto;
import com.eformworks.signstage.backend.feature.organization.service.MemberService;
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

@Tag(name = "Organization", description = "조직 멤버 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "조직 멤버 목록 조회")
    @GetMapping
    public ApiResponse<List<MemberDto.Response.MemberSummary>> findMembers(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        List<MemberDto.Response.MemberSummary> response = memberService.findMembers(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 추가",
            description = "OWNER/ADMIN만 호출할 수 있다. role=OWNER 지정은 OWNER만 가능하다. "
                    + "loginId는 이미 가입된 사용자여야 한다(초대 이메일/토큰 흐름은 이번 범위 밖)."
    )
    @PostMapping
    public ApiResponse<MemberDto.Response.MemberSummary> addMember(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody MemberDto.Request.AddMember request
    ) {
        MemberDto.Response.MemberSummary response =
                memberService.addMember(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 역할 변경",
            description = "OWNER/ADMIN만 호출할 수 있다. OWNER 지정/해제는 OWNER만 가능하고, 마지막 OWNER는 낮출 수 없다."
    )
    @PutMapping("/{memberId}/role")
    public ApiResponse<MemberDto.Response.MemberSummary> updateMemberRole(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberDto.Request.ChangeRole request
    ) {
        MemberDto.Response.MemberSummary response =
                memberService.updateMemberRole(organizationId, memberId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 멤버 제거",
            description = "OWNER/ADMIN만 호출할 수 있다. ADMIN은 OWNER를 제거할 수 없고, 마지막 OWNER는 제거할 수 없다."
    )
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> removeMember(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long memberId
    ) {
        memberService.removeMember(organizationId, memberId, currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
