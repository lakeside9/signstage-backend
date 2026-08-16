package com.eformworks.signstage.backend.feature.platformadmin.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequest;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequestStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationCreationRequestRepository;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationRepository;
import com.eformworks.signstage.backend.feature.platformadmin.dto.PlatformAdminOrganizationRequestDto;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 관리자의 조직 생성 요청 승인/반려를 구현한다(signstage-docs
 * business/organization-creation-approval-review.md). 조회는 PLATFORM_SUPPORT 이상,
 * 승인/반려는 PLATFORM_OPS 이상만 가능하다 — 기존 조직 등록·상태 변경과 같은 등급이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminOrganizationRequestService {

    private static final Set<String> ORGANIZATION_CONTROL_ALLOWED_ROLES = Set.of("PLATFORM_OPS", "PLATFORM_SUPER");

    private final OrganizationCreationRequestRepository requestRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PlatformAdminOrganizationService platformAdminOrganizationService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public Page<PlatformAdminOrganizationRequestDto.Response.RequestSummary> findRequests(
            OrganizationCreationRequestStatus status,
            Pageable pageable
    ) {
        Page<OrganizationCreationRequest> requests = status != null
                ? requestRepository.findAllByStatus(status, pageable)
                : requestRepository.findAll(pageable);

        Map<Long, String> loginIdsByUserId = resolveReviewerLoginIds(requests.getContent());
        return requests.map(request -> toSummary(request, loginIdsByUserId));
    }

    /**
     * 승인 = 관리자 대행 등록과 같은 저장 로직을 탄다({@link PlatformAdminOrganizationService
     * #saveOrganizationWithOwner}). 요청 자체는 코드를 담지 않으므로 관리자가 승인 시점에 입력한다
     * (3.3절). 1인 1조직 제한(2026-08-16 결정)과 보유 조직 개수 제한(최대 10개, 7.3절)도 이
     * 시점에 검사한다. 감사 로그는 {@code CREATE_ORGANIZATION}을 재사용한다(신설하지 않음 —
     * 7.5절 결정됨).
     */
    @Transactional
    public PlatformAdminOrganizationRequestDto.Response.RequestSummary approve(
            Long requestId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminOrganizationRequestDto.Request.Approve request
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationCreationRequest creationRequest = findPendingOrThrow(requestId);
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_CODE_DUPLICATE);
        }

        User owner = creationRequest.getRequestedBy();
        platformAdminOrganizationService.checkSingleOrganizationLimit(owner);
        platformAdminOrganizationService.checkOwnerLimit(owner);

        Organization organization = platformAdminOrganizationService.saveOrganizationWithOwner(
                creationRequest.getOrganizationName(), request.getCode(), owner
        );
        creationRequest.approve(actingUserId, organization);

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.CREATE_ORGANIZATION, owner.getId(), organization.getId(),
                "code=" + organization.getCode() + ", ownerLoginId=" + owner.getLoginId() + ", requestId=" + requestId
        );
        return toSummary(creationRequest, resolveReviewerLoginIds(List.of(creationRequest)));
    }

    @Transactional
    public PlatformAdminOrganizationRequestDto.Response.RequestSummary reject(
            Long requestId,
            Long actingUserId,
            String actingPlatformRole,
            PlatformAdminOrganizationRequestDto.Request.Reject request
    ) {
        checkCanManage(actingPlatformRole);
        OrganizationCreationRequest creationRequest = findPendingOrThrow(requestId);
        creationRequest.reject(actingUserId, request.getRejectionReason());

        auditLogRecorder.record(
                actingUserId, PlatformAdminAction.REJECT_ORGANIZATION_REQUEST, creationRequest.getRequestedBy().getId(), null,
                "organizationName=" + creationRequest.getOrganizationName() + ", reason=" + request.getRejectionReason()
        );
        return toSummary(creationRequest, resolveReviewerLoginIds(List.of(creationRequest)));
    }

    private void checkCanManage(String actingPlatformRole) {
        if (!ORGANIZATION_CONTROL_ALLOWED_ROLES.contains(actingPlatformRole)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private OrganizationCreationRequest findPendingOrThrow(Long requestId) {
        OrganizationCreationRequest creationRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_NOT_FOUND));
        if (creationRequest.getStatus() != OrganizationCreationRequestStatus.PENDING) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_NOT_PENDING);
        }
        return creationRequest;
    }

    private Map<Long, String> resolveReviewerLoginIds(List<OrganizationCreationRequest> requests) {
        List<Long> reviewerIds = requests.stream()
                .map(OrganizationCreationRequest::getReviewedBy)
                .filter(java.util.Objects::nonNull)
                .toList();
        return userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getLoginId));
    }

    private PlatformAdminOrganizationRequestDto.Response.RequestSummary toSummary(
            OrganizationCreationRequest creationRequest,
            Map<Long, String> reviewerLoginIdsByUserId
    ) {
        User requestedBy = creationRequest.getRequestedBy();
        String reviewerLoginId = creationRequest.getReviewedBy() != null
                ? reviewerLoginIdsByUserId.get(creationRequest.getReviewedBy())
                : null;
        return new PlatformAdminOrganizationRequestDto.Response.RequestSummary(
                creationRequest.getId(),
                requestedBy.getId(),
                requestedBy.getLoginId(),
                requestedBy.getName(),
                creationRequest.getOrganizationName(),
                creationRequest.getNote(),
                creationRequest.getStatus().name(),
                creationRequest.getRejectionReason(),
                reviewerLoginId,
                creationRequest.getReviewedAt(),
                creationRequest.getOrganization() != null ? creationRequest.getOrganization().getId() : null,
                creationRequest.getCreatedAt()
        );
    }
}
