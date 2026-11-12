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
}
