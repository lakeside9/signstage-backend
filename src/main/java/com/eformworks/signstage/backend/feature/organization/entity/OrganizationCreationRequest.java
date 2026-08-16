package com.eformworks.signstage.backend.feature.organization.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직 생성 요청. signstage-docs business/organization-creation-approval-review.md의
 * 결정 사항을 그대로 따른다 — 조직은 더 이상 사용자가 즉시 만들지 않고, 이 요청을
 * 플랫폼 관리자(PLATFORM_OPS 이상)가 승인해야 실제로 {@link Organization}/{@link Member}가
 * 생긴다(3.1/3.2절). 코드는 요청에 담지 않는다 — 승인 시점에 관리자가 정한다(3.3절).
 *
 * <p>{@code reviewedBy}는 {@code platform_admin_audit_log.admin_user_id}와 같은 이유로
 * FK 없는 순수 행위자 참조다({@code User} 연관관계로 두지 않음). {@code requestedBy}/
 * {@code organization}은 이 엔티티의 핵심 업무 관계라 일반 연관관계로 둔다.
 */
@Entity
@Table(name = "organization_creation_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationCreationRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "organization_name", nullable = false, length = 100)
    private String organizationName;

    /** 부가설명(선택). 심사 근거를 요구하는 "사유"가 아니라 참고용 자유 텍스트다. */
    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationCreationRequestStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Builder
    private OrganizationCreationRequest(User requestedBy, String organizationName, String note) {
        this.requestedBy = requestedBy;
        this.organizationName = organizationName;
        this.note = note;
        this.status = OrganizationCreationRequestStatus.PENDING;
    }

    /**
     * 승인 시 호출한다. 관리자가 요청 없이 조직을 직접 만드는 경우에도 이 메서드를 그대로
     * 써서 "PENDING을 거치지 않고 곧바로 APPROVED가 된" 요청 행으로 남긴다.
     */
    public void approve(Long reviewedBy, Organization organization) {
        this.status = OrganizationCreationRequestStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.organization = organization;
    }

    public void reject(Long reviewedBy, String rejectionReason) {
        this.status = OrganizationCreationRequestStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }

    /** PENDING 상태에서만 요청자 본인이 호출할 수 있다(서비스 레이어에서 검증). */
    public void cancel() {
        this.status = OrganizationCreationRequestStatus.CANCELLED;
    }
}
