package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사(Ceremony) 건별 마진 override — {@link OrganizationMarginPolicy}(조직 기본값)를
 * 이 행사에서만 덮어쓴다. signstage-docs
 * business/platform-partner-customer-billing-model-reference.md 4장 결정(2026-09-11), "조직
 * 기본값 + 행사별 override" 2단 구조. 행이 없으면 "이 행사는 override가 없다"는 뜻이라
 * {@code CustomerQuoteService}가 조직 기본값으로 폴백한다 — 행을 지우면(clear) 다시 조직
 * 기본값을 따른다.
 */
@Entity
@Table(name = "ceremony_margin_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyMarginOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false, unique = true)
    private Ceremony ceremony;

    @Embedded
    private MarginInfo margin;

    @Builder
    private CeremonyMarginOverride(Ceremony ceremony, MarginInfo margin) {
        this.ceremony = ceremony;
        this.margin = margin;
    }

    public void updateMargin(MarginInfo margin) {
        this.margin = margin;
    }
}
