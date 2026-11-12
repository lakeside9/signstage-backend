package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.feature.order.entity.ScopeChangeRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScopeChangeRequestRepository extends JpaRepository<ScopeChangeRequest, Long> {

    List<ScopeChangeRequest> findAllByPartnerOrderSalesOrderIdOrderByIdDesc(Long salesOrderId);

    List<ScopeChangeRequest> findAllByPartnerOrderSalesOrderIdAndClosedAtIsNull(Long salesOrderId);

    List<ScopeChangeRequest> findAllByPartnerOrderIdOrderByIdDesc(Long partnerOrderId);

    Optional<ScopeChangeRequest> findByIdAndPartnerOrderSalesOrderId(Long id, Long salesOrderId);
}
