package com.eformworks.signstage.backend.feature.ceremony.entity;

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
 * 행사 이벤트 효과 카탈로그 한 건(예: 서명 하이라이트, 전원완료 폭죽) — 플랫폼 관리자가 등록·수정한다.
 * legacy signstage의 고정 효과 목록을 데이터로 옮긴 것으로, signstage-docs
 * business/ceremony-event-effect-migration-plan.md/-implementation-tasks.md(BE-CATALOG-01) 참고.
 *
 * <p>{@code code}/{@code targetType}/{@code triggerType}/{@code rendererKey}/
 * {@code requiredOptionalFeatureId}는 등록 후 불변이다(서비스에서 update 시 바꾸지 않는다) —
 * 이 값들이 바뀌면 이미 저장된 {@code ceremony_event_effect_settings}의 의미가 깨지기 때문이다.
 */
@Entity
@Table(name = "ceremony_effect_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyEffectDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private CeremonyEffectTarget targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private CeremonyEffectTrigger triggerType;

    /**
     * 이 효과를 쓰려면 조직이 구매해 적용해둬야 하는 선택옵션(entitlement) — 예: 서명
     * 하이라이트류는 {@code SIGNER_FIELD_ZOOM}, 폭죽류는 {@code ALL_SIGNED_FIREWORKS}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_optional_feature_id", nullable = false)
    private OptionalFeature requiredOptionalFeature;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(name = "renderer_key", nullable = false, length = 100)
    private String rendererKey;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "is_user_visible", nullable = false)
    private boolean userVisible;

    /** 전체완료(ALL_SIGNATURES_COMPLETED) 효과 중 "수동 전체 실행" 버튼 대상이 될 수 있는지. */
    @Column(name = "manually_triggerable", nullable = false)
    private boolean manuallyTriggerable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 정규화된 JSON object 문자열, 없으면 null. 검증·정규화는 서비스가 담당한다. */
    @Column(name = "config_json", columnDefinition = "json")
    private String configJson;

    @Builder
    private CeremonyEffectDefinition(
            String code,
            CeremonyEffectTarget targetType,
            CeremonyEffectTrigger triggerType,
            OptionalFeature requiredOptionalFeature,
            String displayName,
            String description,
            String rendererKey,
            Boolean manuallyTriggerable,
            int displayOrder,
            String configJson
    ) {
        this.code = code;
        this.targetType = targetType;
        this.triggerType = triggerType;
        this.requiredOptionalFeature = requiredOptionalFeature;
        this.displayName = displayName;
        this.description = description;
        this.rendererKey = rendererKey;
        this.enabled = true;
        this.userVisible = true;
        this.manuallyTriggerable = manuallyTriggerable != null && manuallyTriggerable;
        this.displayOrder = displayOrder;
        this.configJson = configJson;
    }

    /**
     * 플랫폼 관리자 수정. {@code code}/{@code targetType}/{@code triggerType}/{@code rendererKey}/
     * {@code requiredOptionalFeature}는 여기서 바꾸지 않는다(클래스 주석 참고).
     */
    public void updateInfo(
            String displayName,
            String description,
            boolean enabled,
            boolean userVisible,
            boolean manuallyTriggerable,
            String configJson
    ) {
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.userVisible = userVisible;
        this.manuallyTriggerable = manuallyTriggerable;
        this.configJson = configJson;
    }

    /** BE-CATALOG-03의 그룹 재정규화(10 단위 배치)에서 사용한다. */
    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
