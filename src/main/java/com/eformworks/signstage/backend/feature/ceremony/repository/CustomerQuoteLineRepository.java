package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuoteLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerQuoteLineRepository extends JpaRepository<CustomerQuoteLine, Long> {

    List<CustomerQuoteLine> findAllByCustomerQuoteIdOrderByIdAsc(Long customerQuoteId);
}
