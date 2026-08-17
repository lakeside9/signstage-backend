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
 * {@code @MessageMapping} 핸들러 없이 서버가 클라이언트로만 쏜다. 레거시 정책대로
 * 진행 중인 펜 스트로크는 보내지 않고 "확정"된 사건(상태 전이, 서명 완료)만 보낸다
 * (signstage-docs business/ceremony-feature-migration-review.md 2.4절).
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

    private void send(Long eventId, String type, Map<String, Object> payload) {
        RealtimeEventDto event = new RealtimeEventDto(type, eventId, LocalDateTime.now(), payload);
        messagingTemplate.convertAndSend("/topic/events/" + eventId + "/state", event);
    }
}
