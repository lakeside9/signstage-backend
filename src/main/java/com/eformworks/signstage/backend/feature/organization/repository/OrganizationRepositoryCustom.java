package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * {@link OrganizationRepository}가 Spring Data 관례로 표현하기 어려운 동적 검색 조건을 담는다.
 * 구현은 {@link OrganizationRepositoryImpl}(QueryDSL)이 맡는다.
 */
public interface OrganizationRepositoryCustom {

    /**
     * 플랫폼 관리자 조직 검색용. 각 조건은 값이 없으면(null) 무시된다.
     * name/code는 부분 일치(대소문자 무시), status는 정확히 일치한다.
     */
    Page<Organization> search(String name, String code, OrganizationStatus status, Pageable pageable);
}
