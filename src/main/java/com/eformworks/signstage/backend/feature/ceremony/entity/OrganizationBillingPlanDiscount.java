package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직×과금 플랜 세밀 할인 오버라이드. signstage-docs
 * business/organization-event-discount-pricing-review.md 4.1절(2026-08-21 재검토: 조직×품목
 * 세밀 오버라이드 안 채택) 참고. 이 조직이 이 플랜을 쓸 때 카탈로그의 {@code discountType}/
 * {@code discountValue} 대신 여기 값을 쓴다. 이 조직×이 플랜 조합에 "오늘" 유효한 오버라이드
 * 행이 없으면 카탈로그 값을 그대로 쓴다({@code OrganizationDiscountService#resolveBillingPlanDiscount}).
 *
 * <p>이 값은 {@code Ceremony} 생성(플랜 최초 선택)/변경 시점에 {@link CeremonyPlanHistory}로
 * 스냅샷되므로, 나중에 여기 값을 바꿔도 이미 만들어진 Ceremony의 계산 결과에는 영향을 주지
 * 않는다(같은 문서 4.1절 결정 — "라이브 참조" 대신 "생성 시점 스냅샷 고정").
 *
 * <p>행 하나 = 기간 하나(다중 버전, {@code TaxPolicy}와 같은 방식) — signstage-docs
 * business/organization-discount-override-security-and-validity-period-review.md 결정
 * #4(2026-09-08, 안 B 채택). 같은 (organization, billingPlan)에 여러 행을 둘 수 있고, 각 행이
 * {@code effectiveFrom}~{@code effectiveTo} 기간 동안만 유효하다 — "8월엔 10%, 9월엔 20%"처럼
 * 기간별로 다른 할인값을 미리 예약해둘 수 있다. 기간이 겹치지 않게 막는 것은 DB 제약이 아니라
 * {@code OrganizationDiscountService}의 서비스 레이어 검증이다(MySQL은 범위 제약을 지원하지
 * 않는다 — 같은 문서 3.3절).
 */
@Entity
@Table(
        name = "organization_billing_plan_discounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_obpd_organization_plan_period",
                columnNames = {"organization_id", "billing_plan_id", "effective_from"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationBillingPlanDiscount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    /**
     * signstage-docs business/billing-catalog-zero-base-schema-redesign-review.md 결정
     * #4(2026-09-08) — {@link DiscountInfo}의 기본 정밀도(19, 4)보다 좁은 정밀도(12, 2)를
     * 그대로 유지한다(순수 리팩터링, 기존 스키마 값 보존).
     */
    @Embedded
    @AttributeOverride(name = "discountValue", column = @Column(name = "discount_value", nullable = false, precision = 12, scale = 2))
    private DiscountInfo discount;

    /** 이 기간의 시작일(포함). 생략 없이 항상 값을 가진다 — {@code TaxPolicy.effectiveFrom}과 같은 이유. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 이 기간의 종료일(포함). null이면 그 뒤로 다른 기간이 없는 한 무기한. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder
    private OrganizationBillingPlanDiscount(
            Organization organization,
            BillingPlan billingPlan,
            DiscountType discountType,
            BigDecimal discountValue,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.discount = new DiscountInfo(discountType, discountValue);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** 플랫폼 관리자가 이미 있는 기간 하나(할인값 또는 기간 자체)를 고칠 때 쓴다. */
    public void update(DiscountType discountType, BigDecimal discountValue, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.discount = new DiscountInfo(discountType, discountValue);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
