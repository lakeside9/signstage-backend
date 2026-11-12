package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동적 검색 조건(search/searchAccounts)은 {@link UserRepositoryCustom}(QueryDSL 구현은
 * {@link UserRepositoryImpl})에 있다.
 */
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** 플랫폼 담당자 알림 대상 — 플랫폼 역할이 있는 활성 사용자. */
    List<User> findAllByPlatformRoleIsNotNullAndStatus(UserStatus status);
}
