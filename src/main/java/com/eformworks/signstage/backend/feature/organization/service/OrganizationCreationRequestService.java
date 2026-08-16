package com.eformworks.signstage.backend.feature.organization.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.UserRepository;
import com.eformworks.signstage.backend.feature.organization.dto.OrganizationCreationRequestDto;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequest;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequestStatus;
import com.eformworks.signstage.backend.feature.organization.error.OrganizationErrorCode;
import com.eformworks.signstage.backend.feature.organization.repository.OrganizationCreationRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일반 사용자의 조직 생성 요청 제출/취소/조회를 구현한다. signstage-docs
 * business/organization-creation-approval-review.md의 결정 사항을 그대로 따른다 — 조직은
 * 이 요청이 승인돼야 만들어진다(승인/반려는 {@code feature.platformadmin}에서 다룬다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationCreationRequestService {

    /** 재신청 포함 최대 제출 횟수(3.4/7.2절) — 마지막 승인 이후로 다시 센다. */
    private static final int MAX_REQUESTS_PER_CYCLE = 5;

    /**
     * "마지막 승인 이력 없음"을 나타내는 하한값. {@code LocalDateTime.MIN}은 MySQL
     * {@code TIMESTAMP} 컬럼의 표현 범위(1970-01-01~2038-01-19)를 넘어서 비교 쿼리 자체가
     * SQLException으로 실패한다 — 그 범위 안의, 실제 요청이 있을 수 없는 과거 시각을 대신 쓴다.
     */
    private static final LocalDateTime NO_PRIOR_APPROVAL = LocalDateTime.of(1970, 1, 2, 0, 0);

    private final OrganizationCreationRequestRepository requestRepository;
    private final UserRepository userRepository;

    /**
     * 동시 PENDING 요청은 1건만 허용하고(3.4절), 재신청은 최초 요청을 포함해 최대 5회까지만
     * 허용한다(승인되면 리셋, 취소도 카운트 포함 — 7.2절).
     */
    @Transactional
    public OrganizationCreationRequestDto.Response.RequestSummary submit(
            Long currentUserId,
            OrganizationCreationRequestDto.Request.Create request
    ) {
        if (requestRepository.existsByRequestedByIdAndStatus(currentUserId, OrganizationCreationRequestStatus.PENDING)) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_ALREADY_PENDING);
        }

        LocalDateTime cutoff = requestRepository
                .findTopByRequestedByIdAndStatusOrderByReviewedAtDesc(currentUserId, OrganizationCreationRequestStatus.APPROVED)
                .map(OrganizationCreationRequest::getReviewedAt)
                .orElse(NO_PRIOR_APPROVAL);
        long countSinceLastApproval = requestRepository.countByRequestedByIdAndCreatedAtAfter(currentUserId, cutoff);
        if (countSinceLastApproval >= MAX_REQUESTS_PER_CYCLE) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_LIMIT_EXCEEDED);
        }

        User requester = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApplicationException(IdentityErrorCode.INVALID_CREDENTIAL));

        OrganizationCreationRequest creationRequest = OrganizationCreationRequest.builder()
                .requestedBy(requester)
                .organizationName(request.getOrganizationName())
                .note(blankToNull(request.getNote()))
                .build();
        requestRepository.save(creationRequest);

        return toSummary(creationRequest);
    }

    public List<OrganizationCreationRequestDto.Response.RequestSummary> findMyRequests(Long currentUserId) {
        return requestRepository.findAllByRequestedByIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::toSummary)
                .toList();
    }

    /** PENDING 상태의 본인 요청만 취소할 수 있다. 취소된 요청은 CANCELLED로 남는다(삭제하지 않음). */
    @Transactional
    public void cancel(Long requestId, Long currentUserId) {
        OrganizationCreationRequest creationRequest = findOrThrow(requestId);
        if (!creationRequest.getRequestedBy().getId().equals(currentUserId)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
        if (creationRequest.getStatus() != OrganizationCreationRequestStatus.PENDING) {
            throw new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_NOT_PENDING);
        }
        creationRequest.cancel();
    }

    private OrganizationCreationRequest findOrThrow(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ApplicationException(OrganizationErrorCode.ORGANIZATION_REQUEST_NOT_FOUND));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private OrganizationCreationRequestDto.Response.RequestSummary toSummary(OrganizationCreationRequest creationRequest) {
        return new OrganizationCreationRequestDto.Response.RequestSummary(
                creationRequest.getId(),
                creationRequest.getOrganizationName(),
                creationRequest.getNote(),
                creationRequest.getStatus().name(),
                creationRequest.getRejectionReason(),
                creationRequest.getReviewedAt(),
                creationRequest.getOrganization() != null ? creationRequest.getOrganization().getId() : null,
                creationRequest.getCreatedAt()
        );
    }
}
