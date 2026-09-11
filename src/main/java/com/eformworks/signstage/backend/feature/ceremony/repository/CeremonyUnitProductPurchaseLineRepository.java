package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyUnitProductPurchaseLineRepository extends JpaRepository<CeremonyUnitProductPurchaseLine, Long> {

    /** 한 구매 요청(헤더)에 속한 줄 전체 — 화면 표시/승인 상세용. */
    List<CeremonyUnitProductPurchaseLine> findAllByPurchaseIdOrderByIdAsc(Long purchaseId);

    /** 요청자 본인의 이력 조회 화면용 — 여러 구매 요청의 줄을 한 번에 모은다. */
    List<CeremonyUnitProductPurchaseLine> findAllByPurchase_CeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /** 유효 한도 계산용 — APPROVED만 넘겨서 쓴다({@code CeremonyService#calculateEffectiveCapacity}). */
    List<CeremonyUnitProductPurchaseLine> findAllByPurchase_CeremonyIdAndUnitProduct_TypeAndPurchase_Status(
            Long ceremonyId,
            UnitProductType type,
            PurchaseStatus status
    );

    /**
     * "이미 구매(요청)했는지" 판정용 — 토글형(예: EVENT_EFFECT_BUNDLE) 단위 상품 재구매 방지에 쓴다.
     * REJECTED는 재요청을 허용해야 하므로 PENDING/APPROVED만 걸러서 넘긴다.
     */
    boolean existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(
            Long ceremonyId,
            Long unitProductId,
            List<PurchaseStatus> statuses
    );

    /** 카탈로그 관리 화면의 "사용 중" 경고용 — 이 단위 상품을 승인받아 쓰는 구매 줄 수. */
    long countByUnitProduct_IdAndPurchase_Status(Long unitProductId, PurchaseStatus status);

    /** 단위 상품 삭제 가능 여부(사용 이력 없음) 판정에 쓴다 — 상태와 무관하게 구매된 적이 있는지. */
    boolean existsByUnitProduct_Id(Long unitProductId);

    /**
     * 플랜이 확정되지 않은(DRAFT) 행사 삭제 시 이 행사의 추가구매 요청 줄을 함께 지운다 —
     * {@code CeremonyUnitProductPurchaseRepository.deleteAllByCeremonyId}보다 먼저 호출해야
     * 한다(FK 순서).
     */
    void deleteAllByPurchase_CeremonyId(Long ceremonyId);
}
