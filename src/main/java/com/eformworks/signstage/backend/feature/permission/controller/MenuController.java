package com.eformworks.signstage.backend.feature.permission.controller;

import com.eformworks.signstage.backend.core.logging.TraceIdProvider;
import com.eformworks.signstage.backend.core.security.CurrentUser;
import com.eformworks.signstage.backend.core.web.ApiResponse;
import com.eformworks.signstage.backend.feature.permission.dto.MenuDto;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.service.MenuService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLATFORM_SUPPORT 이상 누구나 자기 역할 기준 메뉴 트리를 조회할 수 있다(SecurityConfig가
 * /api/platform-admin/** 전체를 게이트). 구조 편집(PUT)은 PLATFORM_SUPER 전용이며
 * {@code MenuService}가 한 번 더 검사한다 — signstage-docs
 * business/menu-and-action-permission-management-review.md 11장.
 *
 * <p>조직(ORGANIZATION) 축 메뉴 API는 착수 범위(12장 결정 #2, 플랫폼 먼저)에 따라 이번 범위
 * 밖이다 — 이 컨트롤러는 PLATFORM 콘솔만 다룬다.
 */
@Tag(name = "PlatformAdmin-Menu", description = "플랫폼 콘솔 메뉴 트리 조회/편집 API")
@RestController
@RequestMapping("/api/platform-admin/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final TraceIdProvider traceIdProvider;

    @Operation(summary = "내 역할 기준 메뉴 트리 조회", description = "role_permissions에서 허용되지 않은 메뉴는 응답에서 빠진다.")
    @GetMapping
    public ApiResponse<List<MenuDto.Response.MenuNode>> getMyMenuTree(@AuthenticationPrincipal CurrentUser currentUser) {
        List<MenuDto.Response.MenuNode> tree = menuService.getMenuTree(RoleAxis.PLATFORM, currentUser.platformRole());
        return ApiResponse.success(tree, traceIdProvider.getTraceId());
    }

    @Operation(
            summary = "메뉴 구조 편집",
            description = "이름(현재 Accept-Language)/경로/아이콘/순서/사용여부를 바꾼다. PLATFORM_SUPER만 호출할 수 있다."
    )
    @PutMapping("/{menuId}")
    public ApiResponse<Void> updateMenu(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long menuId,
            @RequestHeader(name = "Accept-Language", defaultValue = "ko") String languageHeader,
            @Valid @RequestBody MenuDto.Request.UpdateMenu request
    ) {
        String languageCode = languageHeader.split("[,;]")[0].trim();
        menuService.updateMenu(currentUser.platformRole(), menuId, languageCode, request);
        return ApiResponse.success(null, traceIdProvider.getTraceId());
    }
}
