package com.eformworks.signstage.backend.feature.ceremony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 파트너 → 실고객 고객 견적서 헤더 — signstage-docs
 * business/partner-customer-quote-design-review.md 결정(2026-09-11). 옛 {@code BillingQuote}
 * (2026-09-11 자가-체크아웃 도입으로 완전 삭제, business/unit-product-purchase-self-checkout-review.md
 * 6장 결정)와 같은 스냅샷/append-only 패턴(버전 번호, 재견적은 새 버전 추가)을 쓰지만 별개
 * 엔티티였다 — {@code BillingQuote}는 플랫폼이 확정하는 불변 기록(파트너→플랫폼)이었고, 이건
 * 파트너가 영업 단계에서 마진을 바꿔가며 여러 번 다시 뽑아보는 도구(파트너→실고객)라
 * 라이프사이클과 소유권이 다르다. 무효화(VOID) 개념은 두지 않는다 — 오래된 버전은 그냥 안
 * 쓰면 된다.
 *
 * <p>{@code systemUsageCostAmount}는 이 견적을 만든 시점에 {@code CeremonyService
 * #buildQuoteCalculation}의 라인 중 {@link UnitProductCategory#isSystemUsageFee()}가 참인
 * 라인들의 {@code netAmount} 합계(=파트너가 플랫폼에 내는 시스템 사용료 원가 기준선)를 그대로
 * 스냅샷한 것이다. {@code margin}도 적용 당시 유효했던 값(조직 기본값 또는 행사별 override)의
 * 스냅샷이라, 이후 그 설정이 바뀌어도 이미 만든 견적은 바뀌지 않는다.
 */
@Entity
@Table(name = "customer_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CustomerQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    /** 이 Ceremony 안에서의 버전 번호 — 1부터 시작, 재견적할 때마다 1씩 증가. */
    @Column(nullable = false)
    private Integer version;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_fraction_digits", nullable = false)
    private Short currencyFractionDigits;

    @Column(name = "currency_rounding_mode", nullable = false, length = 20)
    private String currencyRoundingMode;

    /** 시스템 사용료(ESSENTIAL+APPLICATION) 원가 소계 — 파트너가 플랫폼에 내는 금액 스냅샷. */
    @Column(name = "system_usage_cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal systemUsageCostAmount;

    /** 적용 당시 유효했던 마진(조직 기본값 또는 행사별 override)의 스냅샷. */
    @Embedded
    private MarginInfo margin;

    @Column(name = "system_usage_margin_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal systemUsageMarginAmount;

    /** = systemUsageCostAmount + systemUsageMarginAmount. */
    @Column(name = "system_usage_customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal systemUsageCustomerAmount;

    /** 장비·인력(EQUIPMENT/PERSONNEL) 라인 합계 — 파트너가 직접 입력한 고객 단가 기준. */
    @Column(name = "equipment_personnel_customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal equipmentPersonnelCustomerAmount;

    /** = systemUsageCustomerAmount + equipmentPersonnelCustomerAmount. 세전 금액이다(5장 결정 — 세액은 파트너 몫). */
    @Column(name = "total_customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCustomerAmount;

    @Column(name = "pricing_calculated_at", nullable = false)
    private LocalDateTime pricingCalculatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CustomerQuote(
            Ceremony ceremony,
            Integer version,
            String currencyCode,
            Short currencyFractionDigits,
            String currencyRoundingMode,
            BigDecimal systemUsageCostAmount,
            MarginInfo margin,
            BigDecimal systemUsageMarginAmount,
            BigDecimal systemUsageCustomerAmount,
            BigDecimal equipmentPersonnelCustomerAmount,
            BigDecimal totalCustomerAmount,
            LocalDateTime pricingCalculatedAt
    ) {
        this.ceremony = ceremony;
        this.version = version;
        this.currencyCode = currencyCode;
        this.currencyFractionDigits = currencyFractionDigits;
        this.currencyRoundingMode = currencyRoundingMode;
        this.systemUsageCostAmount = systemUsageCostAmount;
        this.margin = margin;
        this.systemUsageMarginAmount = systemUsageMarginAmount;
        this.systemUsageCustomerAmount = systemUsageCustomerAmount;
        this.equipmentPersonnelCustomerAmount = equipmentPersonnelCustomerAmount;
        this.totalCustomerAmount = totalCustomerAmount;
        this.pricingCalculatedAt = pricingCalculatedAt;
    }
}
