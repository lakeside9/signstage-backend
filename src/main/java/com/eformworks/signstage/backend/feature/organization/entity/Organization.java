package com.eformworks.signstage.backend.feature.organization.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조직(고객사). signstage-docs business/user-organization-design.md 3.2절 스키마를 따른다.
 * 사업자 정보(사업자등록번호 등)와 과금 연동용 상태 전환은 이번 최소 구현 범위 밖이라
 * 아직 매핑/노출하지 않는다 — 컬럼은 마이그레이션에 미리 만들어 두고 필요할 때 확장한다.
 */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    @Column(name = "default_locale", nullable = false, length = 10)
    private String defaultLocale;

    @Column(name = "default_language_code", nullable = false, length = 10)
    private String defaultLanguageCode;

    @Column(name = "default_time_zone_id", nullable = false, length = 50)
    private String defaultTimeZoneId;

    @Column(name = "billing_currency_code", nullable = false, length = 3)
    private String billingCurrencyCode;

    /**
     * 데모 조직 여부 — signstage-docs
     * business/demo-account-exhibition-signer-preview-review.md 11장(2026-09-09, 결정 번복) 참고.
     * 데모 조직에서는 {@code CeremonyService.findActiveMemberOrThrow}가 실제 {@code Member} 행
     * 없이도 플랫폼 관리자를 가상 멤버로 우회시켜준다 — 플랫폼 관리자가 데모 행사 생성부터
     * 문서·서명자 등록, 하위 행사 제어까지 전부 직접 할 수 있게 하기 위해서다. 기본값 false.
     */
    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Builder
    private Organization(
            String name,
            String code,
            String defaultLanguageCode,
            String defaultLocale,
            String defaultTimeZoneId,
            String billingCurrencyCode,
            boolean demo
    ) {
        this.name = name;
        this.code = code;
        this.status = OrganizationStatus.ACTIVE;
        this.defaultLanguageCode = InternationalizationDefaults.languageCodeOrDefault(defaultLanguageCode);
        this.defaultLocale = InternationalizationDefaults.formatLocaleOrDefault(defaultLocale);
        this.defaultTimeZoneId = InternationalizationDefaults.timeZoneIdOrDefault(defaultTimeZoneId);
        this.billingCurrencyCode = InternationalizationDefaults.currencyCodeOrDefault(billingCurrencyCode);
        this.demo = demo;
    }

    /**
     * 플랫폼 관리자가 조직을 정지/재개할 때 사용한다
     * (signstage-docs business/platform-admin-member-management.md 참고).
     */
    public void changeStatus(OrganizationStatus status) {
        this.status = status;
    }

    /**
     * OWNER가 조직 정보를 수정할 때 사용한다({@code OrganizationService#updateOrganization}).
     * code는 조직을 식별하는 값이라 이 경로로 바꾸지 않는다.
     */
    public void updateInfo(
            String name,
            String defaultLanguageCode,
            String defaultLocale,
            String defaultTimeZoneId,
            String billingCurrencyCode
    ) {
        this.name = name;
        this.defaultLanguageCode = InternationalizationDefaults.languageCodeOrDefault(defaultLanguageCode);
        this.defaultLocale = InternationalizationDefaults.formatLocaleOrDefault(defaultLocale);
        this.defaultTimeZoneId = InternationalizationDefaults.timeZoneIdOrDefault(defaultTimeZoneId);
        this.billingCurrencyCode = InternationalizationDefaults.currencyCodeOrDefault(billingCurrencyCode);
    }
}
