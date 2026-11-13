package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.CustomerRefund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, Long> {

    List<CustomerRefund> findAllBySalesOrderIdOrderByIdAsc(Long salesOrderId);
}
