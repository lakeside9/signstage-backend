package com.eformworks.signstage.backend.feature.permission.controller;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.permission.dto.PermissionDto;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLATFORM_SUPPORT 이상 누구나 자기 허용 권한키 목록(/me)을 받아올 수 있다. 매트릭스 조회·변경은
 * PLATFORM_SUPER 전용이며 {@code RolePermissionService}가 한 번 더 검사한다(11장, 12장 결정 #4).
 * 이 컨트롤러 자체에 대한 접근권은 SecurityConfig의 platform-admin 등급 게이트만으로 지키고
 * role_permissions로는 설정하지 않는다 — 자기 잠금 방지(12장 결정 #6).
 */
@Tag(name = "PlatformAdmin-Permission", description = "플랫폼 콘솔 역할×권한 매트릭스 조회/편집 API")
@RestController
@RequestMapping("/api/platform-admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final RolePermissionService rolePermissionService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "내 허용 권한키 목록", description = "프런트 hasPermission(key)가 참조하는 집합이다(10장).")
    @GetMapping("/me")
    public ApiResponse<PermissionDto.Response.MyPermissions> getMyPermissions(@AuthenticationPrincipal CurrentUser currentUser) {
        List<String> keys = rolePermissionService.allowedKeys(currentUser.platformRole()).stream().sorted().toList();
        return ApiResponse.success(
                new PermissionDto.Response.MyPermissions(RoleAxis.PLATFORM.name(), currentUser.platformRole(), keys),
                traceIdProvider.getTraceId()
        );
    }

    @Operation(
            summary = "역할×권한 매트릭스 조회",
            description = "PLATFORM_SUPER만 호출할 수 있다. axis 생략 시 PLATFORM(관리자 콘솔) — "
                    + "ORGANIZATION을 넘기면 조직 사용자 콘솔(MemberRole) 매트릭스를 돌려준다."
    )
    @GetMapping
    public ApiResponse<List<PermissionDto.Response.PermissionMatrixRow>> getMatrix(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "PLATFORM") RoleAxis axis
    ) {
        checkSuperRole(currentUser.platformRole());
        return ApiResponse.success(rolePermissionService.matrixFor(axis), traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "역할별 허용 여부 변경",
            description = "PLATFORM_SUPER만 호출할 수 있다. 변경은 role_permission_histories에 append-only로 남는다. "
                    + "axis는 대상 permissionDefinitionId의 role_axis와 일치해야 한다."
    )
    @PutMapping("/{permissionDefinitionId}")
    public ApiResponse<Void> setAllowed(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long permissionDefinitionId,
            @RequestParam(defaultValue = "PLATFORM") RoleAxis axis,
            @Valid @RequestBody PermissionDto.Request.SetAllowed request
    ) {
        rolePermissionService.setAllowed(
                currentUser.platformRole(), axis, permissionDefinitionId,
                request.getRoleValue(), request.getAllowed()
        );
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }

    private void checkSuperRole(String actingPlatformRole) {
        if (!"PLATFORM_SUPER".equals(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
