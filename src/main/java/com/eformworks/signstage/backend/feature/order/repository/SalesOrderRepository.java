package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Query(
            value = """
                    select s from SalesOrder s
                    join fetch s.customer
                    where (:status is null or s.status = :status)
                      and (:customerId is null or s.customer.id = :customerId)
                    """,
            countQuery = """
                    select count(s) from SalesOrder s
                    where (:status is null or s.status = :status)
                      and (:customerId is null or s.customer.id = :customerId)
                    """
    )
    Page<SalesOrder> search(
            @Param("status") SalesOrderStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable
    );

    Optional<SalesOrder> findByQuoteAccessToken(String quoteAccessToken);

    boolean existsByRegionSurchargeId(Long regionSurchargeId);

    boolean existsByQuoteTemplateId(Long quoteTemplateId);

    boolean existsByBillingPlanId(Long billingPlanId);
}
