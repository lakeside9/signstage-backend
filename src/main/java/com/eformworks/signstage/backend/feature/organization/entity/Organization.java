package com.eformworks.signstage.backend.feature.organization.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
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

    @Builder
    private Organization(String name, String code, String defaultLocale) {
        this.name = name;
        this.code = code;
        this.status = OrganizationStatus.ACTIVE;
        this.defaultLocale = defaultLocale != null ? defaultLocale : "ko-KR";
    }

    /**
     * 플랫폼 관리자가 조직을 정지/재개할 때 사용한다
     * (signstage-docs business/platform-admin-member-management.md 참고).
     */
    public void changeStatus(OrganizationStatus status) {
        this.status = status;
    }
}
