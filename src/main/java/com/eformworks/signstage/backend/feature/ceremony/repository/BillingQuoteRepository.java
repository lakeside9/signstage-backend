package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.BillingQuote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingQuoteRepository extends JpaRepository<BillingQuote, Long> {

    List<BillingQuote> findAllByCeremonyIdOrderByVersionDesc(Long ceremonyId);

    Optional<BillingQuote> findByIdAndCeremonyId(Long id, Long ceremonyId);

    /** 다음 버전 번호 계산에 쓴다 — 이 Ceremony에 아직 견적이 없으면 0을 돌려준다(다음 버전 = 1). */
    @Query("select coalesce(max(q.version), 0) from BillingQuote q where q.ceremony.id = :ceremonyId")
    int findMaxVersion(@Param("ceremonyId") Long ceremonyId);
}
