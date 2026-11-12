package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyAssignmentRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyRepository;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 조회·조직 멤버 확인·접근 권한/상태 검사 공용 헬퍼. 조직 스코핑은 JWT 클레임이 아니라
 * 매 요청마다 organization_members를 직접 조회해 판단한다(기존 {@code MemberService}와 같은
 * 패턴). ceremony의 여러 서비스가 같은 패키지에서 package-private 메서드로 공유한다.
 * 2026-11-11 {@code CeremonyService}에서 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyAccessService {

    private final CeremonyRepository ceremonyRepository;
    private final CeremonyAssignmentRepository ceremonyAssignmentRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RolePermissionService rolePermissionService;

    Ceremony findCeremonyInOrganizationOrThrow(Long organizationId, Long ceremonyId) {
        Ceremony ceremony = ceremonyRepository.findById(ceremonyId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND));
        if (!ceremony.getOrganization().getId().equals(organizationId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_NOT_FOUND);
        }
        return ceremony;
    }

    Member findActiveMemberOrThrow(Long organizationId, Long userId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseGet(() -> resolveDemoVirtualMemberOrThrow(organizationId, userId));
    }

    /**
     * 데모 조직 우회 — signstage-docs
     * business/demo-account-exhibition-signer-preview-review.md 11.2절(2026-09-09, 결정 번복).
     * 실제 {@code Member} 행이 없을 때만(비용이 큰 조회는 평소 조직 사용자 흐름에 영향이 없도록
     * 실패 경로에서만 시도한다) 호출자가 플랫폼 관리자이고 그 조직이 데모 조직(Organization.isDemo)
     * 이면, 저장하지 않는 가상의 Member를 그 자리에서 만들어 돌려준다 — 이 메서드를 공유하는
     * {@code CeremonyEventService}/{@code CeremonyEventEffectSettingService}/
     * {@code CeremonyEffectRuntimeService}/{@code CeremonyResultService}/{@code SignerService}/
     * {@code TemplateService}/{@code TemplateFieldService} 8개 서비스 전부에 자동 파급된다.
     *
     * <p>가상 Member의 역할은 호출자의 플랫폼 등급에 따라 갈린다 — {@code ACTION_DEMO_CEREMONY_MANAGE}
     * 권한이 있으면(PLATFORM_OPS 이상) 전체 관리가 가능한 OWNER, 없으면(PLATFORM_SUPPORT) 조회만
     * 가능한 VIEWER다. 이 메서드 자체는 두 등급을 구분하지 않고 항상 같은 코드 경로를 타지만,
     * 이미 있는 역할 기반 검사(예: {@code checkCeremonyManageAccess}가 쓰는
     * {@code ACTION_CEREMONY_MANAGE})가 VIEWER는 그대로 걸러내므로 새 검사를 추가하지 않아도
     * 두 등급이 자연스럽게 나뉜다.
     */
    private Member resolveDemoVirtualMemberOrThrow(Long organizationId, Long userId) {
        Organization organization = organizationRepository.findById(organizationId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (organization != null && organization.isDemo() && user != null && user.getPlatformRole() != null) {
            MemberRole virtualRole = rolePermissionService.isAllowed(user.getPlatformRole().name(), "ACTION_DEMO_CEREMONY_MANAGE")
                    ? MemberRole.OWNER
                    : MemberRole.VIEWER;
            return Member.builder().organization(organization).user(user).role(virtualRole).status(MemberStatus.ACTIVE).build();
        }
        throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
    }

    /** OWNER/ADMIN/VIEWER는 조직의 모든 행사를 조회할 수 있고, OPERATOR는 배정된 행사만 조회할 수 있다. */
    void checkCeremonyReadAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 행사 생성/수정(단위 상품 구매, 하위 행사 생성 등) 권한. OWNER/ADMIN은 항상 가능하고,
     * OPERATOR는 배정된 행사만, VIEWER는 불가하다(user-organization-design.md 4.2절
     * "행사(Ceremony) 생성/수정/삭제").
     */
    void checkCeremonyManageAccess(Ceremony ceremony, Member actingMember, Long currentUserId) {
        if (!rolePermissionService.isAllowed(actingMember.getRole().name(), "ACTION_CEREMONY_MANAGE")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        // OPERATOR의 "본인/배정 건만" 제약은 소유권 기반 불변식이라 role_permissions로 옮기지
        // 않는다(signstage-docs business/menu-and-action-permission-management-review.md 3장).
        if (actingMember.getRole() == MemberRole.OPERATOR) {
            checkAssigned(ceremony, currentUserId);
        }
    }

    /**
     * 플랜 변경·추가구매·고객 견적·현장지원 요청은 파트너 직접 수주 행사에서만 쓴다 — 플랫폼 수주
     * 행사는 범위와 가격을 플랫폼이 수주·발주로 관리한다(signstage-docs
     * business/platform-order-partner-subcontract-review.md 4.3~4.7절).
     */
    void checkPartnerDirectCeremony(Ceremony ceremony) {
        if (ceremony.isPlatformOrder()) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLATFORM_ORDER_RESTRICTED);
        }
    }

    /**
     * 완료(COMPLETED)된 Ceremony 아래에서는 하위 데이터를 더 이상 수정할 수 없다 — 조회만 가능하다.
     * 결과물 생성처럼 완료를 유발하는 호출 자체는 이 체크가 통과한 뒤(아직 IN_PROGRESS일 때)
     * 실행되고, 완료 전이는 그 성공 이후에 일어나므로 스스로를 막지 않는다.
     */
    void checkCeremonyEditable(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_ALREADY_COMPLETED);
        }
    }

    /**
     * 서명자/문서/하위 행사 등록은 플랜이 확정된(DRAFT를 벗어난) Ceremony에서만 허용한다.
     * {@link SignerService}/{@link TemplateService}/{@link CeremonyEventService}가
     * {@link #checkCeremonyEditable}과 함께 재사용한다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.3절.
     */
    void checkCeremonyPlanConfirmed(Ceremony ceremony) {
        if (ceremony.getStatus() == CeremonyStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        }
    }

    private void checkAssigned(Ceremony ceremony, Long currentUserId) {
        if (!ceremonyAssignmentRepository.existsByCeremonyIdAndUserId(ceremony.getId(), currentUserId)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
