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
 * <p>기존 대문자 "사실" 이벤트({@code EVENT_STATUS_CHANGED}, {@code SIGNATURE_COMPLETED},
 * {@code SIGNATURE_CLEARED}, {@code SIGNATURE_REPLACED}, {@code SIGNATURE_STROKE_SUBMITTED},
 * {@code ALL_SIGNERS_COMPLETED} — 구 frontend 호환용, signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md PRE-04 참고)와, 신규 점(dot) 표기
 * 효과 이벤트({@code ceremony.effect.requested}, {@code ceremony.effect.setting.changed})를
 * 같은 봉투·같은 토픽으로 함께 보낸다 — 별도 adapter 클래스를 두지 않고
 * {@link com.eformworks.signstage.backend.feature.ceremony.service.CeremonyRealtimeNotifier}가
 * 발행 시점에 두 계열을 나란히 쏘는 것 자체가 호환 정책이다(BE-RUNTIME-01).
 */
@Getter
@AllArgsConstructor
public class RealtimeEventDto {

    private final String type;
    private final Long eventId;
    private final LocalDateTime occurredAt;
    private final Map<String, Object> payload;

    /**
     * 단조 증가 순번 — 효과 요청/runtime 변경/서명 완료는 그 사건의 감사 로그 id를 그대로
     * 쓴다(같은 이벤트 안에서 append-only로 늘어나는 id라 단조 증가가 보장된다). 그 외 기존
     * "사실" 이벤트는 전환 기간 동안 {@code null}을 허용한다.
     */
    private final Long version;
}
