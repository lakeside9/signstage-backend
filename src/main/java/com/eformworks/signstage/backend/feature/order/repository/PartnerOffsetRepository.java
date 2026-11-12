package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOffset;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerOffsetRepository extends JpaRepository<PartnerOffset, Long> {

    @Query(
            value = """
                    select o from PartnerOffset o
                    join fetch o.partnerOrder p
                    join fetch p.salesOrder
                    join fetch o.organization
                    where (:status is null or o.status = :status)
                      and (:organizationId is null or o.organization.id = :organizationId)
                    """,
            countQuery = """
                    select count(o) from PartnerOffset o
                    where (:status is null or o.status = :status)
                      and (:organizationId is null or o.organization.id = :organizationId)
                    """
    )
    Page<PartnerOffset> search(
            @Param("status") PartnerOffsetStatus status,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    /** 정산에서 뺄 상계 — 정산월 말일까지 생긴 대기 상계, 오래된 순. */
    @Query("""
            select o from PartnerOffset o
            where o.organization.id = :organizationId
              and o.currencyCode = :currencyCode
              and o.status = com.eformworks.signstage.backend.feature.order.entity.PartnerOffsetStatus.PENDING
              and o.occurredOn <= :until
            order by o.occurredOn, o.id
            """)
    List<PartnerOffset> findPendingForSettlement(
            @Param("organizationId") Long organizationId,
            @Param("currencyCode") String currencyCode,
            @Param("until") LocalDate until
    );

    @Query("""
            select o from PartnerOffset o
            join fetch o.partnerOrder p
            join fetch p.salesOrder
            join fetch o.organization
            where o.partnerPayout.id = :partnerPayoutId
            order by o.occurredOn, o.id
            """)
    List<PartnerOffset> findAllByPartnerPayoutId(@Param("partnerPayoutId") Long partnerPayoutId);

    List<PartnerOffset> findAllByCarriedOverFromPayoutId(Long carriedOverFromPayoutId);
}
