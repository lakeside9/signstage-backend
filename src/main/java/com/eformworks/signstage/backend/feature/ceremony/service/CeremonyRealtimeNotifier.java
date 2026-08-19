package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.dto.RealtimeEventDto;
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

    public void notifySignatureCompleted(Long eventId, Long signerId, String signerName) {
        send(eventId, "SIGNATURE_COMPLETED", Map.of(
                "signerId", signerId,
                "signerName", signerName
        ));
    }

    /**
     * 이 이벤트의 필수 서명자 전원이 방금 완료로 전환된 순간에만 쏜다 — 중복/누락 방지는
     * 호출부인 {@code SignerPortalService.completeSignature}가 {@code CeremonyEvent} 행 잠금으로
     * 보장한다. 프로젝터가 ALL_SIGNED_FIREWORKS 옵션이 적용된 이벤트에서만 이 메시지를 폭죽
     * 연출로 소비한다(다른 화면은 이 타입을 처리하지 않아 조용히 무시한다).
     */
    public void notifyAllSignersCompleted(Long eventId) {
        send(eventId, "ALL_SIGNERS_COMPLETED", Map.of());
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

    private void send(Long eventId, String type, Map<String, Object> payload) {
        RealtimeEventDto event = new RealtimeEventDto(type, eventId, LocalDateTime.now(), payload);
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/state", event);
    }
}
