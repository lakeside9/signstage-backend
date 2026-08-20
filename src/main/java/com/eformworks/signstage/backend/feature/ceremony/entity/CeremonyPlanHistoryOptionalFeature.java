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

/**
 * {@link CeremonyPlanHistory} 스냅샷 시점에 그 플랜에 포함돼 있던 선택옵션 매핑(다대다 조인).
 * append-only다 — {@link CeremonyPlanHistory}와 같은 생명주기로, Ceremony 생성 시(최초 플랜
 * 선택)와 {@code CeremonyService#changePlan}에서 매 변경마다 그 순간의
 * {@link BillingPlanOptionalFeature} 구성을 그대로 복사해 저장한다.
 *
 * <p>카탈로그 관리자가 나중에 {@code BillingPlan}의 옵션 구성을 바꿔도(9장 후속 결정 —
 * 플랜의 선택옵션 구성이 생성 후에도 수정 가능해짐) 이미 확정/진행 중인 행사는 이 스냅샷을
 * 기준으로 삼아 영향받지 않는다 — signstage-docs business/ceremony-billing-options-review.md
 * 9장 참고. {@code CeremonyService#retrievePurchasedOptionalFeatureIds}가 라이브
 * {@code BillingPlanOptionalFeature} 조회 대신 이걸 쓴다(이력이 없는 레거시 행사만 라이브로
 * 폴백).
 */
@Entity
@Table(name = "ceremony_plan_history_optional_features")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyPlanHistoryOptionalFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_plan_history_id", nullable = false)
    private CeremonyPlanHistory ceremonyPlanHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Builder
    private CeremonyPlanHistoryOptionalFeature(CeremonyPlanHistory ceremonyPlanHistory, OptionalFeature optionalFeature) {
        this.ceremonyPlanHistory = ceremonyPlanHistory;
        this.optionalFeature = optionalFeature;
    }
}
