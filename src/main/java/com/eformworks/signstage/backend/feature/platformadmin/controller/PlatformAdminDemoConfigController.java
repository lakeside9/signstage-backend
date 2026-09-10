package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.demo.dto.DemoConfigDto;
import com.eformworks.signstage.backend.feature.demo.service.DemoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 관리자의 체험형 데모 프로필 관리 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장. legacy
 * {@code DemoSettings.tsx}가 쓰던 {@code /api/admin/demo/**}(하드코딩 `hasRole("ADMIN")`) 대신,
 * 이 프로젝트의 다른 관리자 API와 같은 관례(`/api/platform-admin/**`, 동적 RBAC
 * {@code ACTION_DEMO_CEREMONY_MANAGE} 재사용 — 데모 행사 관리와 같은 권한 등급)를 따른다.
 * 이 화면 자체는 새로 만든다(legacy `DemoSettings.tsx`는 재사용하지 않는다).
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 체험형 데모 프로필 API")
@RestController
@RequestMapping("/api/platform-admin/demo-configs")
@RequiredArgsConstructor
public class PlatformAdminDemoConfigController {

    private final DemoConfigService demoConfigService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "데모 프로필 전체 목록 조회")
    @GetMapping
    public ApiResponse<List<DemoConfigDto.Response.Config>> findAll(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.success(demoConfigService.findAll(currentUser.platformRole()), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "프로필에 지정할 수 있는 데모 행사 + 서명자 후보 조회",
            description = "데모 조직(Organization.isDemo) 소속 행사 전체와 그 행사의 서명자 목록을 함께 돌려준다."
    )
    @GetMapping("/event-options")
    public ApiResponse<List<DemoConfigDto.Response.DemoEventOption>> findEventOptions(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.success(demoConfigService.findEventOptions(currentUser.platformRole()), traceIdProvider.getTraceId());
    }

    @Operation(summary = "데모 프로필 생성/수정", description = "slug로 upsert한다 — 이미 있으면 갱신, 없으면 새로 만든다.")
    @PutMapping
    public ApiResponse<DemoConfigDto.Response.Config> upsert(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody DemoConfigDto.Request.Upsert request
    ) {
        return ApiResponse.success(demoConfigService.upsert(currentUser.platformRole(), request), traceIdProvider.getTraceId());
    }

    @Operation(summary = "데모 프로필 삭제")
    @DeleteMapping("/{slug}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable String slug) {
        demoConfigService.delete(currentUser.platformRole(), slug);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
