package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권역별 정액 지역 할증 — D26 권장안. 한 행에 고객 가격(고객 견적에 더함)과 파트너 단가(발주
 * 금액에 더함)를 함께 둔다. 할증이 없는 권역도 0원 행을 두어 "할증 없음"을 명시한다.
 */
@Entity
@Table(name = "region_surcharges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionSurcharge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceRegion region;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customerAmount;

    @Column(name = "partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal partnerAmount;

    /** 숙박 1박·1인 금액(D26) — 관리자가 숙박을 넣은 견적·발주에 박수 × 인원만큼 더한다. */
    @Column(name = "lodging_customer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lodgingCustomerAmount;

    @Column(name = "lodging_partner_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lodgingPartnerAmount;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Embedded
    private EffectivePeriod period;

    @Builder
    private RegionSurcharge(
            ServiceRegion region, String currencyCode, BigDecimal customerAmount, BigDecimal partnerAmount,
            BigDecimal lodgingCustomerAmount, BigDecimal lodgingPartnerAmount, String taxCode, EffectivePeriod period
    ) {
        this.region = region;
        this.currencyCode = currencyCode;
        this.customerAmount = customerAmount;
        this.partnerAmount = partnerAmount;
        this.lodgingCustomerAmount = lodgingCustomerAmount != null ? lodgingCustomerAmount : BigDecimal.ZERO;
        this.lodgingPartnerAmount = lodgingPartnerAmount != null ? lodgingPartnerAmount : BigDecimal.ZERO;
        this.taxCode = taxCode;
        this.period = period;
    }

    public void close(LocalDate effectiveTo) {
        this.period = period.close(effectiveTo);
    }
}
