package com.eformworks.signstage.backend.feature.ceremony.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEventSignerState} 상태 전이 단위 테스트 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-STATE-03.
 */
class CeremonyEventSignerStateTest {

    private CeremonyEvent event() {
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(Ceremony.builder().title("행사").build())
                .name("하위 행사")
                .eventType(CeremonyEventType.MAIN)
                .accessKey("access-key")
                .build();
        ReflectionTestUtils.setField(event, "id", 1L);
        return event;
    }

    private Signer signer() {
        Signer signer = Signer.builder().name("서명자").accessKey("signer-key").build();
        ReflectionTestUtils.setField(signer, "id", 10L);
        return signer;
    }

    @Test
    @DisplayName("생성 직후 기본 상태는 PENDING이고 완료 관련 값이 비어 있다")
    void newState_defaultsToPending() {
        CeremonyEventSignerState state = CeremonyEventSignerState.builder().event(event()).signer(signer()).build();

        assertThat(state.getSignatureStatus()).isEqualTo(SignatureStatus.PENDING);
        assertThat(state.getLastCompletionLogId()).isNull();
        assertThat(state.getCompletedAt()).isNull();
        assertThat(state.getId().getEventId()).isEqualTo(1L);
        assertThat(state.getId().getSignerId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("완료→지움→재서명→완료 순서로 상태가 정확히 전이된다")
    void completeClearResignComplete_transitionsCorrectly() {
        CeremonyEventSignerState state = CeremonyEventSignerState.builder().event(event()).signer(signer()).build();

        state.markCompleted(100L);
        assertThat(state.getSignatureStatus()).isEqualTo(SignatureStatus.COMPLETED);
        assertThat(state.getLastCompletionLogId()).isEqualTo(100L);
        assertThat(state.getCompletedAt()).isNotNull();

        state.markPending(); // SIGNATURE_CLEAR
        assertThat(state.getSignatureStatus()).isEqualTo(SignatureStatus.PENDING);
        assertThat(state.getLastCompletionLogId()).isNull();
        assertThat(state.getCompletedAt()).isNull();

        state.markCompleted(200L); // 재서명 후 완료 재요청
        assertThat(state.getSignatureStatus()).isEqualTo(SignatureStatus.COMPLETED);
        assertThat(state.getLastCompletionLogId()).isEqualTo(200L);
        assertThat(state.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("완료 후 관리자가 SIGNING으로 초기화하면 완료 관련 값이 비워진다")
    void markSigning_afterCompleted_clearsCompletionFields() {
        CeremonyEventSignerState state = CeremonyEventSignerState.builder().event(event()).signer(signer()).build();
        state.markCompleted(100L);

        state.markSigning();

        assertThat(state.getSignatureStatus()).isEqualTo(SignatureStatus.SIGNING);
        assertThat(state.getLastCompletionLogId()).isNull();
        assertThat(state.getCompletedAt()).isNull();
    }
}
