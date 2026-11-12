package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeLine;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderChangeLineRepository extends JpaRepository<SalesOrderChangeLine, Long> {

    List<SalesOrderChangeLine> findAllBySalesOrderChangeIdOrderByDisplayOrder(Long salesOrderChangeId);

    List<SalesOrderChangeLine> findAllBySalesOrderChangeIdInOrderByDisplayOrder(Collection<Long> salesOrderChangeIds);

    boolean existsByCustomerPriceId(Long customerPriceId);
}
