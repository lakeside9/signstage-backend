package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CustomerQuote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerQuoteRepository extends JpaRepository<CustomerQuote, Long> {

    List<CustomerQuote> findAllByCeremonyIdOrderByVersionDesc(Long ceremonyId);

    Optional<CustomerQuote> findByIdAndCeremonyId(Long id, Long ceremonyId);

    /** 다음 버전 번호 계산에 쓴다 — 이 Ceremony에 아직 고객 견적서가 없으면 0을 돌려준다(다음 버전 = 1). */
    @Query("select coalesce(max(q.version), 0) from CustomerQuote q where q.ceremony.id = :ceremonyId")
    int findMaxVersion(@Param("ceremonyId") Long ceremonyId);
}
