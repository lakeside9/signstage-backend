package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEffectDefinitionDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CeremonyEffectDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 이벤트 효과 카탈로그 조회. {@code BillingCatalogController}와 같은 이유로 조직 스코프가
 * 없는 전역 카탈로그다 — 인증된 사용자면 누구나 조회할 수 있다(행사 등록/수정 화면에서 프리셋을
 * 고를 때 필요). 활성 + 사용자 노출 정의만 반환한다 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-CATALOG-03 참고.
 */
@Tag(name = "Ceremony", description = "행사 이벤트 효과 카탈로그 조회 API")
@RestController
@RequiredArgsConstructor
public class CeremonyEffectController {

    private final CeremonyEffectDefinitionService ceremonyEffectDefinitionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "이벤트 효과 카탈로그 목록 조회", description = "활성 + 사용자 노출 정의만 반환한다.")
    @GetMapping("/api/ceremony-effects")
    public ApiResponse<List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary>> findPublicDefinitions() {
        return ApiResponse.success(ceremonyEffectDefinitionService.findPublicDefinitions(), traceIdProvider.getTraceId());
    }
}
