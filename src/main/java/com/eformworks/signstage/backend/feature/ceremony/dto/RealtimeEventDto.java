package com.eformworks.signstage.backend.feature.ceremony.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code /topic/events/{eventId}/state}로 보내는 WebSocket 메시지 봉투. REST DTO의
 * Request/Response 중첩 구조(backend-coding-convention.md 6장)는 API 응답 전용이라
 * WebSocket 브로드캐스트 페이로드에는 적용하지 않는다.
 *
 * <p>이번 라운드에서 쓰는 {@code type} 값: {@code EVENT_STATUS_CHANGED}, {@code SIGNATURE_COMPLETED}.
 */
@Getter
@AllArgsConstructor
public class RealtimeEventDto {

    private final String type;
    private final Long eventId;
    private final LocalDateTime occurredAt;
    private final Map<String, Object> payload;
}
