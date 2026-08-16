package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationDto;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 조회를 구현한다(signstage-docs business/user-organization-design.md 5장). 조직
 * 생성은 더 이상 이 서비스가 다루지 않는다 — {@code feature.organization.service
 * .OrganizationCreationRequestService}로 요청을 제출하고, 플랫폼 관리자 승인을 거쳐야
 * 조직이 만들어진다(business/organization-creation-approval-review.md). 초대 수락
 * ((b) 경로)은 organization_invitations이 아직 없어 이번 범위 밖이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;

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
