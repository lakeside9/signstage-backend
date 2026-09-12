package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.support.dto.FaqDto;
import com.eformworks.signstage.backend.feature.support.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ 조회 — 조직 스코프가 없는 전역 카탈로그다(인증된 사용자면 누구나). 카테고리별 그룹핑은
 * 프런트가 한다 — signstage-docs business/partner-support-center-review.md 9장 결정.
 */
@Tag(name = "Support", description = "FAQ 조회 API")
@RestController
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "FAQ 목록 조회", description = "활성 FAQ만 표시 순서대로 반환한다.")
    @GetMapping("/api/faqs")
    public ApiResponse<List<FaqDto.Response.FaqSummary>> findPublicFaqs() {
        return ApiResponse.success(faqService.findPublicFaqs(), traceIdProvider.getTraceId());
    }
}
