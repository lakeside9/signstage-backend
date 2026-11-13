package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.dto.QuoteTemplateDto;
import com.eformworks.signstage.backend.feature.order.service.QuoteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 견적 공통 문구 API — 견적서 "제공 서비스" 본문과 하단 안내문구를 기간별로 관리한다. 등록·수정·기간 종료·삭제는
 * 수주 가격표와 같은 ACTION_ORDER_PRICE_MANAGE 권한이 필요하다.
 */
@Tag(name = "PlatformAdmin", description = "플랫폼 관리자의 견적 공통 문구 API")
@RestController
@RequestMapping("/api/platform-admin/quote-templates")
@RequiredArgsConstructor
public class PlatformAdminQuoteTemplateController {

    private final QuoteTemplateService quoteTemplateService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "견적 공통 문구 목록 조회")
    @GetMapping
    public ApiResponse<List<QuoteTemplateDto.Response.AdminQuoteTemplate>> findQuoteTemplates() {
        return ApiResponse.success(quoteTemplateService.findQuoteTemplates(), traceIdProvider.getTraceId());
    }

    @Operation(summary = "오늘 유효한 견적 공통 문구", description = "오늘 새 견적에 복사될 문구. 없으면 data가 null이다.")
    @GetMapping("/effective")
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> findEffectiveQuoteTemplate() {
        return ApiResponse.success(quoteTemplateService.findEffectiveQuoteTemplate().orElse(null), traceIdProvider.getTraceId());
    }

    @Operation(summary = "견적 공통 문구 등록", description = "다른 문구와 기간이 겹치면 거부한다(ORDER_QUOTE_TEMPLATE_PERIOD_OVERLAP).")
    @PostMapping
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> createQuoteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody QuoteTemplateDto.Request.SaveQuoteTemplate request
    ) {
        return ApiResponse.success(
                quoteTemplateService.createQuoteTemplate(currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "견적 공통 문구 수정", description = "견적에 쓰이지 않은 문구만 고친다(ORDER_QUOTE_TEMPLATE_IN_USE).")
    @PutMapping("/{quoteTemplateId}")
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> updateQuoteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId,
            @Valid @RequestBody QuoteTemplateDto.Request.SaveQuoteTemplate request
    ) {
        return ApiResponse.success(
                quoteTemplateService.updateQuoteTemplate(quoteTemplateId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "견적 공통 문구 기간 종료")
    @PutMapping("/{quoteTemplateId}/close")
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> closeQuoteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId,
            @Valid @RequestBody PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        return ApiResponse.success(
                quoteTemplateService.closeQuoteTemplate(quoteTemplateId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "견적서 공급자 정보 저장", description = "상호·대표자·사업자등록번호·주소·연락처. 견적에 쓰인 문구는 비어 있을 때 한 번만 "
            + "채울 수 있다(ORDER_QUOTE_SUPPLIER_IN_USE).")
    @PutMapping("/{quoteTemplateId}/supplier")
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> updateSupplier(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId,
            @Valid @RequestBody QuoteTemplateDto.Request.SaveSupplier request
    ) {
        return ApiResponse.success(
                quoteTemplateService.updateSupplier(quoteTemplateId, currentUser.userId(), currentUser.platformRole(), request),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "회사 인감 업로드", description = "PNG·JPEG, 2MB 이하. 견적에 쓰인 문구는 인감이 없을 때만 올릴 수 있다.")
    @PostMapping(value = "/{quoteTemplateId}/seal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> uploadSeal(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(
                quoteTemplateService.uploadSeal(quoteTemplateId, currentUser.userId(), currentUser.platformRole(), file),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "회사 인감 삭제", description = "견적에 쓰이지 않은 문구만.")
    @DeleteMapping("/{quoteTemplateId}/seal")
    public ApiResponse<QuoteTemplateDto.Response.AdminQuoteTemplate> deleteSeal(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId
    ) {
        return ApiResponse.success(
                quoteTemplateService.deleteSeal(quoteTemplateId, currentUser.userId(), currentUser.platformRole()),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(summary = "회사 인감 이미지", description = "플랫폼 관리자 인증이 필요하다. 캐시하지 않는다.")
    @GetMapping("/{quoteTemplateId}/seal")
    public ResponseEntity<Resource> loadSeal(@PathVariable Long quoteTemplateId) {
        QuoteTemplateService.SealImage seal = quoteTemplateService.loadSeal(quoteTemplateId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(seal.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(seal.resource());
    }

    @Operation(summary = "견적 공통 문구 삭제", description = "견적에 쓰인 문구는 삭제할 수 없다(ORDER_QUOTE_TEMPLATE_IN_USE).")
    @DeleteMapping("/{quoteTemplateId}")
    public ApiResponse<Void> deleteQuoteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long quoteTemplateId
    ) {
        quoteTemplateService.deleteQuoteTemplate(quoteTemplateId, currentUser.userId(), currentUser.platformRole());
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
