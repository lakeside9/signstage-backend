package com.eformworks.signstage.backend.feature.order.repository;

import com.eformworks.signstage.backend.core.jpa.AppendOnlyRepository;
import com.eformworks.signstage.backend.feature.order.entity.PartnerLedgerEntry;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerLedgerEntryRepository extends AppendOnlyRepository<PartnerLedgerEntry, Long> {

    Page<PartnerLedgerEntry> findAllByOrganizationIdOrderByOccurredOnDescIdDesc(Long organizationId, Pageable pageable);

    /** 미지급 잔액(공급가) — 지급 의무 합계 − 지급 합계. */
    @Query("select coalesce(sum(e.amount), 0) from PartnerLedgerEntry e where e.organization.id = :organizationId")
    BigDecimal sumAmountByOrganizationId(@Param("organizationId") Long organizationId);
}
