package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객 가격표 — signstage-docs business/platform-order-partner-subcontract-review.md 6장, D06.
 * 플랜(패키지) 가격 또는 단위 상품(추가 옵션) 가격 중 하나를 갖는다. 금액은 공급가(세금 별도)다.
 * 지금 카탈로그의 파트너 판매가({@code UnitProductPricePeriod})와 따로 관리한다.
 */
@Entity
@Table(name = "customer_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPrice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id")
    private BillingPlan billingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id")
    private UnitProduct unitProduct;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Embedded
    private EffectivePeriod period;

    @Builder
    private CustomerPrice(
            BillingPlan billingPlan, UnitProduct unitProduct, String currencyCode, BigDecimal unitPrice,
            String taxCode, EffectivePeriod period
    ) {
        this.billingPlan = billingPlan;
        this.unitProduct = unitProduct;
        this.currencyCode = currencyCode;
        this.unitPrice = unitPrice;
        this.taxCode = taxCode;
        this.period = period;
    }

    public void close(LocalDate effectiveTo) {
        this.period = period.close(effectiveTo);
    }
}
