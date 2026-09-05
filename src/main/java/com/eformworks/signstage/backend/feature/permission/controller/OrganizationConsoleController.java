package com.eformworks.signstage.backend.feature.permission.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.permission.dto.MenuDto;
import com.eformworks.signstage.backend.feature.permission.dto.PermissionDto;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.service.OrganizationConsoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직 사용자 콘솔(ORGANIZATION 축)의 "내 메뉴/권한" — signstage-docs
 * business/menu-and-action-permission-management-review.md 10장. 인증된 사용자 누구나
 * 호출할 수 있다(SecurityConfig의 {@code anyRequest().authenticated()}) — 조직 멤버가
 * 아니면 빈 목록을 받는다({@link OrganizationConsoleService}).
 */
@Tag(name = "Organization-Console", description = "조직 사용자 콘솔 메뉴 트리/권한 조회 API")
@RestController
@RequestMapping("/api/organizations/me")
@RequiredArgsConstructor
public class OrganizationConsoleController {

    private final OrganizationConsoleService organizationConsoleService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "내 역할 기준 메뉴 트리 조회", description = "role_permissions에서 허용되지 않은 메뉴는 응답에서 빠진다.")
    @GetMapping("/menus")
    public ApiResponse<List<MenuDto.Response.MenuNode>> getMyMenuTree(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.success(
                organizationConsoleService.getMyMenuTree(currentUser.userId()), traceIdProvider.getTraceId());
    }

    @Operation(summary = "내 허용 권한키 목록", description = "프런트 hasPermission(key)가 참조하는 집합이다(10장).")
    @GetMapping("/permissions")
    public ApiResponse<PermissionDto.Response.MyPermissions> getMyPermissions(@AuthenticationPrincipal CurrentUser currentUser) {
        List<String> keys = organizationConsoleService.getMyPermissionKeys(currentUser.userId()).stream().sorted().toList();
        String roleValue = organizationConsoleService.resolveMyRole(currentUser.userId())
                .map(Enum::name)
                .orElse(null);
        return ApiResponse.success(
                new PermissionDto.Response.MyPermissions(RoleAxis.ORGANIZATION.name(), roleValue, keys),
                traceIdProvider.getTraceId()
        );
    }
}
