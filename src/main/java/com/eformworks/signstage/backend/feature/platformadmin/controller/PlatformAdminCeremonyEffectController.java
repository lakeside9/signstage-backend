package com.eformworks.signstage.backend.feature.platformadmin.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEffectDefinitionDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyEffectDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 행사 이벤트 효과 카탈로그(서명 하이라이트/전체완료 폭죽 등 프리셋) 정의 관리. PLATFORM_SUPPORT
 * 이상만 도달할 수 있고(SecurityConfig에서 {@code /api/platform-admin/**} 전체를 게이트),
 * 실제 등록·수정·순서 이동은 {@code ACTION_EFFECT_MANAGE}가 허용된 등급(PLATFORM_OPS 이상)만
 * 서비스에서 한 번 더 검사한다 — 다른 PlatformAdminXxxController와 같은 패턴이다.
 * signstage-docs business/ceremony-event-effect-implementation-tasks.md BE-CATALOG-03 참고.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 행사 이벤트 효과 카탈로그 API")
@RestController
@RequestMapping("/api/platform-admin/ceremony-effects")
@RequiredArgsConstructor
public class PlatformAdminCeremonyEffectController {

    private final CeremonyEffectDefinitionService ceremonyEffectDefinitionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "이벤트 효과 정의 목록 조회", description = "keyword(code/표시명 부분일치), target, trigger, enabled, userVisible로 검색할 수 있다.")
    @GetMapping
    public ApiResponse<PageResponse<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary>> findDefinitions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean userVisible,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> response =
                ceremonyEffectDefinitionService.findDefinitions(
                        keyword, targetType, triggerType, enabled, userVisible, pageable
                );
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "이벤트 효과 정의 상세 조회")
    @GetMapping("/{id}")
    public ApiResponse<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> findDefinition(
            @PathVariable Long id
    ) {
        return ApiResponse.success(ceremonyEffectDefinitionService.findDefinition(id), traceIdProvider.getTraceId());
    }

    @Operation(summary = "이벤트 효과 정의 등록", description = "ACTION_EFFECT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PostMapping
    public ApiResponse<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> createDefinition(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition request
    ) {
        CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary response =
                ceremonyEffectDefinitionService.createDefinition(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "이벤트 효과 정의 수정",
            description = "ACTION_EFFECT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상). "
                    + "code/target/trigger/rendererKey/requiredOptionalFeatureId는 등록 후 불변이라 여기서 바꿀 수 없다."
    )
    @PutMapping("/{id}")
    public ApiResponse<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> updateDefinition(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody CeremonyEffectDefinitionDto.Request.UpdateCeremonyEffectDefinition request
    ) {
        CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary response =
                ceremonyEffectDefinitionService.updateDefinition(id, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "이벤트 효과 정의 표시 순서 일괄 변경",
            description = "같은 분류(target, trigger) 안에서만 순서를 바꿀 수 있다. 목록 화면의 위/아래 이동 버튼이 "
                    + "그 그룹 전체를 원하는 순서로 다시 나열해 id만 통째로 보낸다. "
                    + "ACTION_EFFECT_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상)."
    )
    @PutMapping("/order")
    public ApiResponse<List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary>> reorderDefinitions(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions request
    ) {
        List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> response =
                ceremonyEffectDefinitionService.reorderDefinitions(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
