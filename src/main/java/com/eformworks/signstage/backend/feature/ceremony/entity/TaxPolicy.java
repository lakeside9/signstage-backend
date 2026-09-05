package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 국가·세금 코드·유효기간별 세금 계산 정책. */
@Entity
@Table(name = "tax_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "administrative_area", length = 50)
    private String administrativeArea;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxCategory category;

    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "price_inclusion", nullable = false, length = 10)
    private String priceInclusion;

    @Column(name = "rounding_level", nullable = false, length = 10)
    private String roundingLevel;

    @Column(name = "rounding_mode", nullable = false, length = 20)
    private String roundingMode;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;
}
