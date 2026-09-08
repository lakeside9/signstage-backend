package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventSignerState;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.SignatureStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventSignerStateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEventSignerStateService} 단위 테스트 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-STATE-03.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyEventSignerStateServiceTest {

    private static final Long EVENT_ID = 1L;
    private static final Long SIGNER_ID = 10L;

    @Mock
    private CeremonyEventSignerStateRepository ceremonyEventSignerStateRepository;

    @InjectMocks
    private CeremonyEventSignerStateService service;

    private CeremonyEvent event() {
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(Ceremony.builder().title("행사").build())
                .name("하위 행사")
                .eventType(CeremonyEventType.MAIN)
                .accessKey("access-key")
                .build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private Signer signer() {
        Signer signer = Signer.builder().name("서명자").accessKey("signer-key").build();
        ReflectionTestUtils.setField(signer, "id", SIGNER_ID);
        return signer;
    }

    @Test
    @DisplayName("상태 행이 없으면 새로 만들어 완료 처리한다")
    void markCompleted_newState_createsAndCompletes() {
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.empty());
        given(ceremonyEventSignerStateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.markCompleted(event(), signer(), 100L);

        verify(ceremonyEventSignerStateRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("같은 서명자가 반복 완료해도 새 행을 만들지 않는다(이미 있으면 그 행을 갱신)")
    void markCompleted_existingState_doesNotCreateDuplicateRow() {
        CeremonyEventSignerState existing = CeremonyEventSignerState.builder().event(event()).signer(signer()).build();
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.of(existing));

        service.markCompleted(event(), signer(), 100L);
        service.markCompleted(event(), signer(), 200L); // 반복 완료 요청

        verify(ceremonyEventSignerStateRepository, never()).save(any());
        assertThat(existing.getSignatureStatus()).isEqualTo(SignatureStatus.COMPLETED);
        assertThat(existing.getLastCompletionLogId()).isEqualTo(200L); // 마지막 값으로 갱신됨
    }

    @Test
    @DisplayName("동시에 다른 트랜잭션이 먼저 행을 만들었으면(unique 위반) 그 행을 다시 읽어 갱신한다")
    void markCompleted_concurrentInsertRace_recoversByReReading() {
        CeremonyEventSignerState createdByOtherTransaction =
                CeremonyEventSignerState.builder().event(event()).signer(signer()).build();
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(createdByOtherTransaction));
        given(ceremonyEventSignerStateRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        service.markCompleted(event(), signer(), 100L);

        assertThat(createdByOtherTransaction.getSignatureStatus()).isEqualTo(SignatureStatus.COMPLETED);
        assertThat(createdByOtherTransaction.getLastCompletionLogId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("일괄 초기화는 대상 서명자 전원을 PENDING으로 되돌린다")
    void markAllPending_setsAllToPending() {
        Signer signerA = signer();
        Signer signerB = Signer.builder().name("서명자B").accessKey("signer-key-b").build();
        ReflectionTestUtils.setField(signerB, "id", 20L);

        CeremonyEventSignerState stateA = CeremonyEventSignerState.builder().event(event()).signer(signerA).build();
        stateA.markCompleted(1L);
        CeremonyEventSignerState stateB = CeremonyEventSignerState.builder().event(event()).signer(signerB).build();
        stateB.markCompleted(2L);

        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.of(stateA));
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, 20L))
                .willReturn(Optional.of(stateB));

        service.markAllPending(event(), List.of(signerA, signerB));

        assertThat(stateA.getSignatureStatus()).isEqualTo(SignatureStatus.PENDING);
        assertThat(stateB.getSignatureStatus()).isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    @DisplayName("상태 행이 아예 없는 서명자는 완료로 보지 않는다")
    void isSignerComplete_noRow_returnsFalse() {
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.empty());

        assertThat(service.isSignerComplete(EVENT_ID, SIGNER_ID)).isFalse();
    }

    @Test
    @DisplayName("필수 서명자 중 한 명이라도 미완료면 전원 완료가 아니다")
    void isAllComplete_oneIncomplete_returnsFalse() {
        CeremonyEventSignerState completed = CeremonyEventSignerState.builder().event(event()).signer(signer()).build();
        completed.markCompleted(1L);
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, SIGNER_ID))
                .willReturn(Optional.of(completed));
        given(ceremonyEventSignerStateRepository.findByIdEventIdAndIdSignerId(EVENT_ID, 20L))
                .willReturn(Optional.empty());

        assertThat(service.isAllComplete(EVENT_ID, List.of(SIGNER_ID, 20L))).isFalse();
    }
}
