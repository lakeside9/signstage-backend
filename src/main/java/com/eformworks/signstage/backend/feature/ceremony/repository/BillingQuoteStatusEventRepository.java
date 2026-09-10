package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuoteStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingQuoteStatusEventRepository extends JpaRepository<BillingQuoteStatusEvent, Long> {

    /** 최신 이벤트가 앞에 오도록 정렬 — 목록 첫 행이 "현재 상태"다. */
    List<BillingQuoteStatusEvent> findAllByBillingQuoteIdOrderByOccurredAtDescIdDesc(Long billingQuoteId);

    /** 견적 목록 화면이 N+1 없이 한 번에 각 견적의 최신 상태를 구하는 데 쓴다. */
    List<BillingQuoteStatusEvent> findAllByBillingQuoteIdInOrderByOccurredAtDescIdDesc(List<Long> billingQuoteIds);
}
