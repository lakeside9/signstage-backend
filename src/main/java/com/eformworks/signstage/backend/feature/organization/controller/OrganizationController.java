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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization", description = "조직(고객사) API")
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final TraceIdProvider traceIdProvider;

    @Operation(
            summary = "조직 생성",
            description = "로그인한 사용자 본인이 자동으로 OWNER가 된다. role은 요청값으로 받지 않는다."
    )
    @PostMapping
    public ApiResponse<OrganizationDto.Response.Organization> createOrganization(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody OrganizationDto.Request.CreateOrganization request
    ) {
        OrganizationDto.Response.Organization response =
                organizationService.createOrganization(request, currentUser.userId());
        return ApiResponse.success(response, traceIdProvider.getTraceId());
    }

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
}
