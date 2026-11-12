package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

    List<PartnerOrder> findAllBySalesOrderIdOrderByIssuedAtDesc(Long salesOrderId);

    @Query(
            value = """
                    select p from PartnerOrder p
                    join fetch p.salesOrder
                    join fetch p.organization
                    where (:status is null or p.status = :status)
                      and (:organizationId is null or p.organization.id = :organizationId)
                    """,
            countQuery = """
                    select count(p) from PartnerOrder p
                    where (:status is null or p.status = :status)
                      and (:organizationId is null or p.organization.id = :organizationId)
                    """
    )
    Page<PartnerOrder> search(
            @Param("status") PartnerOrderStatus status,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    Optional<PartnerOrder> findByIdAndOrganizationId(Long id, Long organizationId);

    /** 검수를 만들 발주 — 수락했고 행사가 완료됐지만 아직 검수가 없는 건. */
    @Query("""
            select p from PartnerOrder p
            join fetch p.ceremony c
            where p.status = com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus.ACCEPTED
              and c.completedAt is not null
              and not exists (select 1 from OrderInspection i where i.partnerOrder = p)
            """)
    List<PartnerOrder> findAcceptedCompletedWithoutInspection();

    /** 수주의 수락된 발주 행사가 완료됐는지 — 잔금 청구 가능 여부(D12). */
    @Query("""
            select count(p) > 0 from PartnerOrder p
            join p.ceremony c
            where p.salesOrder.id = :salesOrderId
              and p.status = com.eformworks.signstage.backend.feature.order.entity.PartnerOrderStatus.ACCEPTED
              and c.completedAt is not null
            """)
    boolean existsCompletedBySalesOrderId(@Param("salesOrderId") Long salesOrderId);
}
