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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 이벤트 효과 카탈로그 한 건(예: 서명 하이라이트, 전원완료 폭죽) — 플랫폼 관리자가 등록·수정한다.
 * legacy signstage의 고정 효과 목록을 데이터로 옮긴 것으로, signstage-docs
 * business/ceremony-event-effect-migration-plan.md/-implementation-tasks.md(BE-CATALOG-01) 참고.
 *
 * <p>{@code code}/{@code targetType}/{@code triggerType}/{@code rendererKey}는 등록 후
 * 불변이다(서비스에서 update 시 바꾸지 않는다) — 이 값들이 바뀌면 이미 저장된
 * {@code ceremony_event_effect_settings}의 의미가 깨지기 때문이다.
 *
 * <p>이 효과를 쓰려면 조직이 구매해 적용해둬야 하는 선택옵션(entitlement)은 더 이상 이
 * 엔티티가 단일 FK로 갖지 않는다(2026-09-08 결정) — 선택옵션(특히 "이벤트 효과 묶음" 종류)이
 * 몇 개짜리 묶음 상품으로 계속 늘어날 수 있어야 해서, {@code CeremonyEffectDefinitionOption}
 * N:N 매핑으로 옮겼다. 효과 하나가 여러 묶음(예: "3종"과 "5종")에 동시에 포함될 수 있고,
 * 묶음 구성은 선택옵션 쪽(카탈로그 관리 화면)에서 편집한다 — 이 엔티티는 어느 묶음에
 * 속하는지 알 필요가 없다.
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
     * 플랫폼 관리자 수정. {@code code}/{@code targetType}/{@code triggerType}/{@code rendererKey}는
     * 여기서 바꾸지 않는다(클래스 주석 참고).
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
