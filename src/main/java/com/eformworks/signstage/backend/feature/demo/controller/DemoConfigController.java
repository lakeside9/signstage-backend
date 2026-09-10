package com.eformworks.signstage.backend.feature.demo.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.demo.dto.DemoConfigDto;
import com.eformworks.signstage.backend.feature.demo.service.DemoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 체험형 데모 사이트(legacy {@code demo-signstage-frontend}, 별도 저장소, 그대로 재사용)가
 * 호출하는 공개 API — signstage-docs
 * business/demo-account-exhibition-signer-preview-review.md 13장. 로그인 불필요, slug 소지
 * 만으로 접근한다(프로젝터/서명자 포털과 같은 인가 모델). 경로·응답 모양을 legacy가 이미
 * 소비하는 계약 그대로 맞췄다 — 그 저장소는 전혀 건드리지 않는다.
 */
@Tag(name = "Demo", description = "체험형 데모 사이트 공개 API")
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoConfigController {

    private final DemoConfigService demoConfigService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "데모 셸(iframe)이 사용할 전시/서명 URL 조회", description = "slug가 없거나 프로필이 없으면 data가 null이다.")
    @SecurityRequirements(value = {})
    @GetMapping("/config")
    public ApiResponse<DemoConfigDto.Response.Config> getConfig(@RequestParam(required = false) String slug) {
        return ApiResponse.success(demoConfigService.getConfig(slug), traceIdProvider.getTraceId());
    }

    @Operation(summary = "데모 프로필 선택 목록 조회", description = "활성화(enabled)된 프로필만, slug 오름차순.")
    @SecurityRequirements(value = {})
    @GetMapping("/configs")
    public ApiResponse<List<DemoConfigDto.Response.PublicSummary>> listPublicConfigs() {
        return ApiResponse.success(demoConfigService.listPublicConfigs(), traceIdProvider.getTraceId());
    }
}
