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

    /**
     * 행사 삭제 가능 여부 판정에 쓴다({@code CeremonyService#deleteCeremony}) — 대기중이거나
     * 승인된 요청이 있으면 삭제를 막는다. 반려(REJECTED)는 막지 않는다 — 이미 종결된 이력일
     * 뿐이라 재요청 허용 판정({@code existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn})
     * 에서도 같은 이유로 제외한다.
     */
    boolean existsByCeremonyIdAndStatusIn(Long ceremonyId, List<PurchaseStatus> statuses);

    /** 플랜이 확정되지 않은(DRAFT) 행사 삭제 시 이 행사의 추가구매 요청(헤더, 반려 이력 포함)을 함께 지운다. */
    void deleteAllByCeremonyId(Long ceremonyId);
}
