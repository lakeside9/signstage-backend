package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import com.eformworks.signstage.backend.feature.organization.entity.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByCode(String code);

    /**
     * 플랫폼 관리자 조직 검색용. 각 조건은 값이 없으면(null) 무시된다.
     * name/code는 부분 일치(대소문자 무시), status는 정확히 일치한다.
     */
    @Query("""
            SELECT o FROM Organization o
            WHERE (:name IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:code IS NULL OR LOWER(o.code) LIKE LOWER(CONCAT('%', :code, '%')))
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<Organization> search(
            @Param("name") String name,
            @Param("code") String code,
            @Param("status") OrganizationStatus status,
            Pageable pageable
    );
}
