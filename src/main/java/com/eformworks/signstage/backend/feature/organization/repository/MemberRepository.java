package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.repository.entity.Member;
import com.eformworks.signstage.backend.feature.organization.repository.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.repository.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, MemberStatus status);

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    List<Member> findAllByOrganizationIdAndStatusNot(Long organizationId, MemberStatus status);

    List<Member> findAllByUserIdAndStatus(Long userId, MemberStatus status);

    long countByOrganizationIdAndRoleAndStatus(Long organizationId, MemberRole role, MemberStatus status);

    long countByOrganizationIdAndStatus(Long organizationId, MemberStatus status);
}
