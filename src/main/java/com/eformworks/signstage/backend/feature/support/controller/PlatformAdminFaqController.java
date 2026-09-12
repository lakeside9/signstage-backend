package com.eformworks.signstage.backend.feature.support.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.core.web.PageResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.support.dto.FaqDto;
import com.eformworks.signstage.backend.feature.support.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ 관리. PLATFORM_SUPPORT 이상만 도달할 수 있고(SecurityConfig가
 * {@code /api/platform-admin/**} 전체를 게이트), 실제 등록·수정·삭제·순서 이동은
 * {@code ACTION_FAQ_MANAGE}가 허용된 등급(PLATFORM_OPS 이상)만 서비스에서 한 번 더 검사한다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자 FAQ 관리 API")
@RestController
@RequestMapping("/api/platform-admin/faqs")
@RequiredArgsConstructor
public class PlatformAdminFaqController {

    private final FaqService faqService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "FAQ 목록 조회", description = "active로 필터할 수 있다(생략하면 전체).")
    @GetMapping
    public ApiResponse<PageResponse<FaqDto.Response.FaqSummary>> findFaqs(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<FaqDto.Response.FaqSummary> response = faqService.findFaqs(active, pageable);
        return ApiResponse.success(PageResponse.from(response), traceIdProvider.getTraceId());
    }

    @Operation(summary = "FAQ 상세 조회")
    @GetMapping("/{faqId}")
    public ApiResponse<FaqDto.Response.FaqSummary> findFaq(@PathVariable Long faqId) {
        return ApiResponse.success(faqService.findFaq(faqId), traceIdProvider.getTraceId());
    }

    @Operation(summary = "FAQ 등록", description = "ACTION_FAQ_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PostMapping
    public ApiResponse<FaqDto.Response.FaqSummary> createFaq(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody FaqDto.Request.CreateFaq request
    ) {
        FaqDto.Response.FaqSummary response = faqService.createFaq(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "FAQ 수정", description = "ACTION_FAQ_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @PutMapping("/{faqId}")
    public ApiResponse<FaqDto.Response.FaqSummary> updateFaq(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long faqId,
            @Valid @RequestBody FaqDto.Request.UpdateFaq request
    ) {
        FaqDto.Response.FaqSummary response =
                faqService.updateFaq(faqId, currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "FAQ 삭제", description = "ACTION_FAQ_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상).")
    @DeleteMapping("/{faqId}")
    public ApiResponse<Void> deleteFaq(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long faqId
    ) {
        faqService.deleteFaq(faqId, currentUser.platformRole(), currentUser.userId());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "FAQ 표시 순서 일괄 변경",
            description = "목록 화면의 위/아래 이동 버튼이 전체 목록을 원하는 순서로 다시 나열해 통째로 보낸다. "
                    + "ACTION_FAQ_MANAGE가 허용된 등급만 호출할 수 있다(PLATFORM_OPS 이상)."
    )
    @PutMapping("/order")
    public ApiResponse<List<FaqDto.Response.FaqSummary>> updateDisplayOrders(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        List<FaqDto.Response.FaqSummary> response =
                faqService.updateDisplayOrders(currentUser.platformRole(), currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
