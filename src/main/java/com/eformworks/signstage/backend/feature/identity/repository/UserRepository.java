package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import com.eformworks.signstage.backend.feature.identity.repository.entity.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * 플랫폼 관리자 회원 검색용. 각 조건은 값이 없으면(null/빈 문자열) 무시된다.
     * loginId/name/email은 부분 일치(대소문자 무시), status는 정확히 일치한다.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:loginId IS NULL OR LOWER(u.loginId) LIKE LOWER(CONCAT('%', :loginId, '%')))
              AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(
            @Param("loginId") String loginId,
            @Param("name") String name,
            @Param("email") String email,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    /** 플랫폼 관리자 계정(platform_role이 있는 User) 목록 조회용. */
    Page<User> findAllByPlatformRoleIsNotNull(Pageable pageable);
}
