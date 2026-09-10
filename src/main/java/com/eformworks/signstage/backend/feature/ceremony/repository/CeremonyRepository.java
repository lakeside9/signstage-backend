package com.eformworks.signstage.backend.feature.ceremony.repository;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CeremonyRepository extends JpaRepository<Ceremony, Long>, CeremonyRepositoryCustom {

    List<Ceremony> findAllByOrganizationId(Long organizationId);

    /** 카탈로그 관리 화면의 "사용 중" 경고용 — 이 플랜을 쓰는 행사 수(signstage-docs 9장). */
    long countByBillingPlanId(Long billingPlanId);

    /**
     * 구독 잔여 횟수 계산에 쓴다 — 별도 카운터 컬럼을 두지 않고 그때그때 센다(signstage-docs
     * business/organization-event-discount-pricing-review.md 8.2-9번, 2026-09-10).
     */
    long countBySubscriptionId(Long subscriptionId);
}
