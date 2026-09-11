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
                    + "설정한 적이 없으면 marginType/marginValue가 둘 다 null로 온다."
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

    @Operation(
            summary = "조직 기본 재판매 마진 설정",
            description = "호출자가 OWNER여야 한다. 플랫폼은 이 값에 상한·승인 등 어떤 통제도 두지 않는다 — 파트너 재량이다."
    )
    @PutMapping
    public ApiResponse<CustomerQuoteDto.Response.MarginPolicy> updateOrganizationMarginPolicy(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody CustomerQuoteDto.Request.UpdateMargin request
    ) {
        CustomerQuoteDto.Response.MarginPolicy response =
                customerQuoteService.updateOrganizationMarginPolicy(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
