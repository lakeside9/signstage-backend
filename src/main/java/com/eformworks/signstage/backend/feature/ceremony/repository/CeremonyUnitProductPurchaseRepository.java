package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyUnitProductPurchaseRepository extends JpaRepository<CeremonyUnitProductPurchase, Long> {

    /** 요청자 본인의 이력 조회용(전체 상태 포함, 최신순). */
    List<CeremonyUnitProductPurchase> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /** 플랫폼 관리자 승인 대기열용. */
    Page<CeremonyUnitProductPurchase> findAllByStatus(PurchaseStatus status, Pageable pageable);
}
