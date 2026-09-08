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

    /**
     * 관리 화면(메뉴 관리, 12장 결정 #10) 전용 — 역할 필터링 없이 이 콘솔의 메뉴 전체(비활성
     * 포함)를 평면 목록으로 반환한다. 호출자가 {@code PLATFORM_SUPER}인지는 컨트롤러가 먼저
     * 검사한다(11장, {@link RolePermissionService#setAllowed}와 같은 이유로 하드코딩만으로
     * 지킨다).
     */
    public List<MenuDto.Response.MenuAdminRow> getAllMenus(RoleAxis console) {
        List<Menu> menus = menuRepository.findAllByConsoleOrderByDisplayOrderAsc(console);
        Map<Long, String> labels = resolveLabels(menus);
        return menus.stream()
                .map(menu -> new MenuDto.Response.MenuAdminRow(
                        menu.getId(),
                        menu.getParentMenu() == null ? null : menu.getParentMenu().getId(),
                        menu.getMenuKey(),
                        menu.getLabelKey(),
                        labels.get(menu.getId()),
                        menu.getPath(),
                        menu.getIconKey(),
                        menu.getDisplayOrder(),
                        menu.isActive()
                ))
                .toList();
    }

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

        Map<Long, String> labels = resolveLabels(visibleMenus);

        Map<Long, List<Menu>> childrenByParentId = visibleMenus.stream()
                .filter(menu -> menu.getParentMenu() != null)
                .collect(Collectors.groupingBy(menu -> menu.getParentMenu().getId()));

        return visibleMenus.stream()
                .filter(menu -> menu.getParentMenu() == null)
                .map(menu -> toNode(menu, childrenByParentId, labels))
                .toList();
    }

    private MenuDto.Response.MenuNode toNode(Menu menu, Map<Long, List<Menu>> childrenByParentId, Map<Long, String> labels) {
        List<MenuDto.Response.MenuNode> children = childrenByParentId.getOrDefault(menu.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenByParentId, labels))
                .toList();
        return new MenuDto.Response.MenuNode(
                menu.getId(), menu.getMenuKey(), menu.getLabelKey(), labels.get(menu.getId()), menu.getPath(),
                menu.getIconKey(), menu.getDisplayOrder(), children
        );
    }

    /**
     * 현재 Accept-Language에 해당하는 {@link MenuTranslation}이 있으면 그 값을, 없으면
     * {@code label_key}를 {@link MessageTranslator}로 번역한 값을(그마저 없으면 label_key
     * 문자열 자체를) 메뉴 id별로 돌려준다.
     */
    private Map<Long, String> resolveLabels(List<Menu> menus) {
        String languageCode = LocaleContextHolder.getLocale().getLanguage();
        List<Long> menuIds = menus.stream().map(Menu::getId).toList();
        Map<Long, String> overrideLabels = menuTranslationRepository.findAllByMenuIdIn(menuIds).stream()
                .filter(translation -> translation.getLanguageCode().equals(languageCode))
                .collect(Collectors.toMap(translation -> translation.getMenu().getId(), MenuTranslation::getLabel));

        return menus.stream().collect(Collectors.toMap(
                Menu::getId,
                menu -> overrideLabels.getOrDefault(
                        menu.getId(), messageTranslator.translate(menu.getLabelKey(), Map.of(), menu.getLabelKey()))
        ));
    }

    /**
     * 12장 결정 #10(2026-09-05) — 이름(현재 언어)/경로/순서/사용여부까지 관리 화면에서 편집한다.
     * 후속(2026-09-05)으로 상위 메뉴(레벨) 이동도 같은 API에서 다룬다. 구조 변경은
     * {@link MenuHistory}에, 이름 변경은 {@link MenuTranslation}(+이력)에 각각 남긴다.
     */
    @Transactional
    public void updateMenu(String actingPlatformRole, Long menuId, String languageCode, MenuDto.Request.UpdateMenu request) {
        checkSuperRole(actingPlatformRole);
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ApplicationException(PermissionErrorCode.MENU_NOT_FOUND));

        Menu newParent = resolveNewParent(menu, request.getParentMenuId());
        menu.changeParent(newParent);
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

    /**
     * 상위 메뉴 후보를 검증한다 — 자기 자신, 다른 콘솔의 메뉴, 자기 하위 트리(순환)는 상위로
     * 지정할 수 없다. {@code null}(최상위로 이동)은 항상 허용한다.
     */
    private Menu resolveNewParent(Menu menu, Long requestedParentId) {
        if (requestedParentId == null) {
            return null;
        }
        if (requestedParentId.equals(menu.getId())) {
            throw new ApplicationException(PermissionErrorCode.MENU_PARENT_INVALID);
        }
        Menu candidate = menuRepository.findById(requestedParentId)
                .orElseThrow(() -> new ApplicationException(PermissionErrorCode.MENU_NOT_FOUND));
        if (candidate.getConsole() != menu.getConsole()) {
            throw new ApplicationException(PermissionErrorCode.MENU_PARENT_INVALID);
        }
        for (Menu cursor = candidate; cursor != null; cursor = cursor.getParentMenu()) {
            if (cursor.getId().equals(menu.getId())) {
                // candidate가 menu 자신의 하위 트리에 있다 — 순환이 생긴다.
                throw new ApplicationException(PermissionErrorCode.MENU_PARENT_INVALID);
            }
        }
        return candidate;
    }

    /** {@link RolePermissionService#setAllowed}와 같은 이유로 하드코딩만으로 지킨다. */
    private void checkSuperRole(String actingPlatformRole) {
        if (!"PLATFORM_SUPER".equals(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
