package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 조직×플랜 할인 오버라이드({@link OrganizationBillingPlanDiscount})의 변경 이력. append-only다
 * — 수정/삭제 메서드를 두지 않는다. {@code BillingPlanHistory}와 같은 패턴이지만, 오버라이드는
 * 제거(하드 삭제)가 실제로 일어난다는 점이 다르다 — 그래서 살아있는 오버라이드 행을 참조하는
 * 대신 {@code organization}/{@code billingPlan}(둘 다 삭제되지 않는 값) 조합으로 스코핑하고,
 * {@code removed}로 "이 시점에 오버라이드가 제거됐다"를 표현한다. 제거 이벤트도 그 직전 값을
 * {@code discountType}/{@code discountValue}에 그대로 남긴다 — "무엇이 제거됐는지"를 이력만
 * 보고 알 수 있어야 하기 때문이다.
 *
 * <p>설정(생성/수정)/제거 모두 {@code OrganizationDiscountService}가 호출 시점에 한 행씩 쌓는다.
 * "누가/언제"는 {@link BaseEntity#getCreatedBy()}/{@link BaseEntity#getCreatedAt()}로 충분해
 * 별도 컬럼을 두지 않는다.
 */
@Entity
@Table(name = "organization_billing_plan_discount_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OrganizationBillingPlanDiscountHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** true면 이 행이 "오버라이드 제거" 이벤트다 — discountType/discountValue는 제거 직전 값. */
    @Column(nullable = false)
    private boolean removed;

    @Builder
    private OrganizationBillingPlanDiscountHistory(
            Organization organization,
            BillingPlan billingPlan,
            DiscountType discountType,
            BigDecimal discountValue,
            boolean removed
    ) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.removed = removed;
    }
}
