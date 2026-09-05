package com.eformworks.signstage.backend.feature.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.i18n.MessageTranslator;
import com.eformworks.signstage.backend.feature.permission.dto.MenuDto;
import com.eformworks.signstage.backend.feature.permission.entity.Menu;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionType;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.repository.MenuHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuTranslationHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.MenuTranslationRepository;
import com.eformworks.signstage.backend.feature.permission.repository.PermissionDefinitionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuHistoryRepository menuHistoryRepository;
    @Mock
    private MenuTranslationRepository menuTranslationRepository;
    @Mock
    private MenuTranslationHistoryRepository menuTranslationHistoryRepository;
    @Mock
    private PermissionDefinitionRepository permissionDefinitionRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private MessageTranslator messageTranslator;

    @InjectMocks
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void getMenuTree_hidesMenusNotAllowedForTheRole() {
        Menu dashboard = Menu.builder().console(RoleAxis.PLATFORM).menuKey("MENU_DASHBOARD")
                .labelKey("navigation.dashboard").path("/admin").displayOrder(0).build();
        Menu accounts = Menu.builder().console(RoleAxis.PLATFORM).menuKey("MENU_ACCOUNTS")
                .labelKey("navigation.adminAccounts").path("/admin/accounts").displayOrder(1).build();
        ReflectionTestUtils.setField(dashboard, "id", 1L);
        ReflectionTestUtils.setField(accounts, "id", 2L);
        given(menuRepository.findAllByConsoleOrderByDisplayOrderAsc(RoleAxis.PLATFORM))
                .willReturn(List.of(dashboard, accounts));

        PermissionDefinition accountsMenuPermission = PermissionDefinition.builder()
                .permissionKey("MENU_ACCOUNTS").permissionType(PermissionType.MENU).roleAxis(RoleAxis.PLATFORM)
                .menu(accounts).labelKey("navigation.adminAccounts").displayOrder(1).build();
        given(permissionDefinitionRepository.findAllByRoleAxisAndActiveTrueOrderByDisplayOrderAsc(RoleAxis.PLATFORM))
                .willReturn(List.of(accountsMenuPermission));

        // PLATFORM_SUPPORT는 MENU_ACCOUNTS가 허용 목록에 없다 — 사이드바에서 빠져야 한다.
        given(rolePermissionService.allowedKeys("PLATFORM_SUPPORT")).willReturn(Set.of());
        given(messageTranslator.translate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .willAnswer(invocation -> invocation.getArgument(2));

        List<MenuDto.Response.MenuNode> tree = menuService.getMenuTree(RoleAxis.PLATFORM, "PLATFORM_SUPPORT");

        assertThat(tree).extracting(MenuDto.Response.MenuNode::getMenuKey).containsExactly("MENU_DASHBOARD");
    }

    @Test
    void getMenuTree_defaultsToVisible_whenNoMenuPermissionDefinitionSeededYet() {
        Menu dashboard = Menu.builder().console(RoleAxis.PLATFORM).menuKey("MENU_DASHBOARD")
                .labelKey("navigation.dashboard").path("/admin").displayOrder(0).build();
        given(menuRepository.findAllByConsoleOrderByDisplayOrderAsc(RoleAxis.PLATFORM)).willReturn(List.of(dashboard));
        given(permissionDefinitionRepository.findAllByRoleAxisAndActiveTrueOrderByDisplayOrderAsc(RoleAxis.PLATFORM))
                .willReturn(List.of());
        given(rolePermissionService.allowedKeys("PLATFORM_SUPPORT")).willReturn(Set.of());
        given(messageTranslator.translate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .willAnswer(invocation -> invocation.getArgument(2));

        List<MenuDto.Response.MenuNode> tree = menuService.getMenuTree(RoleAxis.PLATFORM, "PLATFORM_SUPPORT");

        assertThat(tree).extracting(MenuDto.Response.MenuNode::getMenuKey).containsExactly("MENU_DASHBOARD");
    }

    @Test
    void getAllMenus_returnsFlatListIncludingInactive_forAdminScreen() {
        Menu dashboard = Menu.builder().console(RoleAxis.PLATFORM).menuKey("MENU_DASHBOARD")
                .labelKey("navigation.dashboard").path("/admin").displayOrder(0).build();
        Menu accounts = Menu.builder().console(RoleAxis.PLATFORM).menuKey("MENU_ACCOUNTS")
                .labelKey("navigation.adminAccounts").path("/admin/accounts").displayOrder(1).build();
        accounts.updateStructure("/admin/accounts", "ShieldCheck", 1, false);
        ReflectionTestUtils.setField(dashboard, "id", 1L);
        ReflectionTestUtils.setField(accounts, "id", 2L);
        given(menuRepository.findAllByConsoleOrderByDisplayOrderAsc(RoleAxis.PLATFORM))
                .willReturn(List.of(dashboard, accounts));
        given(messageTranslator.translate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .willAnswer(invocation -> invocation.getArgument(2));

        List<MenuDto.Response.MenuAdminRow> rows = menuService.getAllMenus(RoleAxis.PLATFORM);

        assertThat(rows).extracting(MenuDto.Response.MenuAdminRow::getMenuKey)
                .containsExactly("MENU_DASHBOARD", "MENU_ACCOUNTS");
        assertThat(rows).filteredOn(row -> row.getMenuKey().equals("MENU_ACCOUNTS"))
                .extracting(MenuDto.Response.MenuAdminRow::isActive)
                .containsExactly(false);
    }
}
