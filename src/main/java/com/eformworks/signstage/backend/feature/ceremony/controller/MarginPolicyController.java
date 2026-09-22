package com.eformworks.signstage.backend.feature.ceremony.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.ceremony.dto.CustomerQuoteDto;
import com.eformworks.signstage.backend.feature.ceremony.service.CustomerQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직(파트너) 기본 재판매 마진 — signstage-docs
 * business/platform-partner-customer-billing-model-reference.md 4장 결정(2026-09-11). 조직
 * 조회/수정 자체가 아니라 과금 개념이라 {@code OrganizationController}가 아니라 이 패키지에
 * 둔다({@code feature.organization}은 {@code feature.ceremony}를 참조하지 않는 기존 방향을
 * 지킨다 — {@code OrganizationSubscriptionService}와 같은 선례).
 */
@Tag(name = "MarginPolicy", description = "조직 기본 재판매 마진 API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/margin-policy")
@RequiredArgsConstructor
public class MarginPolicyController {

    private final CustomerQuoteService customerQuoteService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "조직 기본 재판매 마진 조회",
            description = "파트너가 실고객에게 시스템 사용료를 재판매할 때 얹는 기본 마진(정률/정액) — 호출자가 OWNER여야 한다. "
                    + "파트너 시간대의 오늘에 적용할 정책이 없으면 marginType/marginValue가 둘 다 null로 온다."
    )
    @GetMapping
    public ApiResponse<CustomerQuoteDto.Response.MarginPolicy> retrieveOrganizationMarginPolicy(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        CustomerQuoteDto.Response.MarginPolicy response =
                customerQuoteService.retrieveOrganizationMarginPolicy(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @GetMapping("/periods")
    public ApiResponse<List<CustomerQuoteDto.Response.MarginPeriod>> retrievePeriods(
            @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long organizationId) {
        return ApiResponse.success(customerQuoteService.retrieveOrganizationMarginPeriods(organizationId, currentUser.userId()),
                traceIdProvider.getTraceId());
    }

    @PostMapping("/periods")
    public ApiResponse<CustomerQuoteDto.Response.MarginPeriod> createPeriod(
            @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long organizationId,
            @Valid @RequestBody CustomerQuoteDto.Request.MarginPeriod request) {
        return ApiResponse.success(customerQuoteService.saveOrganizationMarginPeriod(organizationId, null, currentUser.userId(), request),
                traceIdProvider.getTraceId());
    }

    @PutMapping("/periods/{policyId}")
    public ApiResponse<CustomerQuoteDto.Response.MarginPeriod> updatePeriod(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @PathVariable Long policyId,
            @Valid @RequestBody CustomerQuoteDto.Request.MarginPeriod request
    ) {
        CustomerQuoteDto.Response.MarginPeriod response =
                customerQuoteService.saveOrganizationMarginPeriod(organizationId, policyId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
