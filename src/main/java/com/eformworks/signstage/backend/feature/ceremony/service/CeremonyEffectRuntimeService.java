package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체완료(ALL_SIGNATURES_COMPLETED, PROJECTOR) 효과의 실행·runtime 제어 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-RUNTIME. 개별 서명
 * (SIGNATURE_COMPLETED) 효과는 이 서비스를 거치지 않는다 — frontend가
 * {@code SIGNATURE_COMPLETED} 사실 이벤트와 이미 받아둔 효과 설정 snapshot만으로 스스로
 * enqueue한다(PRE-04). 이 서비스가 다루는 건 자동/수동 어느 쪽이든 서버가 명시적으로
 * "재생하라"고 명령해야 하는 전체완료 효과 하나뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyEffectRuntimeService {

    private static final CeremonyEffectTarget CELEBRATION_TARGET = CeremonyEffectTarget.PROJECTOR;
    private static final CeremonyEffectTrigger CELEBRATION_TRIGGER = CeremonyEffectTrigger.ALL_SIGNATURES_COMPLETED;
    private static final Duration MANUAL_TRIGGER_RATE_LIMIT = Duration.ofSeconds(3);

    private final CeremonyEventRepository ceremonyEventRepository;
    private final CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final CeremonyEventSignerStateService ceremonyEventSignerStateService;
    private final CeremonyEventService ceremonyEventService;
    private final CeremonyRealtimeNotifier ceremonyRealtimeNotifier;
    private final CeremonyService ceremonyService;

    /**
     * 이 프로젝트에 분산 rate limit 인프라(Redis 등)가 없어 단일 인스턴스 전제로 메모리에
     * 둔다(BE-RUNTIME-04) — WebSocket 자체가 이미 다중 서버 확장 시 별도 인프라가 필요하다고
     * 문서화돼 있다(ceremony-feature-migration-review.md 8장 미결 항목과 같은 전제).
     */
    private final ConcurrentHashMap<Long, Instant> lastManualTriggerAtByEventId = new ConcurrentHashMap<>();

    /**
     * BE-RUNTIME-02 — 서명 완료 트랜잭션이 커밋된 뒤 별도 {@code REQUIRES_NEW} 트랜잭션에서
     * "지금 막 전원 완료로 전환됐는가"를 판정하고, 그렇다면 {@code claimAutomaticCelebration}
     * 원자적 조건부 UPDATE로 최초 1회 자격을 얻는다({@link SignerPortalService#completeSignature}가
     * {@code afterCommit}에서 호출한다). 이 메서드 자체가 실패해도 이미 커밋된 서명 결과에는
     * 영향이 없다 — 호출부가 이미 트랜잭션 경계 밖이고, 아래에서도 예외를 전부 잡아 삼킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryAutomaticCelebration(Long eventId) {
        log.info("[CUTOVER-DEBUG] tryAutomaticCelebration invoked. eventId={}", eventId);
        try {
            CeremonyEvent event = ceremonyEventRepository.findById(eventId).orElse(null);
            if (event == null || event.getStatus() != CeremonyEventStatus.STARTED) {
                log.info("[CUTOVER-DEBUG] bail: event null or not STARTED. event={}, status={}",
                        event == null ? null : event.getId(), event == null ? null : event.getStatus());
                return;
            }

            Set<Long> requiredSignerIds = ceremonyEventService.collectFinishRequiredSignerIds(event);
            log.info("[CUTOVER-DEBUG] requiredSignerIds={}", requiredSignerIds);
            if (requiredSignerIds.isEmpty()) {
                // "필수 signer 집합이 비어 있으면 전체 완료로 보지 않는다" — allMatch의 공허한
                // 참(vacuous truth)이 빈 이벤트를 완료로 오판하지 않게 막는다.
                log.info("[CUTOVER-DEBUG] bail: requiredSignerIds empty");
                return;
            }
            boolean allComplete = ceremonyEventSignerStateService.isAllComplete(eventId, requiredSignerIds);
            log.info("[CUTOVER-DEBUG] isAllComplete={}", allComplete);
            if (!allComplete) {
                return;
            }

            int claimed = ceremonyEventRepository.claimAutomaticCelebration(eventId);
            log.info("[CUTOVER-DEBUG] claimAutomaticCelebration affected rows={}", claimed);
            if (claimed == 0) {
                return; // 이미 다른 호출(동시 완료 경합, 또는 재완료)이 먼저 claim했다.
            }

            // 클레임에 성공한 시점부터가 "최초 완료 이력 소비"다 — 아래에서 설정이 없거나
            // 비활성/runtime OFF라 실제로 방송하지 못해도 이 claim은 되돌리지 않는다. 그래야
            // 나중에 runtime을 켜거나 정의를 다시 활성화해도 지난 완료가 소급 실행되지 않는다.
            ceremonyRealtimeNotifier.notifyAllSignersCompleted(eventId); // 구 frontend 호환(PRE-04)

            var settingOpt = ceremonyEventEffectSettingRepository
                    .findByEventIdAndClassificationWithDefinition(eventId, CELEBRATION_TARGET, CELEBRATION_TRIGGER);
            log.info("[CUTOVER-DEBUG] setting present={}, enabled={}, runtimeEnabled={}",
                    settingOpt.isPresent(),
                    settingOpt.map(s -> s.getDefinition().isEnabled()).orElse(null),
                    settingOpt.map(CeremonyEventEffectSetting::isRuntimeEnabled).orElse(null));
            settingOpt
                    .filter(setting -> setting.getDefinition().isEnabled() && setting.isRuntimeEnabled())
                    .ifPresent(setting -> {
                        log.info("[CUTOVER-DEBUG] broadcasting auto celebration. effectCode={}", setting.getDefinition().getCode());
                        recordAndBroadcastRequest(event, setting, ActorType.SYSTEM, 0L, "auto");
                    });
        } catch (Exception e) {
            log.warn("전원완료 자동 효과 판정/실행 실패 — 서명 결과에는 영향 없음. eventId={}", eventId, e);
        }
    }

    /** BE-RUNTIME-03 — STARTED 행사에서만 허용한다. 비활성 정의를 ON으로 바꾸는 요청은 거부한다. */
    @Transactional
    public void updateRuntimeEnabled(
            Long organizationId, Long ceremonyId, Long eventId, Long currentUserId,
            String targetType, String triggerType, boolean runtimeEnabled
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }

        CeremonyEventEffectSetting setting = findSettingOrThrow(eventId, targetType, triggerType);
        if (runtimeEnabled && !setting.getDefinition().isEnabled()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_INACTIVE);
        }
        if (setting.isRuntimeEnabled() == runtimeEnabled) {
            return; // 이미 같은 값 — "과거 요청을 새로 만들지 않는다"는 최소한 로그/방송을 새로 만들지 않는 것까지 포함한다.
        }

        setting.updateRuntimeEnabled(runtimeEnabled);
        CeremonyEventLog log = ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(ActorType.ADMIN)
                        .actorId(currentUserId)
                        .eventAction(CeremonyEventAction.EFFECT_RUNTIME_CHANGED)
                        .message(
                                "targetType=" + setting.getId().getTargetType()
                                        + ", triggerType=" + setting.getId().getTriggerType()
                                        + ", runtimeEnabled=" + runtimeEnabled
                        )
                        .build()
        );
        // setting.changed 전용 — 과거 요청(effect.requested)을 다시 만들지 않는다(PRE-04).
        ceremonyRealtimeNotifier.notifyEffectSettingChanged(eventId, setting, log.getId());
    }

    /**
     * BE-RUNTIME-04 — 관리자가 전체 효과를 즉시 재생한다. 현재 완료 인원이나 자동 실행 이력은
     * 조건에서 뺀다(자동 경로와 완전히 독립) — 몇 번이든 다시 재생할 수 있되, 짧은 시간
     * 안에서는 rate limit으로 막는다.
     */
    @Transactional
    public void triggerManualCelebration(
            Long organizationId, Long ceremonyId, Long eventId, Long currentUserId, String targetType, String triggerType
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }

        CeremonyEventEffectSetting setting = findSettingOrThrow(eventId, targetType, triggerType);
        if (!setting.getDefinition().isEnabled()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_INACTIVE);
        }
        if (!setting.isRuntimeEnabled()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_RUNTIME_DISABLED);
        }
        if (!setting.getDefinition().isManuallyTriggerable()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_NOT_MANUALLY_TRIGGERABLE);
        }
        checkManualTriggerRateLimit(eventId);

        recordAndBroadcastRequest(event, setting, ActorType.ADMIN, currentUserId, "manual");
    }

    /** {@code compute}로 "간격 확인 + 갱신"을 원자적으로 묶는다 — get 다음 put은 그 사이 다른 요청이 끼어들 수 있다. */
    private void checkManualTriggerRateLimit(Long eventId) {
        Instant now = Instant.now();
        boolean[] rejected = {false};
        lastManualTriggerAtByEventId.compute(eventId, (id, previous) -> {
            if (previous != null && Duration.between(previous, now).compareTo(MANUAL_TRIGGER_RATE_LIMIT) < 0) {
                rejected[0] = true;
                return previous;
            }
            return now;
        });
        if (rejected[0]) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_MANUAL_TRIGGER_RATE_LIMITED);
        }
    }

    /** 감사 로그를 남기고 {@code requestId}(그 로그 id 기반, FE-CORE 중복 제거 키)로 재생 명령을 방송한다. */
    private void recordAndBroadcastRequest(
            CeremonyEvent event, CeremonyEventEffectSetting setting, ActorType actorType, Long actorId, String triggeredBy
    ) {
        CeremonyEventAction action = "auto".equals(triggeredBy)
                ? CeremonyEventAction.EFFECT_AUTO_TRIGGERED
                : CeremonyEventAction.EFFECT_MANUAL_TRIGGERED;
        CeremonyEventLog log = ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(actorType)
                        .actorId(actorId)
                        .eventAction(action)
                        .message("effectDefinitionId=" + setting.getDefinition().getId())
                        .build()
        );
        String requestId = triggeredBy + "-" + log.getId();
        ceremonyRealtimeNotifier.notifyEffectRequested(event.getId(), setting, requestId, triggeredBy, log.getId());
    }

    private CeremonyEventEffectSetting findSettingOrThrow(Long eventId, String targetType, String triggerType) {
        CeremonyEffectTarget target = parseTarget(targetType);
        CeremonyEffectTrigger trigger = parseTrigger(triggerType);
        return ceremonyEventEffectSettingRepository
                .findByEventIdAndClassificationWithDefinition(eventId, target, trigger)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.EFFECT_SETTING_NOT_FOUND));
    }

    private CeremonyEvent findEventInCeremonyOrThrow(Long ceremonyId, Long eventId) {
        CeremonyEvent event = ceremonyEventRepository.findById(eventId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND));
        if (!event.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND);
        }
        return event;
    }

    private CeremonyEffectTarget parseTarget(String targetType) {
        try {
            return CeremonyEffectTarget.valueOf(targetType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private CeremonyEffectTrigger parseTrigger(String triggerType) {
        try {
            return CeremonyEffectTrigger.valueOf(triggerType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
