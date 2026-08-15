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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직 소속 + 역할(OrganizationMember). 한 User가 여러 Organization에 각각 다른 역할로
 * 동시에 속할 수 있다(signstage-docs business/user-organization-design.md 3.2절).
 *
 * <p>초대 토큰(organization_invitations) 기반 가입은 이번 최소 구현 범위 밖이다.
 * 지금은 OWNER/ADMIN이 이미 가입된 User를 loginId로 바로 추가하는 방식만 지원하며,
 * {@code invitedAt}은 그 흐름이 추가될 때 쓰기 위해 컬럼만 미리 마련해 둔다.
 */
@Entity
@Table(
        name = "organization_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_org_member", columnNames = {"organization_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Builder
    private Member(Organization organization, User user, MemberRole role, MemberStatus status, LocalDateTime joinedAt) {
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.status = status != null ? status : MemberStatus.ACTIVE;
        this.joinedAt = joinedAt;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    public void remove() {
        this.status = MemberStatus.REMOVED;
    }
}
