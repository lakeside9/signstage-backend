package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationDto;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationHistoryRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 조회 + 정보 수정을 구현한다(signstage-docs business/user-organization-design.md 5장). 조직
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
    private final OrganizationHistoryRepository organizationHistoryRepository;

    public OrganizationDto.Response.Organization retrieveOrganization(Long organizationId, Long currentUserId) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member member = findActiveMemberOrThrow(organizationId, currentUserId);
        return toOrganizationResponse(organization, member.getRole());
    }

    public List<OrganizationDto.Response.Organization> findMyOrganizations(Long currentUserId) {
        return memberRepository.findAllByUserIdAndStatus(currentUserId, MemberStatus.ACTIVE).stream()
                .map(member -> toOrganizationResponse(member.getOrganization(), member.getRole()))
                .toList();
    }

    /**
     * OWNER만 조직 정보(이름/기본 언어)를 수정할 수 있다(screen-composition-plan.md "조직 설정" 항목).
     * code는 조직 식별자라 이 API로 바꾸지 않는다.
     */
    @Transactional
    public OrganizationDto.Response.Organization updateOrganization(
            Long organizationId,
            Long currentUserId,
            OrganizationDto.Request.UpdateOrganization request
    ) {
        Organization organization = findOrganizationOrThrow(organizationId);
        Member member = findActiveMemberOrThrow(organizationId, currentUserId);
        if (member.getRole() != MemberRole.OWNER) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        organization.updateInfo(request.getName(), request.getDefaultLocale());
        recordOrganizationHistory(organization);
        return toOrganizationResponse(organization, member.getRole());
    }

    /**
     * 변경 이력 조회. 조직 정보를 사용자 본인이 바꿨는지 플랫폼 관리자가 바꿨는지는 각 행의
     * {@code createdBy}로 구분한다(2026-08-30 요청 — "사용자가 변경하거나 관리자가 변경하거나
     * 모두 남겨주세요"). ACTIVE 멤버라면 누구나 조회할 수 있다 — 수정 자체는 OWNER 전용이지만
     * 이력 열람까지 OWNER로 좁힐 이유는 없다.
     */
    public List<OrganizationDto.Response.OrganizationHistorySummary> findOrganizationHistory(
            Long organizationId,
            Long currentUserId
    ) {
        findActiveMemberOrThrow(organizationId, currentUserId);
        return organizationHistoryRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    private void recordOrganizationHistory(Organization organization) {
        organizationHistoryRepository.save(OrganizationHistory.builder().organization(organization).build());
    }

    private OrganizationDto.Response.OrganizationHistorySummary toHistorySummary(OrganizationHistory history) {
        return new OrganizationDto.Response.OrganizationHistorySummary(
                history.getId(),
                history.getName(),
                history.getCode(),
                history.getStatus().name(),
                history.getDefaultLocale(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private Member findActiveMemberOrThrow(Long organizationId, Long userId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }

    private OrganizationDto.Response.Organization toOrganizationResponse(Organization organization, MemberRole myRole) {
        return new OrganizationDto.Response.Organization(
                organization.getId(),
                organization.getName(),
                organization.getCode(),
                organization.getStatus().name(),
                organization.getDefaultLocale(),
                organization.getCreatedAt(),
                myRole.name()
        );
    }
}
