package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Builder
    private Ceremony(Organization organization, BillingPlan billingPlan, String title) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.title = title;
    }
}
