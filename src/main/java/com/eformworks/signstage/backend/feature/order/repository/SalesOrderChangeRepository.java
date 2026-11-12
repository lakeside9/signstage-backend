package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChange;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrderChangeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderChangeRepository extends JpaRepository<SalesOrderChange, Long> {

    List<SalesOrderChange> findAllBySalesOrderIdOrderByIdDesc(Long salesOrderId);

    boolean existsBySalesOrderIdAndStatus(Long salesOrderId, SalesOrderChangeStatus status);

    List<SalesOrderChange> findAllByPartnerOrderIdOrderByIdDesc(Long partnerOrderId);

    List<SalesOrderChange> findAllByPartnerOrderIdAndStatus(Long partnerOrderId, SalesOrderChangeStatus status);

    Optional<SalesOrderChange> findByIdAndSalesOrderId(Long id, Long salesOrderId);

    Optional<SalesOrderChange> findByIdAndPartnerOrderId(Long id, Long partnerOrderId);

    boolean existsByRegionSurchargeId(Long regionSurchargeId);
}
