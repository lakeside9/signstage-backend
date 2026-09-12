package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuoteLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerQuoteLineRepository extends JpaRepository<CustomerQuoteLine, Long> {

    List<CustomerQuoteLine> findAllByCustomerQuoteIdOrderByIdAsc(Long customerQuoteId);

    /** 견적서 삭제 시 먼저 호출한다 — FK에 cascade가 없어 줄을 먼저 지워야 헤더를 지울 수 있다. */
    void deleteAllByCustomerQuoteId(Long customerQuoteId);
}
