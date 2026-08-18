package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 마스터(Ceremony) 완료 상태를 플랫폼 관리자가 강제로 바꾼다(실수로 완료됐거나 예외 상황
 * 처리용). PLATFORM_SUPPORT 이상만 도달할 수 있고(SecurityConfig에서 /api/platform-admin/**
 * 전체를 게이트), 실제 변경은 PLATFORM_OPS 이상만 서비스에서 한 번 더 검사한다 — 다른
 * PlatformAdminXxxController와 같은 패턴이다. 조직 하위 리소스 URL 중첩은
 * {@link PlatformAdminMemberController}와 같은 관례다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 상태 API")
@RestController
@RequestMapping("/api/platform-admin/organizations/{organizationId}/ceremonies")
@RequiredArgsConstructor
public class PlatformAdminCeremonyController {

    private final CeremonyService ceremonyService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "행사 상태 강제 변경",
            description = "IN_PROGRESS/COMPLETED 양방향 변경. PLATFORM_OPS 이상만 호출할 수 있다."
    )
    @PutMapping("/{ceremonyId}/status")
    public ApiResponse<CeremonyDto.Response.CeremonySummary> updateCeremonyStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long ceremonyId,
            @Valid @RequestBody CeremonyDto.Request.UpdateStatus request
    ) {
        CeremonyDto.Response.CeremonySummary response = ceremonyService.updateStatusByPlatformAdmin(
                organizationId, ceremonyId, currentUser.userId(), currentUser.platformRole(), request
        );
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
