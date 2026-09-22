package com.eformworks.signstage.backend.feature.organization.repository;

import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 동적 검색 조건(search)은 {@link OrganizationRepositoryCustom}(QueryDSL 구현은
 * {@link OrganizationRepositoryImpl})에 있다.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long>, OrganizationRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findByIdForMarginUpdate(Long id);

    boolean existsByCode(String code);

    List<Organization> findAllByDemoTrueOrderByCreatedAtDesc();
}
