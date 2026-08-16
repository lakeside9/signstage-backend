package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.identity.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link MemberRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을 담는다.
 * 구현은 {@link MemberRepositoryImpl}(QueryDSL)이 맡는다.
 */
public interface MemberRepositoryCustom {

    /**
     * 어느 조직에도 ACTIVE로 속하지 않은 ACTIVE 상태 사용자를 검색한다. 플랫폼 관리자 콘솔의
     * "멤버 강제 추가" 화면에서 후보를 고를 때 쓴다 — 1인 1조직 제한(2026-08-16 결정)에 따라
     * 이미 조직이 있는 사용자는 애초에 후보가 될 수 없다. loginId/name/email은 각각 부분
     * 일치(대소문자 무시)이고, 값이 없으면(null/빈 문자열) 무시된다.
     */
    Page<User> searchUsersWithoutOrganization(String loginId, String name, String email, Pageable pageable);
}
