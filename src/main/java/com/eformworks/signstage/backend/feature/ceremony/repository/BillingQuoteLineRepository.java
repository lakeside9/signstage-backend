package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingQuoteLineRepository extends JpaRepository<BillingQuoteLine, Long> {

    List<BillingQuoteLine> findAllByBillingQuoteIdOrderByIdAsc(Long billingQuoteId);
}
