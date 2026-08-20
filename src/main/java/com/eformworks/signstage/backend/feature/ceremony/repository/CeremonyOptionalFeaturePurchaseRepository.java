package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyOptionalFeaturePurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyOptionalFeaturePurchaseRepository extends JpaRepository<CeremonyOptionalFeaturePurchase, Long> {

    /** 요청자 본인의 이력 조회용(전체 상태 포함, 최신순). */
    List<CeremonyOptionalFeaturePurchase> findAllByCeremonyIdOrderByCreatedAtDesc(Long ceremonyId);

    /** "구매한 선택옵션" 집계용 — APPROVED만 넘겨서 쓴다({@code CeremonyService#retrievePurchasedOptionalFeatureIds}). */
    List<CeremonyOptionalFeaturePurchase> findAllByCeremonyIdAndStatus(Long ceremonyId, PurchaseStatus status);

    /**
     * "이미 구매(요청)했는지" 판정용. REJECTED는 재요청을 허용해야 하므로 PENDING/APPROVED만
     * 걸러서 넘긴다 — 유니크 제약이 (ceremony_id, optional_feature_id, status)라 REJECTED 행이
     * 있어도 새 PENDING 행을 만들 수 있다.
     */
    boolean existsByCeremonyIdAndOptionalFeatureIdAndStatusIn(
            Long ceremonyId,
            Long optionalFeatureId,
            List<PurchaseStatus> statuses
    );

    /** 플랫폼 관리자 승인 대기열용. */
    Page<CeremonyOptionalFeaturePurchase> findAllByStatus(PurchaseStatus status, Pageable pageable);

    /** 카탈로그 관리 화면의 "사용 중" 경고용 — 이 옵션을 승인받아 쓰는 구매 건수(signstage-docs 9장). */
    long countByOptionalFeatureIdAndStatus(Long optionalFeatureId, PurchaseStatus status);
}
