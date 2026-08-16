package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequest;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationCreationRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationCreationRequestRepository extends JpaRepository<OrganizationCreationRequest, Long> {

    List<OrganizationCreationRequest> findAllByRequestedByIdOrderByCreatedAtDesc(Long requestedById);

    boolean existsByRequestedByIdAndStatus(Long requestedById, OrganizationCreationRequestStatus status);

    /**
     * 재신청 횟수 제한(최초 포함 총 5회, 승인 시 리셋)에 쓴다 — {@code after} 이후 제출된
     * 요청 수를 센다. 마지막 승인 이력이 없으면 호출부가 {@code LocalDateTime.MIN}을 넘긴다.
     */
    long countByRequestedByIdAndCreatedAtAfter(Long requestedById, LocalDateTime after);

    Optional<OrganizationCreationRequest> findTopByRequestedByIdAndStatusOrderByReviewedAtDesc(
            Long requestedById, OrganizationCreationRequestStatus status
    );

    Page<OrganizationCreationRequest> findAllByStatus(OrganizationCreationRequestStatus status, Pageable pageable);
}
