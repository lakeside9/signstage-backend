package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.feature.billing.port.OrderCatalogUsagePort;
import com.eformworks.signstage.backend.feature.order.repository.CustomerPriceRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderChangeLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderLineRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderCatalogUsagePort} 구현 — 플랫폼 수주가 참조하는 플랜·단위 상품은 카탈로그에서 지울 수 없다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCatalogUsageService implements OrderCatalogUsagePort {

    private final CustomerPriceRepository customerPriceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final SalesOrderChangeLineRepository salesOrderChangeLineRepository;

    @Override
    public boolean isBillingPlanUsed(Long billingPlanId) {
        return customerPriceRepository.existsByBillingPlanId(billingPlanId)
                || salesOrderRepository.existsByBillingPlanId(billingPlanId);
    }

    @Override
    public boolean isUnitProductUsed(Long unitProductId) {
        return customerPriceRepository.existsByUnitProductId(unitProductId)
                || salesOrderLineRepository.existsByUnitProductId(unitProductId)
                || salesOrderChangeLineRepository.existsByUnitProductId(unitProductId);
    }
}
