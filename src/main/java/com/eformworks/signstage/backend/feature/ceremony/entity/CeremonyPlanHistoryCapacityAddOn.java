package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link CeremonyPlanHistory} 스냅샷 시점에 그 플랜에서 구매 가능했던 용량 추가구매 상품 매핑
 * (다대다 조인). append-only다 — {@link CeremonyPlanHistory}와 같은 생명주기로, Ceremony 생성
 * 시(최초 플랜 선택)와 {@code CeremonyService#changePlan}에서 매 변경마다 그 순간의
 * {@link BillingPlanCapacityAddOn} 구성을 그대로 복사해 저장한다.
 *
 * <p>{@link CeremonyPlanHistoryOptionalFeature}와 같은 원칙이다 — 카탈로그 관리자가 나중에
 * {@code BillingPlan}의 구매 가능 상품 구성을 바꿔도(안 A 큐레이션 목록에서 상품을 빼도) 이미
 * 확정/진행 중인 행사는 이 스냅샷을 기준으로 삼아 영향받지 않는다 — signstage-docs
 * business/optional-feature-display-scope-and-plan-capacity-addon-review.md 5.5절 참고.
 * {@code CeremonyService#retrieveAvailableCapacityAddOnIds}가 라이브
 * {@code BillingPlanCapacityAddOn} 조회 대신 이걸 우선 쓴다(이력이 없는 레거시 행사만 라이브로
 * 폴백).
 */
@Entity
@Table(name = "ceremony_plan_history_capacity_addons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class CeremonyPlanHistoryCapacityAddOn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_plan_history_id", nullable = false)
    private CeremonyPlanHistory ceremonyPlanHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_addon_id", nullable = false)
    private CapacityAddOn capacityAddOn;

    @Builder
    private CeremonyPlanHistoryCapacityAddOn(CeremonyPlanHistory ceremonyPlanHistory, CapacityAddOn capacityAddOn) {
        this.ceremonyPlanHistory = ceremonyPlanHistory;
        this.capacityAddOn = capacityAddOn;
    }
}
