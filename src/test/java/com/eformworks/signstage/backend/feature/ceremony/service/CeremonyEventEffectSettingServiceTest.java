package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventEffectSettingDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEventEffectSettingService} 단위 테스트 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-SETTING-04.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyEventEffectSettingServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long EVENT_ID = 100L;
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long REQUIRED_FEATURE_ID = 20L;

    @Mock
    private CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;
    @Mock
    private CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    @Mock
    private CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    @Mock
    private CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    @Mock
    private CeremonyEventRepository ceremonyEventRepository;
    @Mock
    private CeremonyService ceremonyService;

    @InjectMocks
    private CeremonyEventEffectSettingService service;

    private Ceremony ceremony() {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        return ceremony;
    }

    private CeremonyEvent event(CeremonyEventStatus status) {
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony())
                .name("하위 행사")
                .eventType(CeremonyEventType.MAIN)
                .accessKey("access-key")
                .build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }

    private CeremonyEffectDefinition definition(Long id, boolean enabled) {
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("HIGHLIGHT").targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.SIGNATURE_COMPLETED)
                .displayName("하이라이트").rendererKey("r")
                .displayOrder(10)
                .build();
        ReflectionTestUtils.setField(definition, "id", id);
        if (!enabled) {
            definition.updateInfo("하이라이트", null, false, true, false, null);
        }
        return definition;
    }

    private CeremonyEventEffectSettingDto.Request.EffectSelection selection(String target, String trigger, Long effectId) {
        return new CeremonyEventEffectSettingDto.Request.EffectSelection(target, trigger, effectId);
    }

    @Test
    @DisplayName("이 이벤트에 적용되지 않은 선택옵션을 요구하는 효과는 선택할 수 없다")
    void applyEffectSelections_optionalFeatureNotApplied_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition(1L, true)));

        assertThatThrownBy(() -> service.applyEffectSelections(
                event, List.of(), List.of(selection("PROJECTOR", "SIGNATURE_COMPLETED", 1L))
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_SELECTION_OPTIONAL_FEATURE_NOT_APPLIED);
        verify(ceremonyEventEffectSettingRepository, never()).deleteAllByEventId(any());
        verify(ceremonyEventEffectSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성화된 효과 정의는 선택할 수 없다")
    void applyEffectSelections_inactiveDefinition_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition(1L, false)));

        assertThatThrownBy(() -> service.applyEffectSelections(
                event, List.of(REQUIRED_FEATURE_ID), List.of(selection("PROJECTOR", "SIGNATURE_COMPLETED", 1L))
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_INACTIVE);
    }

    @Test
    @DisplayName("선택 항목의 분류(target, trigger)가 정의와 다르면 거부된다")
    void applyEffectSelections_classificationMismatch_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition(1L, true)));

        assertThatThrownBy(() -> service.applyEffectSelections(
                event, List.of(REQUIRED_FEATURE_ID), List.of(selection("PROJECTOR", "ALL_SIGNATURES_COMPLETED", 1L))
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_SELECTION_CLASSIFICATION_MISMATCH);
    }

    @Test
    @DisplayName("존재하지 않는 target/trigger 값은 거부된다")
    void applyEffectSelections_invalidClassificationValue_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);

        assertThatThrownBy(() -> service.applyEffectSelections(
                event, List.of(REQUIRED_FEATURE_ID), List.of(selection("SCREEN", "SIGNATURE_COMPLETED", 1L))
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("여러 선택 중 하나라도 검증에 실패하면 전부 저장하지 않는다(원자적)")
    void applyEffectSelections_partiallyInvalid_savesNothing() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition(1L, true)));
        given(ceremonyEffectDefinitionRepository.findById(2L)).willReturn(Optional.empty());
        given(ceremonyEffectDefinitionOptionRepository.existsByEffectDefinitionIdAndUnitProductIdIn(
                1L, List.of(REQUIRED_FEATURE_ID)
        )).willReturn(true);

        assertThatThrownBy(() -> service.applyEffectSelections(
                event, List.of(REQUIRED_FEATURE_ID), List.of(
                        selection("PROJECTOR", "SIGNATURE_COMPLETED", 1L),
                        selection("PROJECTOR", "ALL_SIGNATURES_COMPLETED", 2L)
                )
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
        verify(ceremonyEventEffectSettingRepository, never()).deleteAllByEventId(any());
        verify(ceremonyEventEffectSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 선택은 전체 교체(delete-then-recreate)로 저장된다")
    void applyEffectSelections_success_replacesAll() {
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        given(ceremonyEffectDefinitionRepository.findById(1L)).willReturn(Optional.of(definition(1L, true)));
        given(ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(EVENT_ID)).willReturn(List.of());
        given(ceremonyEffectDefinitionOptionRepository.existsByEffectDefinitionIdAndUnitProductIdIn(
                1L, List.of(REQUIRED_FEATURE_ID)
        )).willReturn(true);

        service.applyEffectSelections(
                event, List.of(REQUIRED_FEATURE_ID), List.of(selection("PROJECTOR", "SIGNATURE_COMPLETED", 1L))
        );

        verify(ceremonyEventEffectSettingRepository).deleteAllByEventId(EVENT_ID);
        verify(ceremonyEventEffectSettingRepository).save(any(CeremonyEventEffectSetting.class));
    }

    @Test
    @DisplayName("STARTED 이벤트는 조직 스코프 API로 효과 설정을 바꿀 수 없다")
    void updateEffectSettings_eventLocked_fail() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event(CeremonyEventStatus.STARTED)));

        CeremonyEventEffectSettingDto.Request.UpdateEffectSelections request =
                new CeremonyEventEffectSettingDto.Request.UpdateEffectSelections(List.of());

        assertThatThrownBy(() -> service.updateEffectSettings(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_LOCKED);
    }

    @Test
    @DisplayName("다른 ceremony 소속 이벤트는 찾을 수 없다")
    void updateEffectSettings_eventFromDifferentCeremony_fail() {
        Ceremony ceremony = ceremony();
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        CeremonyEvent event = event(CeremonyEventStatus.DRAFT);
        ReflectionTestUtils.setField(event.getCeremony(), "id", 999L); // 다른 ceremony 소속으로 위장
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        CeremonyEventEffectSettingDto.Request.UpdateEffectSelections request =
                new CeremonyEventEffectSettingDto.Request.UpdateEffectSelections(List.of());

        assertThatThrownBy(() -> service.updateEffectSettings(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("옵션 해제 시 그 옵션을 요구하는 설정만 지워지고 다른 설정은 유지된다")
    void pruneSettingsRequiringUnappliedFeatures_deletesOnlyDependentSettings() {
        CeremonyEffectDefinition dependsOnRemoved = definition(1L, true); // 원래 REQUIRED_FEATURE_ID가 열어주던 효과
        CeremonyEffectDefinition dependsOnKept = CeremonyEffectDefinition.builder()
                .code("FIREWORKS").targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED)
                .displayName("폭죽").rendererKey("r2").displayOrder(10)
                .build();
        ReflectionTestUtils.setField(dependsOnKept, "id", 2L);

        CeremonyEventEffectSetting settingToRemove = CeremonyEventEffectSetting.builder()
                .event(event(CeremonyEventStatus.DRAFT)).definition(dependsOnRemoved).build();
        CeremonyEventEffectSetting settingToKeep = CeremonyEventEffectSetting.builder()
                .event(event(CeremonyEventStatus.DRAFT)).definition(dependsOnKept).build();

        given(ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(EVENT_ID))
                .willReturn(List.of(settingToRemove, settingToKeep));
        given(ceremonyEffectDefinitionOptionRepository.existsByEffectDefinitionIdAndUnitProductIdIn(1L, List.of(30L)))
                .willReturn(false);
        given(ceremonyEffectDefinitionOptionRepository.existsByEffectDefinitionIdAndUnitProductIdIn(2L, List.of(30L)))
                .willReturn(true);

        // REQUIRED_FEATURE_ID(하이라이트)는 더 이상 적용되지 않고, 30L(폭죽)만 남았다고 가정.
        service.pruneSettingsRequiringUnappliedFeatures(EVENT_ID, List.of(30L));

        verify(ceremonyEventEffectSettingRepository).delete(settingToRemove);
        verify(ceremonyEventEffectSettingRepository, never()).delete(settingToKeep);
    }
}
