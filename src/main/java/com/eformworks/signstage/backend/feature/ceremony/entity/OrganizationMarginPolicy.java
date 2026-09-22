package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.Embedded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직(파트너)의 기본 재판매 마진 — signstage-docs
 * business/platform-partner-customer-billing-model-reference.md 4장 결정(2026-09-11). 조직당
 * 기간별 여러 건 — 해당 일자의 행이 없으면 "기본 마진을 설정하지 않음"을 뜻한다
 * (null sentinel 대신 행의 존재 자체로 표현, {@link CeremonyMarginOverride}와 같은 원칙).
 * 값은 {@link CustomerQuote} 생성 시 {@link CeremonyMarginOverride}가 없을 때의 폴백으로만
 * 쓰인다. 설정 주체는 파트너 OWNER뿐이고 플랫폼은 상한·승인 등 어떤 통제도 두지 않는다
 * ({@code CustomerQuoteService}가 {@code ACTION_MARGIN_POLICY_MANAGE}로 강제).
 */
@Entity
@Table(name = "organization_margin_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationMarginPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Embedded
    private MarginInfo margin;

    @Column(nullable = false)
    private LocalDate effectiveFrom;
    @Column(nullable = false)
    private LocalDate effectiveTo;

    @Builder
    private OrganizationMarginPolicy(Organization organization, MarginInfo margin, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.organization = organization;
        this.margin = margin;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void updatePeriod(MarginInfo margin, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.margin = margin;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
