package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.dto.RealtimeEventDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 행사 실시간 상태 브로드캐스트. REST 처리가 성공한 뒤에 얹는 후처리다 — 별도
 * {@code @MessageMapping} 핸들러 없이 서버가 클라이언트로만 쏜다.
 *
 * <p>2.4절이 기록한 "진행 중인 펜 스트로크는 보내지 않는다"는 정책은 행사제어 화면을 legacy
 * {@code CeremonyControl.tsx}처럼 실시간 미리보기로 만들기로 하면서 뒤집었다 —
 * {@link #notifyStrokeSubmitted}가 스트로크 단위로도 보낸다. 새 토픽을 만들지 않고 기존
 * {@code /topic/events/{eventId}/state}를 그대로 재사용한다({@code CeremonyTopicAuthInterceptor}가
 * 이미 이 패턴을 accessKey로 인가하고 있어 변경이 필요 없다).
 *
 * <p>{@link #notifyEffectRequested}/{@link #notifyEffectSettingChanged}는 signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-RUNTIME-01이 추가한 신규 점(dot)
 * 표기 이벤트다 — {@link RealtimeEventDto} 클래스 주석 참고.
 */
@Service
@RequiredArgsConstructor
public class CeremonyRealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyStatusChanged(Long eventId, CeremonyEventStatus previousStatus, CeremonyEventStatus newStatus) {
        send(eventId, "EVENT_STATUS_CHANGED", Map.of(
                "previousStatus", previousStatus.name(),
                "newStatus", newStatus.name()
        ));
    }

    /** {@code completionId}는 이 완료를 남긴 감사 로그 id — {@code version}과 FE-CORE 중복 제거의 근거로 함께 쓰인다. */
    public void notifySignatureCompleted(Long eventId, Long signerId, String signerName, Long completionId) {
        send(eventId, "SIGNATURE_COMPLETED", Map.of(
                "signerId", signerId,
                "signerName", signerName,
                "completionId", completionId
        ), completionId);
    }

    /**
     * 이 이벤트의 필수 서명자 전원이 방금 완료로 전환된 순간에만 쏜다. 구 frontend 호환용
     * "사실" 이벤트라 신규 frontend는 이걸로 효과를 실행하지 않는다 — 전체완료 효과는
     * {@link #notifyEffectRequested}만 재생한다(PRE-04, 이중 재생 방지). 중복/누락 방지는
     * {@code CeremonyEffectRuntimeService#tryAutomaticCelebration}의
     * {@code claimAutomaticCelebration} 원자적 UPDATE가 보장한다(2026-09-07 갱신 — 예전엔
     * {@code CeremonyEvent} 행 잠금으로 보장했다).
     */
    public void notifyAllSignersCompleted(Long eventId) {
        send(eventId, "ALL_SIGNERS_COMPLETED", Map.of());
    }

    /**
     * 전체완료(ALL_SIGNATURES_COMPLETED) 효과를 실제로 재생하라는 명령 — 자동(최초 1회)과
     * 수동 둘 다 이 메시지 하나로 발행한다({@code triggeredBy}로 구분). {@code requestId}는
     * FE-CORE 스케줄러의 최근 200건 중복 제거 키, {@code auditLogId}는 {@code version}의
     * 근거다(BE-RUNTIME-01).
     */
    public void notifyEffectRequested(
            Long eventId, CeremonyEventEffectSetting setting, String requestId, String triggeredBy, Long auditLogId
    ) {
        CeremonyEffectDefinition definition = setting.getDefinition();
        send(eventId, "ceremony.effect.requested", Map.of(
                "targetType", setting.getId().getTargetType().name(),
                "triggerType", setting.getId().getTriggerType().name(),
                "effectCode", definition.getCode(),
                "rendererKey", definition.getRendererKey(),
                "requestId", requestId,
                "triggeredBy", triggeredBy
        ), auditLogId);
    }

    /**
     * runtime ON/OFF 변경 알림 전용 — 과거 요청을 다시 만들지 않는다(PRE-04). 프로젝터는 이
     * 메시지로 "지금부터 이 효과를 켜/꺼야 한다"만 갱신하고, 재생 자체는 절대 이 메시지로
     * 하지 않는다({@link #notifyEffectRequested}만 재생을 명령한다).
     */
    public void notifyEffectSettingChanged(Long eventId, CeremonyEventEffectSetting setting, Long auditLogId) {
        send(eventId, "ceremony.effect.setting.changed", Map.of(
                "targetType", setting.getId().getTargetType().name(),
                "triggerType", setting.getId().getTriggerType().name(),
                "effectCode", setting.getDefinition().getCode(),
                "runtimeEnabled", setting.isRuntimeEnabled()
        ), auditLogId);
    }

    public void notifySignatureCleared(Long eventId, Long signerId, Long templateFieldId) {
        send(eventId, "SIGNATURE_CLEARED", Map.of(
                "signerId", signerId,
                "templateFieldId", templateFieldId
        ));
    }

    public void notifySignatureReplaced(Long eventId, Long signerId, String signerName) {
        send(eventId, "SIGNATURE_REPLACED", Map.of(
                "signerId", signerId,
                "signerName", signerName
        ));
    }

    /**
     * 서명자 포털이 스트로크 하나를 저장할 때마다 보낸다 — 행사제어/프로젝터 화면이 이걸로
     * 실시간 펜 궤적을 그린다. {@code rawData}는 필드 박스 기준 0~1 좌표 JSON 배열(포털이
     * 제출한 그대로, 서버는 파싱하지 않고 중계만 한다).
     */
    public void notifyStrokeSubmitted(Long eventId, Long signerId, Long templateFieldId, Integer strokeSeq, String rawData) {
        send(eventId, "SIGNATURE_STROKE_SUBMITTED", Map.of(
                "signerId", signerId,
                "templateFieldId", templateFieldId,
                "strokeSeq", strokeSeq,
                "rawData", rawData
        ));
    }

    /** 기존 "사실" 이벤트 전용 — 전환 기간 동안 {@code version}은 null이다. */
    private void send(Long eventId, String type, Map<String, Object> payload) {
        send(eventId, type, payload, null);
    }

    private void send(Long eventId, String type, Map<String, Object> payload, Long version) {
        RealtimeEventDto event = new RealtimeEventDto(type, eventId, LocalDateTime.now(), payload, version);
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/state", event);
    }
}
