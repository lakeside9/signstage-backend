package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {

    List<CustomerInvoice> findAllBySalesOrderIdOrderByIdAsc(Long salesOrderId);


    /** 입금 기한이 지났는데 아직 미수 알림을 보내지 않은 청구. */
    List<CustomerInvoice> findAllByStatusAndDueDateBeforeAndOverdueNotifiedAtIsNull(
            CustomerInvoiceStatus status, LocalDate today
    );

    /** {@code overdueBefore}를 주면 그 날짜 전에 기한이 지난 미입금 청구만 본다. */
    @Query(
            value = """
                    select i from CustomerInvoice i
                    join fetch i.salesOrder
                    join fetch i.customer
                    where (:status is null or i.status = :status)
                      and (:customerId is null or i.customer.id = :customerId)
                      and (:overdueBefore is null
                           or (i.status = com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus.ISSUED
                               and i.dueDate < :overdueBefore))
                    """,
            countQuery = """
                    select count(i) from CustomerInvoice i
                    where (:status is null or i.status = :status)
                      and (:customerId is null or i.customer.id = :customerId)
                      and (:overdueBefore is null
                           or (i.status = com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus.ISSUED
                               and i.dueDate < :overdueBefore))
                    """
    )
    Page<CustomerInvoice> search(
            @Param("status") CustomerInvoiceStatus status,
            @Param("customerId") Long customerId,
            @Param("overdueBefore") LocalDate overdueBefore,
            Pageable pageable
    );
}
