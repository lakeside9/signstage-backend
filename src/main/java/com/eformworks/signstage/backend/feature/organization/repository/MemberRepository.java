package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, MemberStatus status);

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    List<Member> findAllByOrganizationIdAndStatusNot(Long organizationId, MemberStatus status);

    List<Member> findAllByUserIdAndStatus(Long userId, MemberStatus status);

    List<Member> findAllByUserIdAndStatusNot(Long userId, MemberStatus status);

    long countByOrganizationIdAndRoleAndStatus(Long organizationId, MemberRole role, MemberStatus status);

    long countByOrganizationIdAndStatus(Long organizationId, MemberStatus status);

    /** 보유 조직 개수 제한(최대 10개, organization-creation-approval-review.md 7.3절)에 쓴다. */
    long countByUserIdAndRoleAndStatus(Long userId, MemberRole role, MemberStatus status);

    /** 1인 1조직 제한(2026-08-16 결정)에 쓴다 — 역할과 무관하게 이미 다른 조직에 ACTIVE로 속해 있는지 검사. */
    boolean existsByUserIdAndStatus(Long userId, MemberStatus status);
}
