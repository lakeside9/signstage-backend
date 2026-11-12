package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrderLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    List<SalesOrderLine> findAllBySalesOrderIdOrderByDisplayOrder(Long salesOrderId);

    void deleteAllBySalesOrderId(Long salesOrderId);
}
