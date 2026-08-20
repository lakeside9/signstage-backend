package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyCapacityPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyCapacityPurchaseRepository extends JpaRepository<CeremonyCapacityPurchase, Long> {

    /** 요청자 본인의 이력 조회용(전체 상태 포함, 최신순). */
    List<CeremonyCapacityPurchase> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /** 유효 한도 계산용 — APPROVED만 넘겨서 쓴다({@code CeremonyService#calculateEffectiveCapacity}). */
    List<CeremonyCapacityPurchase> findAllByCeremonyIdAndCapacityAddOn_CapacityTypeAndStatus(
            Long ceremonyId,
            CapacityType capacityType,
            PurchaseStatus status
    );

    /** 플랫폼 관리자 승인 대기열용. */
    Page<CeremonyCapacityPurchase> findAllByStatus(PurchaseStatus status, Pageable pageable);

    /** 카탈로그 관리 화면의 "사용 중" 경고용 — 이 상품을 승인받아 쓰는 구매 건수(signstage-docs 9장). */
    long countByCapacityAddOnIdAndStatus(Long capacityAddOnId, PurchaseStatus status);
}
