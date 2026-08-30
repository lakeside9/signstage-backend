package com.eformworks.signstage.backend.feature.organization.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파트너(조직) 정보 변경 이력. append-only다 — 수정/삭제 메서드를 두지 않는다(카탈로그
 * *History류와 같은 패턴, signstage-docs business/ceremony-billing-options-review.md 8장).
 *
 * <p>생성 시점, OWNER의 정보 수정({@code OrganizationService#updateOrganization}), 플랫폼
 * 관리자의 정보 수정({@code PlatformAdminOrganizationService#updateOrganizationInfo})과
 * 상태 변경({@code PlatformAdminOrganizationService#updateOrganizationStatus}) — 사용자가
 * 바꾸든 관리자가 바꾸든 이 네 지점 전부가 그 순간의 전체 상태를 스냅샷 한 행씩 남긴다
 * (2026-08-30 요청 — "사용자가 변경하거나 관리자가 변경하거나 모두 남겨주세요").
 */
@Entity
@Table(name = "organization_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    @Column(name = "default_locale", nullable = false, length = 10)
    private String defaultLocale;

    @Builder
    private OrganizationHistory(Organization organization) {
        this.organization = organization;
        this.name = organization.getName();
        this.code = organization.getCode();
        this.status = organization.getStatus();
        this.defaultLocale = organization.getDefaultLocale();
    }
}
