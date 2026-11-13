package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyResultRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CeremonyResultServiceTest {
    @Mock CeremonyResultRepository ceremonyResultRepository;
    @Mock CeremonyEventRepository ceremonyEventRepository;
    @Mock CeremonyTemplateRepository ceremonyTemplateRepository;
    @Mock TemplateFieldRepository templateFieldRepository;
    @Mock StrokeDataRepository strokeDataRepository;
    @Mock CeremonyEventLogRepository ceremonyEventLogRepository;
    @Mock DocumentStoragePort documentStoragePort;
    @Mock CeremonyAccessService ceremonyAccessService;
    @Mock CeremonyEventService ceremonyEventService;
    @InjectMocks CeremonyResultService resultService;

    private CeremonyEvent stubEvent(CeremonyEventType type, CeremonyEventStatus status) {
        Ceremony ceremony = Ceremony.builder().build();
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony).name("행사").eventType(type).accessKey("key").build();
        ReflectionTestUtils.setField(event, "id", 3L);
        ReflectionTestUtils.setField(event, "status", status);
        given(ceremonyAccessService.findCeremonyInOrganizationOrThrow(1L, 2L)).willReturn(ceremony);
        given(ceremonyEventService.findEventInCeremonyOrThrow(2L, 3L)).willReturn(event);
        return event;
    }

    @ParameterizedTest
    @CsvSource({"TEST,FINISHED", "REHEARSAL,FINISHED", "MAIN,FINISHED", "TEST,FORCE_FINISHED", "REHEARSAL,FORCE_FINISHED", "MAIN,FORCE_FINISHED"})
    void generateResults_acceptsBothTerminalStatuses(CeremonyEventType type, CeremonyEventStatus status) {
        CeremonyEvent event = stubEvent(type, status);
        resultService.generateResults(1L, 2L, 3L, 4L);
        verify(strokeDataRepository).findAllByCeremonyEventId(3L);
        verify(ceremonyTemplateRepository).findAllByCeremonyEventId(3L);
        verify(ceremonyEventLogRepository).save(argThat(log ->
                log.getEventAction() == CeremonyEventAction.GENERATE_RESULTS));
        assertThat(event.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = CeremonyEventStatus.class, names = {"DRAFT", "READY", "STARTED"})
    void generateResults_rejectsUnfinishedEvents(CeremonyEventStatus status) {
        stubEvent(CeremonyEventType.MAIN, status);
        assertThatThrownBy(() -> resultService.generateResults(1L, 2L, 3L, 4L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_NOT_FINISHED);
        verifyNoInteractions(strokeDataRepository, ceremonyTemplateRepository, documentStoragePort);
    }

    @ParameterizedTest
    @EnumSource(value = CeremonyEventStatus.class, names = {"FINISHED", "FORCE_FINISHED"})
    void generateResults_stillRejectsDuplicateGeneration(CeremonyEventStatus status) {
        stubEvent(CeremonyEventType.MAIN, status);
        given(ceremonyResultRepository.existsByCeremonyEventId(3L)).willReturn(true);
        assertThatThrownBy(() -> resultService.generateResults(1L, 2L, 3L, 4L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.RESULTS_ALREADY_GENERATED);
        verifyNoInteractions(documentStoragePort);
    }

    /** 강제종료한 본행사도 PDF를 만들면 끝난 것으로 보고 행사 전체를 완료로 바꾼다(2026-11-13 사용자 결정). */
    @ParameterizedTest
    @EnumSource(value = CeremonyEventStatus.class, names = {"FINISHED", "FORCE_FINISHED"})
    void generateResults_completesCeremonyWhenAllMainEventsEndedWithResults(CeremonyEventStatus otherMainStatus) {
        CeremonyEvent event = stubEvent(CeremonyEventType.MAIN, CeremonyEventStatus.FORCE_FINISHED);
        Ceremony ceremony = event.getCeremony();
        ReflectionTestUtils.setField(ceremony, "id", 2L);
        ReflectionTestUtils.setField(ceremony, "status", CeremonyStatus.IN_PROGRESS);
        CeremonyEvent otherMain = CeremonyEvent.builder()
                .ceremony(ceremony).name("본행사 2").eventType(CeremonyEventType.MAIN).accessKey("key2").build();
        ReflectionTestUtils.setField(otherMain, "id", 5L);
        ReflectionTestUtils.setField(otherMain, "status", otherMainStatus);
        given(ceremonyEventRepository.findAllByCeremonyIdAndEventType(2L, CeremonyEventType.MAIN))
                .willReturn(List.of(event, otherMain));
        given(ceremonyResultRepository.existsByCeremonyEventId(3L)).willReturn(false);
        given(ceremonyResultRepository.existsByCeremonyEventId(5L)).willReturn(true);

        resultService.generateResults(1L, 2L, 3L, 4L);

        assertThat(ceremony.getStatus()).isEqualTo(CeremonyStatus.COMPLETED);
    }

    @Test
    void generateResults_keepsCeremonyInProgressWhileAnotherMainEventIsStillRunning() {
        CeremonyEvent event = stubEvent(CeremonyEventType.MAIN, CeremonyEventStatus.FORCE_FINISHED);
        Ceremony ceremony = event.getCeremony();
        ReflectionTestUtils.setField(ceremony, "id", 2L);
        ReflectionTestUtils.setField(ceremony, "status", CeremonyStatus.IN_PROGRESS);
        CeremonyEvent otherMain = CeremonyEvent.builder()
                .ceremony(ceremony).name("본행사 2").eventType(CeremonyEventType.MAIN).accessKey("key2").build();
        ReflectionTestUtils.setField(otherMain, "id", 5L);
        ReflectionTestUtils.setField(otherMain, "status", CeremonyEventStatus.STARTED);
        given(ceremonyEventRepository.findAllByCeremonyIdAndEventType(2L, CeremonyEventType.MAIN))
                .willReturn(List.of(event, otherMain));

        resultService.generateResults(1L, 2L, 3L, 4L);

        assertThat(ceremony.getStatus()).isEqualTo(CeremonyStatus.IN_PROGRESS);
    }
}
