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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랜에서 "구매 가능한" 용량 추가구매 상품 매핑(다대다 조인, 안 A — 큐레이션). signstage-docs
 * business/optional-feature-display-scope-and-plan-capacity-addon-review.md 4.1/5장 참고.
 *
 * <p>{@link BillingPlanOptionalFeature}와 겉모습은 같지만 의미가 다르다 — 이 매핑에 있다고
 * 무료로 포함되는 게 아니라, "이 플랜을 쓰는 행사가 이 상품을 구매 후보로 고를 수 있다"는
 * 뜻이다(여전히 사용자가 직접 구매 요청 → 관리자 승인). 이 매핑에 없는 {@code CapacityAddOn}은
 * 그 플랜의 행사에서 구매할 수 없다({@code CeremonyService#purchaseCapacity}).
 */
@Entity
@Table(
        name = "billing_plan_capacity_addons",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bpca_plan_addon",
                columnNames = {"billing_plan_id", "capacity_addon_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanCapacityAddOn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capacity_addon_id", nullable = false)
    private CapacityAddOn capacityAddOn;

    @Builder
    private BillingPlanCapacityAddOn(BillingPlan billingPlan, CapacityAddOn capacityAddOn) {
        this.billingPlan = billingPlan;
        this.capacityAddOn = capacityAddOn;
    }
}
