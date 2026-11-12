package com.eformworks.signstage.backend.feature.organization.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.organization.dto.PartnerSubcontractProfileDto;
import com.eformworks.signstage.backend.feature.organization.service.PartnerSubcontractProfileService;
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
 * 파트너 외주 수행사 정보 API(signstage-docs business/platform-order-partner-subcontract-review.md
 * D22). 플랫폼 발주를 받으려면 이 정보를 채우고 플랫폼의 계좌 확인을 받아야 한다.
 */
@Tag(name = "Organization", description = "조직(고객사) API")
@RestController
@RequestMapping("/api/organizations/{organizationId}/partner-profile")
@RequiredArgsConstructor
public class PartnerSubcontractProfileController {

    private final PartnerSubcontractProfileService partnerSubcontractProfileService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "파트너 외주 정보 조회", description = "ACTION_PARTNER_PROFILE_EDIT 권한(OWNER)이 필요하다. 아직 입력하지 않았으면 빈 값을 돌려준다.")
    @GetMapping
    public ApiResponse<PartnerSubcontractProfileDto.Response.Profile> retrieveProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        PartnerSubcontractProfileDto.Response.Profile response =
                partnerSubcontractProfileService.retrieveProfile(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "파트너 외주 정보 저장",
            description = "사업자 정보·정산 계좌·계약 동의를 저장한다. 계좌가 바뀌면 플랫폼의 예금주 확인을 다시 받아야 한다. "
                    + "동의 항목은 true일 때만 반영하고 철회하지 않는다."
    )
    @PutMapping
    public ApiResponse<PartnerSubcontractProfileDto.Response.Profile> updateProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody PartnerSubcontractProfileDto.Request.UpdateProfile request
    ) {
        PartnerSubcontractProfileDto.Response.Profile response =
                partnerSubcontractProfileService.updateProfile(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
