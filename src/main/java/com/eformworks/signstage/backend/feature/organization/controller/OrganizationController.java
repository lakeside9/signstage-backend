package com.eformworks.signstage.backend.feature.organization.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationDto;
import com.eformworks.signstage.backend.feature.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직 조회 + 정보 수정을 다룬다. 생성은 더 이상 이 컨트롤러가 다루지 않는다 — {@code OrganizationCreationRequestController}로
 * 요청을 제출하고 플랫폼 관리자 승인을 거쳐야 조직이 만들어진다(signstage-docs
 * business/organization-creation-approval-review.md).
 */
@Tag(name = "Organization", description = "조직(고객사) API")
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "내가 속한 조직 목록 조회")
    @GetMapping
    public ApiResponse<List<OrganizationDto.Response.Organization>> findMyOrganizations(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        List<OrganizationDto.Response.Organization> response = organizationService.findMyOrganizations(currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직 상세 조회", description = "호출자가 해당 조직의 ACTIVE 멤버여야 조회할 수 있다.")
    @GetMapping("/{organizationId}")
    public ApiResponse<OrganizationDto.Response.Organization> retrieveOrganization(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        OrganizationDto.Response.Organization response =
                organizationService.retrieveOrganization(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(summary = "조직 정보 수정", description = "호출자가 해당 조직의 OWNER여야 한다. 코드는 이 API로 바꿀 수 없다.")
    @PutMapping("/{organizationId}")
    public ApiResponse<OrganizationDto.Response.Organization> updateOrganization(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationDto.Request.UpdateOrganization request
    ) {
        OrganizationDto.Response.Organization response =
                organizationService.updateOrganization(organizationId, currentUser.userId(), request);
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "조직 정보 변경 이력 조회",
            description = "최신순. 생성 시점 1건 + 이후 정보/상태가 바뀔 때마다 1건씩(사용자 본인·플랫폼 관리자 구분 없이 "
                    + "모두 포함). 호출자가 해당 조직의 ACTIVE 멤버이면 누구나 조회할 수 있다."
    )
    @GetMapping("/{organizationId}/history")
    public ApiResponse<List<OrganizationDto.Response.OrganizationHistorySummary>> findOrganizationHistory(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long organizationId
    ) {
        List<OrganizationDto.Response.OrganizationHistorySummary> response =
                organizationService.findOrganizationHistory(organizationId, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }
}
