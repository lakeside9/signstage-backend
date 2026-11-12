package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
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
 * 파트너 발주 단가표 — D05·D28 안 A. {@code organization}이 없으면 기본 단가표이고, 있으면 그
 * 파트너의 예외 단가표다(예외가 있으면 우선한다). 발주 금액은
 * {@code 기본 수행비 + 태블릿 대수 × 대당 단가 + 현장 인력 인·일 × 인·일 단가 + 지역 할증 ± 조정}이다.
 */
@Entity
@Table(name = "partner_rate_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerRateCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "base_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseFee;

    @Column(name = "tablet_unit_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal tabletUnitRate;

    @Column(name = "onsite_staff_day_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal onsiteStaffDayRate;

    @Embedded
    private EffectivePeriod period;

    @Builder
    private PartnerRateCard(
            Organization organization, String currencyCode, BigDecimal baseFee, BigDecimal tabletUnitRate,
            BigDecimal onsiteStaffDayRate, EffectivePeriod period
    ) {
        this.organization = organization;
        this.currencyCode = currencyCode;
        this.baseFee = baseFee;
        this.tabletUnitRate = tabletUnitRate;
        this.onsiteStaffDayRate = onsiteStaffDayRate;
        this.period = period;
    }

    public void close(LocalDate effectiveTo) {
        this.period = period.close(effectiveTo);
    }
}
