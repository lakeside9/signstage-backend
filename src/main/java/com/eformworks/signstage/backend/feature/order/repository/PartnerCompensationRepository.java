package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerCompensation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerCompensationRepository extends JpaRepository<PartnerCompensation, Long> {

    /** 정산에 더할 보상 — 정산월 말일까지 생긴 대기 보상, 오래된 순. */
    @Query("""
            select c from PartnerCompensation c
            join fetch c.organization
            join fetch c.partnerOrder p
            join fetch p.salesOrder
            where c.status = com.eformworks.signstage.backend.feature.order.entity.PartnerCompensationStatus.PENDING
              and c.occurredOn <= :until
            order by c.occurredOn, c.id
            """)
    List<PartnerCompensation> findPendingUntil(@Param("until") LocalDate until);

    @Query("""
            select c from PartnerCompensation c
            join fetch c.partnerOrder p
            join fetch p.salesOrder
            where c.partnerPayout.id = :partnerPayoutId
            order by c.occurredOn, c.id
            """)
    List<PartnerCompensation> findAllByPartnerPayoutId(@Param("partnerPayoutId") Long partnerPayoutId);

    List<PartnerCompensation> findAllByPartnerOrderSalesOrderIdOrderByIdAsc(Long salesOrderId);

    /** 발주의 고객 취소 보상 — 고객 취소 때 발주당 하나만 생긴다. */
    Optional<PartnerCompensation> findByPartnerOrderId(Long partnerOrderId);
}
