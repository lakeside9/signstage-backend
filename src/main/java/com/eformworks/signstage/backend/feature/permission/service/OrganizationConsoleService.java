package com.eformworks.signstage.backend.feature.permission.service;

import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.permission.dto.MenuDto;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 사용자 콘솔(ORGANIZATION 축)의 "내 메뉴/권한" 조회 — 플랫폼 콘솔과 달리 호출자의 역할이
 * JWT에 실려 있지 않고(signstage-docs business/user-organization-design.md 5.2절 미구현)
 * organization_members에서 직접 찾아야 한다. 1인 1조직 제한(2026-08-16 결정)이 있어 활성
 * 멤버십은 최대 1건이다. 멤버십이 없으면(예: 방금 탈퇴) 빈 메뉴/권한으로 안전하게 처리한다 —
 * 호출 자체를 에러로 막지 않는다(다른 API가 이미 403으로 막는다, 2.3절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationConsoleService {

    private final MemberRepository memberRepository;
    private final MenuService menuService;
    private final RolePermissionService rolePermissionService;

    public List<MenuDto.Response.MenuNode> getMyMenuTree(Long userId) {
        return resolveMyRole(userId)
                .map(role -> menuService.getMenuTree(RoleAxis.ORGANIZATION, role.name()))
                .orElseGet(List::of);
    }

    public Set<String> getMyPermissionKeys(Long userId) {
        return resolveMyRole(userId)
                .map(role -> rolePermissionService.allowedKeys(role.name()))
                .orElseGet(Set::of);
    }

    public Optional<MemberRole> resolveMyRole(Long userId) {
        return memberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .findFirst()
                .map(Member::getRole);
    }
}
