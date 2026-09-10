package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.service.SignerPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * legacy 체험형 데모 사이트({@code ~/Works/eform/source/signstage/demo-signstage-frontend},
 * 별도 저장소, 그대로 재사용) 호환 전용 엔드포인트 — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장(2026-09-10). 경로 모양이 이
 * 프로젝트의 다른 컨트롤러 관례(`/api/portal/events/{eventAccessKey}/signers/{signerAccessKey}`)와
 * 다른 건 의도적이다 — legacy 데모 셸의 "다시 체험하기" 버튼이 이미 정확히 이 경로
 * (`/api/ceremonies/portal/event/{eventAccessKey}/signatures/reset`)를 호출하고 있어, 그
 * 저장소 코드를 전혀 건드리지 않으려면 백엔드가 그 경로를 그대로 맞춰야 한다. 실제 처리는
 * {@link SignerPortalService#resetAllSignersForEvent}에 위임한다(새 서명 도메인 로직 없음).
 */
@Tag(name = "Demo", description = "legacy 데모 사이트 호환 전용 API")
@RestController
@RequestMapping("/api/ceremonies/portal/event/{eventAccessKey}/signatures")
@RequiredArgsConstructor
public class LegacyDemoSignatureResetController {

    private final SignerPortalService signerPortalService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "이벤트 전체 서명 초기화(legacy 데모 사이트 전용)",
            description = "signerAccessKey를 받지 않는다 — 그 이벤트의 필수 서명자 전원을 한 번에 초기화한다."
    )
    @SecurityRequirements(value = {})
    @PostMapping("/reset")
    public ApiResponse<Void> resetAllSignersForEvent(@PathVariable String eventAccessKey) {
        signerPortalService.resetAllSignersForEvent(eventAccessKey);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
