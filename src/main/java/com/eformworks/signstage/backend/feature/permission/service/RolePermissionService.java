package com.eformworks.signstage.backend.feature.permission.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.permission.dto.PermissionDto;
import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.entity.RolePermission;
import com.eformworks.signstage.backend.feature.permission.entity.RolePermissionHistory;
import com.eformworks.signstage.backend.feature.permission.error.PermissionErrorCode;
import com.eformworks.signstage.backend.feature.permission.repository.PermissionDefinitionRepository;
import com.eformworks.signstage.backend.feature.permission.repository.RolePermissionHistoryRepository;
import com.eformworks.signstage.backend.feature.permission.repository.RolePermissionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안 C(하이브리드) 핵심 서비스 — {@code RolePermissionService.isAllowed(axis, roleValue,
 * permissionKey)} 하나로 지금 서비스마다 흩어진 {@code Set.of(...).contains(...)}/인라인 비교문을
 * 순차 교체하는 게 목표다(signstage-docs
 * business/menu-and-action-permission-management-review.md 10장). 이 커밋은 스키마와 이
 * 서비스까지만 준비한다 — 기존 하드코딩 교체는 점진적 마이그레이션(12장 결정 #8)으로 별도
 * 진행한다.
 *
 * <p><b>캐싱(12장 결정 #7)</b>: 권한 변경 빈도는 낮고 조회는 매우 잦으므로, 역할값별 "허용
 * 권한키 집합"을 인메모리에 캐싱하고 변경 시 전체 무효화한다(우선 로그인 시점 1회 로드로
 * 시작 — 정교한 부분 무효화는 착수 시 필요해지면 추가한다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RolePermissionHistoryRepository rolePermissionHistoryRepository;
    private final PermissionDefinitionRepository permissionDefinitionRepository;

    private final Map<String, Set<String>> allowedKeysByRole = new ConcurrentHashMap<>();

    public boolean isAllowed(String roleValue, String permissionKey) {
        return allowedKeys(roleValue).contains(permissionKey);
    }

    public Set<String> allowedKeys(String roleValue) {
        return allowedKeysByRole.computeIfAbsent(roleValue, this::loadAllowedKeys);
    }

    private Set<String> loadAllowedKeys(String roleValue) {
        return rolePermissionRepository
                .findAllByRoleValueAndAllowedTrueAndPermissionDefinitionActiveTrue(roleValue).stream()
                .map(rolePermission -> rolePermission.getPermissionDefinition().getPermissionKey())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 관리 화면이 "역할 × 권한키" 매트릭스를 그리기 위한 조회 — {@link PermissionDefinition}
     * 전체(카탈로그)에 {@link RolePermission}(설정값, 없으면 provisioning 필요)을 붙여 반환한다.
     */
    public List<PermissionDefinition> findDefinitions(RoleAxis roleAxis) {
        return permissionDefinitionRepository.findAllByRoleAxisAndActiveTrueOrderByDisplayOrderAsc(roleAxis);
    }

    public List<RolePermission> findRolePermissions(RoleAxis roleAxis) {
        return rolePermissionRepository.findAllByPermissionDefinitionRoleAxis(roleAxis);
    }

    /** 관리 화면이 그리는 "역할 × 권한키" 매트릭스 전체 — 컨트롤러가 {@code PLATFORM_SUPER}인지 먼저 검사한다(11장). */
    public List<PermissionDto.Response.PermissionMatrixRow> matrixFor(RoleAxis roleAxis) {
        Map<Long, List<RolePermission>> byDefinitionId = findRolePermissions(roleAxis).stream()
                .collect(Collectors.groupingBy(rolePermission -> rolePermission.getPermissionDefinition().getId()));

        return findDefinitions(roleAxis).stream()
                .map(definition -> new PermissionDto.Response.PermissionMatrixRow(
                        definition.getId(),
                        definition.getPermissionKey(),
                        definition.getPermissionType().name(),
                        definition.getLabelKey(),
                        definition.getDisplayOrder(),
                        byDefinitionId.getOrDefault(definition.getId(), List.of()).stream()
                                .map(rp -> new PermissionDto.Response.RoleAllowance(rp.getRoleValue(), rp.isAllowed()))
                                .toList()
                ))
                .toList();
    }

    /**
     * 관리 화면 전용 — 컨트롤러가 {@code PLATFORM_SUPER} 등급을 먼저 검사한 뒤에만 호출해야
     * 한다(11장, 12장 결정 #4). 이 화면 자체에 대한 접근권은 의도적으로 이 메커니즘(데이터
     * 기반 role_permissions)의 설정 대상에 포함하지 않는다 — 하드코딩된 등급 검사만으로 지켜야
     * 마지막 PLATFORM_SUPER가 실수로 스스로를 잠그는 사고(12장 결정 #6, 자기 잠금 방지)를
     * 구조적으로 막을 수 있다.
     */
    @Transactional
    public void setAllowed(
            String actingPlatformRole, RoleAxis roleAxis, Long permissionDefinitionId, String roleValue, boolean allowed
    ) {
        checkSuperRole(actingPlatformRole);
        PermissionDefinition definition = permissionDefinitionRepository.findById(permissionDefinitionId)
                .orElseThrow(() -> new ApplicationException(PermissionErrorCode.PERMISSION_DEFINITION_NOT_FOUND));
        if (definition.getRoleAxis() != roleAxis) {
            throw new ApplicationException(PermissionErrorCode.ROLE_VALUE_INVALID);
        }
        RolePermission rolePermission = rolePermissionRepository
                .findByPermissionDefinitionAndRoleValue(definition, roleValue)
                .orElseThrow(() -> new ApplicationException(PermissionErrorCode.ROLE_PERMISSION_NOT_FOUND));

        rolePermission.changeAllowed(allowed);
        rolePermissionHistoryRepository.save(RolePermissionHistory.builder().rolePermission(rolePermission).build());
        allowedKeysByRole.clear();
    }

    /**
     * 권한 관리 화면 자체의 접근권은 의도적으로 {@code role_permissions} 설정 대상에 넣지 않고
     * 이렇게 하드코딩으로만 지킨다(11장, 12장 결정 #4/#6) — 데이터로 뺐다면 마지막
     * {@code PLATFORM_SUPER}가 실수로 스스로를 잠그는 사고를 막을 방법이 없어진다.
     */
    private void checkSuperRole(String actingPlatformRole) {
        if (!"PLATFORM_SUPER".equals(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 새 {@link PermissionDefinition}을 등록할 때 그 축에 속한 모든 역할값에 기본 행을 함께
     * 만든다(7.3절 "촘촘한 표") — 배포(마이그레이션)가 호출하는 초기 시딩 경로다.
     */
    @Transactional
    public void provisionDefaultRolePermissions(PermissionDefinition definition, boolean defaultAllowed) {
        for (String roleValue : RoleAxisRoles.of(definition.getRoleAxis())) {
            rolePermissionRepository.findByPermissionDefinitionAndRoleValue(definition, roleValue)
                    .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                            .permissionDefinition(definition)
                            .roleValue(roleValue)
                            .allowed(defaultAllowed)
                            .build()));
        }
        allowedKeysByRole.clear();
    }
}
