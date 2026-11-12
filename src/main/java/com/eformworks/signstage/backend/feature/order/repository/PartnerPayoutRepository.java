package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerPayout;
import com.eformworks.signstage.backend.feature.order.entity.PartnerPayoutStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPayoutRepository extends JpaRepository<PartnerPayout, Long> {

    @Query(
            value = """
                    select p from PartnerPayout p
                    join fetch p.organization
                    where (:settlementMonth is null or p.settlementMonth = :settlementMonth)
                      and (:status is null or p.status = :status)
                      and (:organizationId is null or p.organization.id = :organizationId)
                    """,
            countQuery = """
                    select count(p) from PartnerPayout p
                    where (:settlementMonth is null or p.settlementMonth = :settlementMonth)
                      and (:status is null or p.status = :status)
                      and (:organizationId is null or p.organization.id = :organizationId)
                    """
    )
    Page<PartnerPayout> search(
            @Param("settlementMonth") String settlementMonth,
            @Param("status") PartnerPayoutStatus status,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    Optional<PartnerPayout> findByIdAndOrganizationId(Long id, Long organizationId);
}
