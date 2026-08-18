package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
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
 * 행사 마스터. {@code organization_id}만 갖고 하위 엔티티(CeremonyEvent 등)는 ceremony_id FK
 * 체인으로 조직에 스코핑된다 — signstage-docs business/ceremony-feature-migration-review.md
 * 4.1절 결정. {@code creator}를 별도로 두지 않고 {@link BaseEntity#getCreatedBy()}로 충분하다
 * (같은 문서 4.6절) — "본인/배정 건" 판정은 {@link CeremonyAssignment}가 담당한다(4.7절).
 *
 * <p>{@code billingPlan}은 DB 컬럼 자체는 nullable이다 — 이 기능 배포 전 만들어진 기존 행사만
 * 예외로 NULL을 허용하고, 신규 생성은 서비스 레이어가 필수로 강제한다(signstage-docs
 * business/ceremony-billing-options-review.md 4.10절).
 *
 * <p>{@code status}는 {@link CeremonyStatus} 참고 — 새로 만든 Ceremony는 항상 IN_PROGRESS로
 * 시작한다(이 기능 배포 전 기존 행은 마이그레이션에서 소급 적용 없이 DEFAULT로만 채운다).
 */
@Entity
@Table(name = "ceremonies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ceremony extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id")
    private BillingPlan billingPlan;

    @Column(nullable = false, length = 200)
    private String title;

    /** 생성 시에는 받지 않고 행사 수정 화면에서만 채운다 — 그래서 nullable이다. */
    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CeremonyStatus status;

    @Builder
    private Ceremony(Organization organization, BillingPlan billingPlan, String title) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.title = title;
        this.status = CeremonyStatus.IN_PROGRESS;
    }

    /** 행사 수정 화면에서 이름/설명을 바꿀 때 쓴다. 플랜은 여기서 바꾸지 않는다(생성 시점에 고정). */
    public void updateInfo(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /**
     * 하위 행사(MAIN 전체)가 완료되면 자동으로, 또는 플랫폼 관리자가 수동으로 상태를 바꿀 때 쓴다
     * (signstage-docs business/ceremony-feature-migration-review.md 참고).
     */
    public void changeStatus(CeremonyStatus status) {
        this.status = status;
    }
}
