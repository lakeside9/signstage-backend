package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
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
 * {@link BillingPlan}의 판매가격/사용여부 기간별 버전 — signstage-docs
 * business/billing-catalog-price-validity-period-review.md 결정(2026-09-09, 다중버전 채택).
 * {@code BillingPlan}은 이제 정체성(id/name)만 갖고, 가격정보({@link CatalogPriceInfo})와
 * 사용여부(active)는 전부 이 엔티티로 옮겨졌다 — "10월엔 5만원, 11월부턴 6만원"처럼 미리
 * 예약된 가격 변경을 배치/스케줄러 없이 조회 시점에 지연 계산({@code findEffective})으로
 * 반영하기 위해서다({@code TaxPolicy}/{@code OrganizationBillingPlanDiscount}와 같은 방식).
 *
 * <p>행 하나 = 기간 하나(다중 버전). 같은 플랜에 여러 행을 둘 수 있고, 각 행이
 * {@code effectiveFrom}~{@code effectiveTo} 기간 동안만 유효하다. 기간이 겹치지 않게 막는 것은
 * DB 제약이 아니라 {@code BillingPlanService}의 서비스 레이어 검증이다(MySQL은 범위 제약을
 * 지원하지 않는다). 모든 플랜은 항상 최소 1개의 기간을 가져야 한다(생성 시 최초 기간을 함께
 * 만든다) — 기간이 하나도 없는 플랜은 판매 가능 여부를 판단할 수 없기 때문이다.
 */
@Entity
@Table(
        name = "billing_plan_price_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bppp_plan_period",
                columnNames = {"billing_plan_id", "effective_from"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanPricePeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Embedded
    private CatalogPriceInfo priceInfo;

    @Column(nullable = false)
    private boolean active;

    /** 이 기간의 시작일(포함). 생략 없이 항상 값을 가진다 — {@code TaxPolicy.effectiveFrom}과 같은 이유. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 이 기간의 종료일(포함). null이면 그 뒤로 다른 기간이 없는 한 무기한. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder
    private BillingPlanPricePeriod(
            BillingPlan billingPlan,
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.billingPlan = billingPlan;
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? "KR_VAT_STANDARD" : taxCode
        );
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** 플랫폼 관리자가 이미 있는 기간 하나(가격/사용여부/기간 자체)를 고칠 때 쓴다. */
    public void update(
            String currencyCode,
            BigDecimal supplyPrice,
            BigDecimal salePrice,
            DiscountType discountType,
            BigDecimal discountValue,
            String taxCode,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.priceInfo = CatalogPriceInfo.of(
                currencyCode, supplyPrice, salePrice, discountType, discountValue,
                taxCode == null || taxCode.isBlank() ? this.priceInfo.getTaxCode() : taxCode
        );
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
