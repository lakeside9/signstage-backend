package com.eformworks.signstage.backend.feature.identity.repository;

import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.identity.entity.User;
import com.eformworks.signstage.backend.feature.identity.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link UserRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을 담는다.
 * 구현은 {@link UserRepositoryImpl}(QueryDSL)이 맡는다.
 */
public interface UserRepositoryCustom {

    /**
     * 플랫폼 관리자 회원 검색용. 각 조건은 값이 없으면(null/빈 문자열) 무시된다.
     * loginId/name/email은 부분 일치(대소문자 무시), status는 정확히 일치한다.
     */
    Page<User> search(String loginId, String name, String email, UserStatus status, Pageable pageable);

    /**
     * 플랫폼 관리자 계정(platform_role이 있는 User) 검색용. {@link #search}와 같은 규칙으로
     * loginId/name/email은 부분 일치, platformRole은 정확히 일치(생략하면 등급 무관 전체).
     */
    Page<User> searchAccounts(String loginId, String name, String email, PlatformRole platformRole, Pageable pageable);
}
