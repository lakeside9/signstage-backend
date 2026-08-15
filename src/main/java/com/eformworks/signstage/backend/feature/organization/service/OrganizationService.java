package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationDto;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.repository.entity.Member;
import com.eformworks.signstage.backend.feature.organization.repository.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.repository.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.repository.entity.Organization;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * signstage-docs business/user-organization-design.md 5.1절 (a) "조직 최초 생성" 흐름과
 * 조직 조회를 구현한다. 초대 수락((b) 경로)은 organization_invitations이 아직 없어
 * 이번 범위 밖이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 조직을 만든 사람이 자동으로 OWNER가 된다. role을 요청값으로 받지 않는다
     * (signstage-docs business/user-organization-design.md 5.1절 (a)).
     */
    @Transactional
    public OrganizationDto.Response.Organization createOrganization(OrganizationDto.Request.CreateOrganization request) {
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_CODE_DUPLICATE);
        }
        if (userRepository.existsByLoginId(request.getOwnerLoginId())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.getOwnerEmail())) {
            throw new ApplicationException(IdentityErrorCode.DUPLICATE_EMAIL);
        }

        Organization organization = Organization.builder()
                .name(request.getOrganizationName())
                .code(request.getCode())
                .build();

        User owner = User.builder()
                .loginId(request.getOwnerLoginId())
                .name(request.getOwnerName())
                .email(request.getOwnerEmail())
                .locale(organization.getDefaultLocale())
                .password(passwordEncoder.encode(request.getOwnerPassword()))
                .build();
        userRepository.save(owner);

        // 이 시점에는 아직 로그인한 사용자가 없어 AuditorAware가 채울 수 없다.
        // 조직을 만든 소유자 자신이 생성 주체이므로 직접 지정한다.
        organization.assignCreatedBy(owner.getId());
        organizationRepository.save(organization);

        Member ownerMembership = Member.builder()
                .organization(organization)
                .user(owner)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        ownerMembership.assignCreatedBy(owner.getId());
        memberRepository.save(ownerMembership);

        return toOrganizationResponse(organization);
    }

    public OrganizationDto.Response.Organization retrieveOrganization(Long organizationId, Long currentUserId) {
        Organization organization = findOrganizationOrThrow(organizationId);
        checkActiveMember(organizationId, currentUserId);
        return toOrganizationResponse(organization);
    }

    public List<OrganizationDto.Response.Organization> findMyOrganizations(Long currentUserId) {
        return memberRepository.findAllByUserIdAndStatus(currentUserId, MemberStatus.ACTIVE).stream()
                .map(Member::getOrganization)
                .map(this::toOrganizationResponse)
                .toList();
    }

    private void checkActiveMember(Long organizationId, Long userId) {
        memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private OrganizationDto.Response.Organization toOrganizationResponse(Organization organization) {
        return new OrganizationDto.Response.Organization(
                organization.getId(),
                organization.getName(),
                organization.getCode(),
                organization.getStatus().name(),
                organization.getDefaultLocale(),
                organization.getCreatedAt()
        );
    }
}
