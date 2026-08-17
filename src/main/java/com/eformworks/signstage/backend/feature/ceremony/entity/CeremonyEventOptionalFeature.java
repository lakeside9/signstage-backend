package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이 CeremonyEvent에 실제로 적용된 선택옵션. 그 Ceremony가 구매한(플랜 기본 포함 또는
 * 추가구매) 선택옵션의 부분집합만 선택할 수 있다 — signstage-docs
 * business/ceremony-billing-options-review.md 4.11절 결정.
 */
@Entity
@Table(
        name = "ceremony_event_optional_features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ceof_event_feature",
                columnNames = {"ceremony_event_id", "optional_feature_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEventOptionalFeature extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_event_id", nullable = false)
    private CeremonyEvent ceremonyEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optional_feature_id", nullable = false)
    private OptionalFeature optionalFeature;

    @Builder
    private CeremonyEventOptionalFeature(CeremonyEvent ceremonyEvent, OptionalFeature optionalFeature) {
        this.ceremonyEvent = ceremonyEvent;
        this.optionalFeature = optionalFeature;
    }
}
