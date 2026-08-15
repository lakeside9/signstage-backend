package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동적 검색 조건(search)은 {@link OrganizationRepositoryCustom}(QueryDSL 구현은
 * {@link OrganizationRepositoryImpl})에 있다.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long>, OrganizationRepositoryCustom {

    boolean existsByCode(String code);
}
