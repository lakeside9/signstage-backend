package com.eformworks.signstage.backend.feature.order.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.order.dto.SalesQuotePortalDto;
import com.eformworks.signstage.backend.feature.order.service.SalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 견적 링크 API(signstage-docs business/platform-order-partner-subcontract-review.md D02).
 * 로그인 없이 토큰만으로 견적을 열람·수락한다. {@code /api/portal/**}라 서명자 포털과 같은 인증 예외와
 * 레이트 리밋을 받는다.
 */
@Tag(name = "SalesQuotePortal", description = "고객 견적 열람·수락 API")
@RestController
@RequestMapping("/api/portal/sales-quotes/{accessToken}")
@RequiredArgsConstructor
public class SalesQuotePortalController {

    private final SalesOrderService salesOrderService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "견적 열람")
    @SecurityRequirements(value = {})
    @GetMapping
    public ApiResponse<SalesQuotePortalDto.Response.SalesQuote> retrieveQuote(@PathVariable String accessToken) {
        return ApiResponse.success(salesOrderService.retrieveQuoteByAccessToken(accessToken), traceIdProvider.getTraceId());
    }

    @Operation(summary = "견적 수락", description = "수락자 이름을 남긴다. 유효기간이 지났거나 이미 수락·취소된 견적은 거부한다.")
    @SecurityRequirements(value = {})
    @PostMapping("/accept")
    public ApiResponse<SalesQuotePortalDto.Response.SalesQuote> acceptQuote(
            @PathVariable String accessToken,
            @Valid @RequestBody SalesQuotePortalDto.Request.AcceptQuote request
    ) {
        return ApiResponse.success(
                salesOrderService.acceptQuoteByAccessToken(accessToken, request.getAcceptedByName()),
                traceIdProvider.getTraceId()
        );
    }
}
