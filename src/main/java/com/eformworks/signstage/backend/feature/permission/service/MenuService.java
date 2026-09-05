package com.eformworks.signstage.backend.feature.permission.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.MessageTranslator;
import com.eformworks.signstage.backend.feature.permission.dto.MenuDto;
import com.eformworks.signstage.backend.feature.permission.entity.Menu;
import com.eformworks.signstage.backend.feature.permission.entity.MenuHistory;
import com.eformworks.signstage.backend.feature.permission.entity.MenuTranslation;
import com.eformworks.signstage.backend.feature.permission.entity.MenuTranslationHistory;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionType;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.error.PermissionErrorCode;
import com.eformworks.signstage.backend.feature.permission.repository.MenuHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuTranslationHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuTranslationRepository;
import com.eformworks.signstage.backend.feature.permission.repository.PermissionDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code menus} 트리 조회/편집 — signstage-docs
 * business/menu-and-action-permission-management-review.md 7.1절.
 *
 * <p>노출 여부(어떤 역할이 이 메뉴를 볼 수 있는가)는 이 서비스가 아니라
 * {@link RolePermissionService}(짝이 되는 {@code MENU} 타입 {@link PermissionDefinition})가
 * 판단한다 — 메뉴 구조와 권한은 관심사가 분리돼 있다(7.1/7.2절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuHistoryRepository menuHistoryRepository;
    private final MenuTranslationRepository menuTranslationRepository;
    private final MenuTranslationHistoryRepository menuTranslationHistoryRepository;
    private final PermissionDefinitionRepository permissionDefinitionRepository;
    private final RolePermissionService rolePermissionService;
    private final MessageTranslator messageTranslator;

    /** 호출자의 역할이 허용된 메뉴만 걸러 트리로 묶어 반환한다 — 프런트가 다시 걸러낼 필요가 없다(10장). */
    public List<MenuDto.Response.MenuNode> getMenuTree(RoleAxis console, String roleValue) {
        List<Menu> activeMenus = menuRepository.findAllByConsoleOrderByDisplayOrderAsc(console).stream()
                .filter(Menu::isActive)
                .toList();

        Set<String> allowedKeys = rolePermissionService.allowedKeys(roleValue);
        Map<Long, String> menuIdToPermissionKey = permissionDefinitionRepository
                .findAllByRoleAxisAndActiveTrueOrderByDisplayOrderAsc(console).stream()
                .filter(definition -> definition.getPermissionType() == PermissionType.MENU && definition.getMenu() != null)
                .collect(Collectors.toMap(definition -> definition.getMenu().getId(), PermissionDefinition::getPermissionKey));

        List<Menu> visibleMenus = activeMenus.stream()
                .filter(menu -> {
                    String permissionKey = menuIdToPermissionKey.get(menu.getId());
                    // 짝이 되는 MENU 권한키가 아직 없으면(시딩 누락) 기본은 노출 — 없다고 숨기면
                    // 초기 시딩 실수가 "메뉴가 사라짐"으로 나타나는 게 더 위험하다(7.5절).
                    return permissionKey == null || allowedKeys.contains(permissionKey);
                })
                .toList();

        String languageCode = LocaleContextHolder.getLocale().getLanguage();
        List<Long> visibleMenuIds = visibleMenus.stream().map(Menu::getId).toList();
        Map<Long, String> overrideLabels = menuTranslationRepository.findAllByMenuIdIn(visibleMenuIds).stream()
                .filter(translation -> translation.getLanguageCode().equals(languageCode))
                .collect(Collectors.toMap(translation -> translation.getMenu().getId(), MenuTranslation::getLabel));

        Map<Long, List<Menu>> childrenByParentId = visibleMenus.stream()
                .filter(menu -> menu.getParentMenu() != null)
                .collect(Collectors.groupingBy(menu -> menu.getParentMenu().getId()));

        return visibleMenus.stream()
                .filter(menu -> menu.getParentMenu() == null)
                .map(menu -> toNode(menu, childrenByParentId, overrideLabels))
                .toList();
    }

    private MenuDto.Response.MenuNode toNode(Menu menu, Map<Long, List<Menu>> childrenByParentId, Map<Long, String> overrideLabels) {
        List<MenuDto.Response.MenuNode> children = childrenByParentId.getOrDefault(menu.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenByParentId, overrideLabels))
                .toList();
        String label = overrideLabels.getOrDefault(
                menu.getId(), messageTranslator.translate(menu.getLabelKey(), Map.of(), menu.getLabelKey()));
        return new MenuDto.Response.MenuNode(
                menu.getId(), menu.getMenuKey(), menu.getLabelKey(), label, menu.getPath(), menu.getIconKey(),
                menu.getDisplayOrder(), children
        );
    }

    /**
     * 12장 결정 #10(2026-09-05) — 이름(현재 언어)/경로/순서/사용여부까지 관리 화면에서 편집한다.
     * 구조 변경은 {@link MenuHistory}에, 이름 변경은 {@link MenuTranslation}(+이력)에 각각 남긴다.
     */
    @Transactional
    public void updateMenu(String actingPlatformRole, Long menuId, String languageCode, MenuDto.Request.UpdateMenu request) {
        checkSuperRole(actingPlatformRole);
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ApplicationException(PermissionErrorCode.MENU_NOT_FOUND));

        menu.updateStructure(request.getPath(), request.getIconKey(), request.getDisplayOrder(), request.getActive());
        menuHistoryRepository.save(MenuHistory.builder().menu(menu).build());

        if (request.getLabel() != null && !request.getLabel().isBlank()) {
            MenuTranslation translation = menuTranslationRepository.findByMenuAndLanguageCode(menu, languageCode)
                    .orElseGet(() -> menuTranslationRepository.save(
                            MenuTranslation.builder().menu(menu).languageCode(languageCode).label(request.getLabel()).build()));
            translation.changeLabel(request.getLabel());
            menuTranslationHistoryRepository.save(MenuTranslationHistory.builder().menuTranslation(translation).build());
        }
    }

    /** {@link RolePermissionService#setAllowed}와 같은 이유로 하드코딩만으로 지킨다. */
    private void checkSuperRole(String actingPlatformRole) {
        if (!"PLATFORM_SUPER".equals(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
