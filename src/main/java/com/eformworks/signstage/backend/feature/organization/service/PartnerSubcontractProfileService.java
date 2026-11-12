package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.organization.dto.PartnerSubcontractProfileDto;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import com.eformworks.signstage.backend.feature.organization.entity.PartnerBusinessType;
import com.eformworks.signstage.backend.feature.organization.entity.PartnerSubcontractProfile;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.MemberRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.organization.repository.PartnerSubcontractProfileRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 외주 수행사 정보(사업자·정산 계좌·계약 동의) — signstage-docs
 * business/platform-order-partner-subcontract-review.md D22. 파트너는
 * {@code ACTION_PARTNER_PROFILE_EDIT}(OWNER)로 입력·조회하고, 플랫폼은
 * {@code ACTION_PARTNER_PROFILE_VERIFY}로 예금주를 확인한다. 발주 발급 전에
 * {@link #isOrderEligible}로 요건을 검사한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerSubcontractProfileService {

    private final PartnerSubcontractProfileRepository partnerSubcontractProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public PartnerSubcontractProfileDto.Response.Profile retrieveProfile(Long organizationId, Long currentUserId) {
        checkCanEdit(organizationId, currentUserId);
        return partnerSubcontractProfileRepository.findByOrganizationId(organizationId)
                .map(PartnerSubcontractProfileDto.Response.Profile::from)
                .orElseGet(() -> PartnerSubcontractProfileDto.Response.Profile.empty(organizationId));
    }

    @Transactional
    public PartnerSubcontractProfileDto.Response.Profile updateProfile(
            Long organizationId,
            Long currentUserId,
            PartnerSubcontractProfileDto.Request.UpdateProfile request
    ) {
        checkCanEdit(organizationId, currentUserId);
        if (request.getBusinessType() != PartnerBusinessType.INDIVIDUAL
                && (request.getBusinessRegistrationNumber() == null || request.getBusinessRegistrationNumber().isBlank())) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_PARTNER_BUSINESS_NUMBER_REQUIRED);
        }
        PartnerSubcontractProfile profile = partnerSubcontractProfileRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> partnerSubcontractProfileRepository.save(
                        PartnerSubcontractProfile.builder().organization(findOrganizationOrThrow(organizationId)).build()
                ));
        profile.updateBusinessInfo(
                request.getBusinessType(), request.getBusinessRegistrationNumber(), request.getRepresentativeName()
        );
        profile.updateSettlementAccount(request.getBankName(), request.getBankAccountNumber(), request.getAccountHolderName());
        if (request.isAgreeSubcontractTerms()) {
            profile.agreeSubcontractTerms();
        }
        if (request.isAgreePrivacyConsignment()) {
            profile.agreePrivacyConsignment();
        }
        return PartnerSubcontractProfileDto.Response.Profile.from(profile);
    }

    public PartnerSubcontractProfileDto.Response.Profile retrieveProfileByPlatformAdmin(Long organizationId) {
        findOrganizationOrThrow(organizationId);
        return partnerSubcontractProfileRepository.findByOrganizationId(organizationId)
                .map(PartnerSubcontractProfileDto.Response.Profile::from)
                .orElseGet(() -> PartnerSubcontractProfileDto.Response.Profile.empty(organizationId));
    }

    /** 플랫폼이 예금주를 확인했음을 기록한다. 계좌가 다시 바뀌면 확인이 풀린다. */
    @Transactional
    public PartnerSubcontractProfileDto.Response.Profile verifySettlementAccount(
            Long organizationId,
            Long adminUserId,
            String actingPlatformRole
    ) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, "ACTION_PARTNER_PROFILE_VERIFY")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        PartnerSubcontractProfile profile = partnerSubcontractProfileRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_PARTNER_PROFILE_NOT_FOUND));
        if (!profile.hasSettlementAccount()) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_PARTNER_ACCOUNT_NOT_REGISTERED);
        }
        profile.verifyAccount(adminUserId);

        auditLogRecorder.record(
                adminUserId, PlatformAdminAction.VERIFY_PARTNER_SETTLEMENT_ACCOUNT, null, organizationId,
                "bankName=" + profile.getBankName() + ", accountHolderName=" + profile.getAccountHolderName()
        );
        return PartnerSubcontractProfileDto.Response.Profile.from(profile);
    }

    /** 지금 발주를 받을 수 있는 파트너 조직 — 활성·비데모이고 외주 정보 요건(D22)을 채웠다. */
    public List<Long> findOrderEligibleOrganizationIds() {
        return partnerSubcontractProfileRepository.findAll().stream()
                .filter(PartnerSubcontractProfile::isOrderEligible)
                .map(PartnerSubcontractProfile::getOrganization)
                .filter(organization -> organization.getStatus() == OrganizationStatus.ACTIVE && !organization.isDemo())
                .map(Organization::getId)
                .toList();
    }

    /** 발주 발급 요건(D22)을 모두 채웠는지. 정보를 한 번도 입력하지 않았으면 false다. */
    public boolean isOrderEligible(Long organizationId) {
        return partnerSubcontractProfileRepository.findByOrganizationId(organizationId)
                .map(PartnerSubcontractProfile::isOrderEligible)
                .orElse(false);
    }

    private void checkCanEdit(Long organizationId, Long currentUserId) {
        Member member = memberRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, currentUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.ACCESS_DENIED));
        if (!rolePermissionService.isAllowed(member.getRole().name(), "ACTION_PARTNER_PROFILE_EDIT")) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private Organization findOrganizationOrThrow(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
    }
}
