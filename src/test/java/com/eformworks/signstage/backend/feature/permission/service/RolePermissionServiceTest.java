package com.eformworks.signstage.backend.feature.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionType;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.entity.RolePermission;
import com.eformworks.signstage.backend.feature.permission.repository.PermissionDefinitionRepository;
import com.eformworks.signstage.backend.feature.permission.repository.RolePermissionHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.RolePermissionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private RolePermissionHistoryRepository rolePermissionHistoryRepository;
    @Mock
    private PermissionDefinitionRepository permissionDefinitionRepository;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    private PermissionDefinition menuDefinition;
    private RolePermission rolePermission;

    @BeforeEach
    void setUp() {
        menuDefinition = PermissionDefinition.builder()
                .permissionKey("MENU_ACCOUNTS")
                .permissionType(PermissionType.MENU)
                .roleAxis(RoleAxis.PLATFORM)
                .labelKey("navigation.adminAccounts")
                .displayOrder(0)
                .build();
        rolePermission = RolePermission.builder()
                .permissionDefinition(menuDefinition)
                .roleValue("PLATFORM_SUPER")
                .allowed(true)
                .build();
    }

    @Test
    void isAllowed_returnsTrue_whenRolePermissionExistsAndAllowed() {
        given(rolePermissionRepository.findAllByRoleValueAndAllowedTrueAndPermissionDefinitionActiveTrue("PLATFORM_SUPER"))
                .willReturn(List.of(rolePermission));

        assertThat(rolePermissionService.isAllowed("PLATFORM_SUPER", "MENU_ACCOUNTS")).isTrue();
        assertThat(rolePermissionService.isAllowed("PLATFORM_SUPER", "MENU_OTHER")).isFalse();
    }

    @Test
    void allowedKeys_cachesPerRole_untilAWriteInvalidatesIt() {
        given(rolePermissionRepository.findAllByRoleValueAndAllowedTrueAndPermissionDefinitionActiveTrue("PLATFORM_SUPPORT"))
                .willReturn(List.of());

        rolePermissionService.allowedKeys("PLATFORM_SUPPORT");
        rolePermissionService.allowedKeys("PLATFORM_SUPPORT");

        // 캐시가 동작하면 같은 역할에 대한 조회는 리포지토리를 한 번만 때린다(10장 캐싱 전략).
        verify(rolePermissionRepository).findAllByRoleValueAndAllowedTrueAndPermissionDefinitionActiveTrue("PLATFORM_SUPPORT");
    }

    @Test
    void setAllowed_rejectsNonSuperRole_withoutTouchingData() {
        assertThatThrownBy(() ->
                rolePermissionService.setAllowed("PLATFORM_OPS", RoleAxis.PLATFORM, 1L, "PLATFORM_OPS", false)
        ).isInstanceOf(ApplicationException.class);

        verify(permissionDefinitionRepository, never()).findById(any());
        verify(rolePermissionHistoryRepository, never()).save(any());
    }

    @Test
    void setAllowed_updatesValueAndAppendsHistory_whenActorIsSuper() {
        RolePermission targetRolePermission = RolePermission.builder()
                .permissionDefinition(menuDefinition).roleValue("PLATFORM_SUPPORT").allowed(true).build();
        given(permissionDefinitionRepository.findById(1L)).willReturn(Optional.of(menuDefinition));
        given(rolePermissionRepository.findByPermissionDefinitionAndRoleValue(menuDefinition, "PLATFORM_SUPPORT"))
                .willReturn(Optional.of(targetRolePermission));

        rolePermissionService.setAllowed("PLATFORM_SUPER", RoleAxis.PLATFORM, 1L, "PLATFORM_SUPPORT", false);

        assertThat(targetRolePermission.isAllowed()).isFalse();
        verify(rolePermissionHistoryRepository).save(any());
    }
}
