package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEffectRuntimeService} 단위 테스트 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-RUNTIME-05.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyEffectRuntimeServiceTest {

    private static final Long EVENT_ID = 100L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long SIGNER_ID = 20L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private CeremonyEventRepository ceremonyEventRepository;
    @Mock
    private CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;
    @Mock
    private CeremonyEventLogRepository ceremonyEventLogRepository;
    @Mock
    private CeremonyEventSignerStateService ceremonyEventSignerStateService;
    @Mock
    private CeremonyEventService ceremonyEventService;
    @Mock
    private CeremonyRealtimeNotifier ceremonyRealtimeNotifier;
    @Mock
    private CeremonyService ceremonyService;

    @InjectMocks
    private CeremonyEffectRuntimeService service;

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

    private Ceremony ceremony() {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        return ceremony;
    }

    private CeremonyEventEffectSetting setting(CeremonyEvent event, boolean definitionEnabled, boolean runtimeEnabled, boolean manuallyTriggerable) {
        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code("FIREWORKS").targetType(CeremonyEffectTarget.PROJECTOR)
                .triggerType(CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED)
                .displayName("폭죽").rendererKey("projector-fireworks")
                .manuallyTriggerable(manuallyTriggerable)
                .displayOrder(10)
                .build();
        ReflectionTestUtils.setField(definition, "id", 1L);
        if (!definitionEnabled) {
            definition.updateInfo("폭죽", null, false, true, manuallyTriggerable, null);
        }

        CeremonyEventEffectSetting setting = CeremonyEventEffectSetting.builder().event(event).definition(definition).build();
        if (!runtimeEnabled) {
            setting.updateRuntimeEnabled(false);
        }
        return setting;
    }

    @BeforeEach
    void setUp() {
        lenient().when(ceremonyEventLogRepository.save(any())).thenAnswer(inv -> {
            CeremonyEventLog log = inv.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 999L);
            return log;
        });
    }

    // ── tryAutomaticCelebration ─────────────────────────────────────────

    @Test
    @DisplayName("필수 서명자 집합이 비어 있으면 전체 완료로 보지 않는다(claim하지 않는다)")
    void tryAutomaticCelebration_emptyRequiredSigners_doesNotClaim() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of());

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyEventRepository, never()).claimAutomaticCelebration(anyLong());
        verify(ceremonyRealtimeNotifier, never()).notifyAllSignersCompleted(anyLong());
    }

    @Test
    @DisplayName("아직 전원 완료가 아니면 claim하지 않는다")
    void tryAutomaticCelebration_notAllComplete_doesNotClaim() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(false);

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyEventRepository, never()).claimAutomaticCelebration(anyLong());
    }

    @Test
    @DisplayName("동시에 마지막 두 명이 완료해도 claim에 실패한 쪽은 아무것도 방송하지 않는다")
    void tryAutomaticCelebration_claimLost_doesNotBroadcast() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(0); // 이미 다른 호출이 claim함

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyRealtimeNotifier, never()).notifyAllSignersCompleted(anyLong());
        verify(ceremonyRealtimeNotifier, never()).notifyEffectRequested(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("claim에 성공하고 설정이 활성+runtime ON이면 구 호환 이벤트와 신규 효과 요청을 모두 방송한다")
    void tryAutomaticCelebration_claimWon_eligibleSetting_broadcastsBoth() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, true, false);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(1);
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyRealtimeNotifier, times(1)).notifyAllSignersCompleted(EVENT_ID);
        verify(ceremonyRealtimeNotifier, times(1))
                .notifyEffectRequested(EVENT_ID, setting, "auto-999", "auto", 999L);
    }

    @Test
    @DisplayName("claim에 성공해도 설정/정의/runtime이 방송 자격을 못 채우면 신규 효과 요청은 방송하지 않는다(구 호환은 방송)")
    void tryAutomaticCelebration_claimWon_ineligibleSetting_onlyLegacyBroadcast() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, false, false); // runtime OFF
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(1);
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyRealtimeNotifier, times(1)).notifyAllSignersCompleted(EVENT_ID);
        verify(ceremonyRealtimeNotifier, never()).notifyEffectRequested(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("설정 자체가 없어도 claim(최초 완료 이력 소비)은 이미 끝난 채로 조용히 끝난다")
    void tryAutomaticCelebration_noSetting_stillClaimsButDoesNotBroadcastEffect() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(1);
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.empty());

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyRealtimeNotifier, times(1)).notifyAllSignersCompleted(EVENT_ID);
        verify(ceremonyRealtimeNotifier, never()).notifyEffectRequested(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("판정 도중 예외가 나도 호출부로 전파하지 않는다(서명 성공과 분리)")
    void tryAutomaticCelebration_exception_isSwallowed() {
        given(ceremonyEventRepository.findById(EVENT_ID)).willThrow(new RuntimeException("boom"));

        service.tryAutomaticCelebration(EVENT_ID); // 예외가 여기서 나오면 이 테스트는 실패한다.
    }

    @Test
    @DisplayName("WebSocket 발행이 강제로 실패해도 예외가 전파되지 않는다")
    void tryAutomaticCelebration_webSocketPublishFails_isSwallowed() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(1);
        doThrow(new RuntimeException("websocket down"))
                .when(ceremonyRealtimeNotifier).notifyAllSignersCompleted(EVENT_ID);

        // 이 시점에서 이미 claim은 성공(최초 완료 이력 소비)했고, 그 다음 WebSocket 발행만
        // 실패한 것이다 — 이 메서드가 REQUIRES_NEW + afterCommit으로 서명 트랜잭션과 이미
        // 완전히 분리돼 있으므로, 여기서 예외가 나도 서명 결과 자체엔 구조적으로 영향이 없다.
        service.tryAutomaticCelebration(EVENT_ID);
    }

    @Test
    @DisplayName("초기화 후 재완료해도(claim이 이미 소비됐으면) 자동 명령이 반복되지 않는다")
    void tryAutomaticCelebration_reCompletionAfterClear_doesNotRepeat() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventService.collectFinishRequiredSignerIds(event)).willReturn(Set.of(SIGNER_ID));
        // 지움→재서명→완료 이후에도 "전원 완료"는 다시 참이지만, claim은 최초 1회만 성공하므로
        // 이번엔 0을 반환한다(claimAutomaticCelebration 자체가 이미 소비된 이벤트에 대해선
        // 항상 0을 반환하도록 구현돼 있다 — 리포지토리 레벨 보장, 여기서는 그 계약을 모킹으로
        // 재현해 서비스가 그 결과를 올바르게 존중하는지만 검증한다).
        given(ceremonyEventSignerStateService.isAllComplete(EVENT_ID, Set.of(SIGNER_ID))).willReturn(true);
        given(ceremonyEventRepository.claimAutomaticCelebration(EVENT_ID)).willReturn(0);

        service.tryAutomaticCelebration(EVENT_ID);

        verify(ceremonyRealtimeNotifier, never()).notifyAllSignersCompleted(anyLong());
        verify(ceremonyRealtimeNotifier, never()).notifyEffectRequested(any(), any(), any(), any(), any());
    }

    // ── updateRuntimeEnabled ─────────────────────────────────────────

    @Test
    @DisplayName("STARTED가 아닌 이벤트는 runtime을 바꿀 수 없다")
    void updateRuntimeEnabled_notStarted_fail() {
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event(CeremonyEventStatus.READY)));

        assertThatThrownBy(() -> service.updateRuntimeEnabled(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED", true
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("비활성 정의는 runtime을 ON으로 바꿀 수 없다")
    void updateRuntimeEnabled_inactiveDefinition_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting(event, false, false, false)));

        assertThatThrownBy(() -> service.updateRuntimeEnabled(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED", true
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_DEFINITION_INACTIVE);
    }

    @Test
    @DisplayName("이미 같은 값이면 로그를 남기거나 방송하지 않는다")
    void updateRuntimeEnabled_alreadySameValue_noop() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, true, false); // 이미 runtime ON
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.updateRuntimeEnabled(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED", true);

        verify(ceremonyEventLogRepository, never()).save(any());
        verify(ceremonyRealtimeNotifier, never()).notifyEffectSettingChanged(any(), any(), any());
    }

    @Test
    @DisplayName("정상 변경은 로그를 남기고 setting.changed만 방송한다(effect.requested는 방송하지 않는다)")
    void updateRuntimeEnabled_success_broadcastsSettingChangedOnly() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, true, false);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.updateRuntimeEnabled(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED", false);

        assertThat(setting.isRuntimeEnabled()).isFalse();
        verify(ceremonyEventLogRepository, times(1)).save(any());
        verify(ceremonyRealtimeNotifier, times(1)).notifyEffectSettingChanged(EVENT_ID, setting, 999L);
        verify(ceremonyRealtimeNotifier, never()).notifyEffectRequested(any(), any(), any(), any(), any());
    }

    // ── triggerManualCelebration ─────────────────────────────────────────

    @Test
    @DisplayName("STARTED가 아닌 이벤트는 수동 실행할 수 없다")
    void triggerManualCelebration_notStarted_fail() {
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event(CeremonyEventStatus.READY)));

        assertThatThrownBy(() -> service.triggerManualCelebration(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("설정이 없으면 수동 실행할 수 없다")
    void triggerManualCelebration_noSetting_fail() {
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event(CeremonyEventStatus.STARTED)));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerManualCelebration(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_SETTING_NOT_FOUND);
    }

    @Test
    @DisplayName("runtime이 꺼져 있으면 수동 실행할 수 없다")
    void triggerManualCelebration_runtimeOff_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting(event, true, false, true)));

        assertThatThrownBy(() -> service.triggerManualCelebration(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_RUNTIME_DISABLED);
    }

    @Test
    @DisplayName("수동 실행을 지원하지 않는 효과는 거부된다")
    void triggerManualCelebration_notManuallyTriggerable_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting(event, true, true, false)));

        assertThatThrownBy(() -> service.triggerManualCelebration(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_NOT_MANUALLY_TRIGGERABLE);
    }

    @Test
    @DisplayName("정상 조건이면 수동 실행 로그를 남기고 effect.requested를 방송한다 — claim은 절대 건드리지 않는다")
    void triggerManualCelebration_success_neverTouchesAutoClaim() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, true, true);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.triggerManualCelebration(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED");

        verify(ceremonyRealtimeNotifier, times(1))
                .notifyEffectRequested(EVENT_ID, setting, "manual-999", "manual", 999L);
        // "수동 실행 후 최초 전체 완료 자동 실행은 별도로 한 번 가능한지" — 수동 경로가 claim
        // 컬럼을 절대 건드리지 않는다는 것으로 구조적으로 보장한다(자동 경로와 완전 독립).
        verify(ceremonyEventRepository, never()).claimAutomaticCelebration(anyLong());
    }

    @Test
    @DisplayName("짧은 시간 안의 반복 수동 실행은 rate limit으로 거부된다")
    void triggerManualCelebration_rateLimited_fail() {
        CeremonyEvent event = event(CeremonyEventStatus.STARTED);
        CeremonyEventEffectSetting setting = setting(event, true, true, true);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyEventEffectSettingRepository.findByEventIdAndClassificationWithDefinition(
                EVENT_ID, CeremonyEffectTarget.PROJECTOR, CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED
        )).willReturn(Optional.of(setting));

        service.triggerManualCelebration(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED");

        assertThatThrownBy(() -> service.triggerManualCelebration(
                ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, "PROJECTOR", "ALL_SIGNATURES_COMPLETED"
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EFFECT_MANUAL_TRIGGER_RATE_LIMITED);

        verify(ceremonyRealtimeNotifier, times(1)).notifyEffectRequested(any(), any(), any(), any(), any());
    }
}
