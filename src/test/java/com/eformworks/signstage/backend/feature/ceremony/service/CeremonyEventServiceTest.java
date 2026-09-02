package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeatureCode;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.StrokeDataRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyEventService}의 문서 매핑/READY 전이 검증 단위 테스트. {@code ceremonyService}는
 * 조직/행사 접근 검사를 담당하는 협력 객체라 통째로 목(mock) 처리하고, 이 클래스가 실제로 갖는
 * 검증 로직(문서 COMPLETED 여부, CONTRACT/EXHIBITION 서명자 매핑 일치)만 검사한다.
 */
@ExtendWith(MockitoExtension.class)
class CeremonyEventServiceTest {

    @Mock
    private CeremonyEventRepository ceremonyEventRepository;
    @Mock
    private CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    @Mock
    private OptionalFeatureRepository optionalFeatureRepository;
    @Mock
    private CeremonyTemplateRepository ceremonyTemplateRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateFieldRepository templateFieldRepository;
    @Mock
    private CeremonyEventLogRepository ceremonyEventLogRepository;
    @Mock
    private StrokeDataRepository strokeDataRepository;
    @Mock
    private SignerRepository signerRepository;
    @Mock
    private CeremonyRealtimeNotifier ceremonyRealtimeNotifier;
    @Mock
    private CeremonyService ceremonyService;

    @InjectMocks
    private CeremonyEventService eventService;

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long CEREMONY_ID = 10L;
    private static final Long EVENT_ID = 100L;
    private static final Long CURRENT_USER_ID = 1L;

    private Ceremony ceremony(Long id) {
        Ceremony ceremony = Ceremony.builder().title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", id);
        return ceremony;
    }

    private CeremonyEvent event(Long id, Ceremony ceremony) {
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony)
                .name("하위 행사")
                .eventType(CeremonyEventType.MAIN)
                .accessKey("access-key")
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Template template(Long id, Ceremony ceremony, TemplateDocumentRole role) {
        Template template = Template.builder()
                .ceremony(ceremony)
                .title("문서")
                .documentRole(role)
                .storageKey("templates/" + id)
                .originalFilename("doc.pdf")
                .storedFilename("doc.pdf")
                .build();
        ReflectionTestUtils.setField(template, "id", id);
        return template;
    }

    private Signer signer(Long id, Ceremony ceremony, String name) {
        Signer signer = Signer.builder().ceremony(ceremony).name(name).accessKey("signer-" + id).build();
        ReflectionTestUtils.setField(signer, "id", id);
        return signer;
    }

    private OptionalFeature optionalFeature(Long id, String name, String exclusivityGroup) {
        OptionalFeature feature = OptionalFeature.builder()
                .code(OptionalFeatureCode.SIGNER_FIELD_ZOOM)
                .name(name)
                .supplyPrice(new BigDecimal("10000"))
                .salePrice(new BigDecimal("10000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ZERO)
                .exclusivityGroup(exclusivityGroup)
                .build();
        ReflectionTestUtils.setField(feature, "id", id);
        return feature;
    }

    private void stubAccess(Ceremony ceremony) {
        given(ceremonyService.findCeremonyInOrganizationOrThrow(ORGANIZATION_ID, CEREMONY_ID)).willReturn(ceremony);
        given(ceremonyService.findActiveMemberOrThrow(ORGANIZATION_ID, CURRENT_USER_ID))
                .willReturn(Member.builder().role(MemberRole.OWNER).build());
    }

    @Test
    @DisplayName("서명란 배치가 완료(COMPLETED)되지 않은 문서는 행사 문서매핑에 저장할 수 없다")
    void mapTemplate_incompleteTemplate_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        Template draftTemplate = template(101L, ceremony, TemplateDocumentRole.CONTRACT); // 기본 상태 DRAFT, complete() 호출 안 함

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(templateRepository.findById(101L)).willReturn(Optional.of(draftTemplate));

        CeremonyEventDto.Request.MapTemplate request =
                new CeremonyEventDto.Request.MapTemplate(101L, "CONTRACT");

        // when & then
        assertThatThrownBy(() -> eventService.mapTemplate(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.TEMPLATE_NOT_COMPLETED);
        verify(ceremonyTemplateRepository, never()).save(any(CeremonyTemplate.class));
    }

    @Test
    @DisplayName("서명란 배치가 완료(COMPLETED)된 문서는 행사 문서매핑에 저장할 수 있다")
    void mapTemplate_completedTemplate_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        Template completedTemplate = template(101L, ceremony, TemplateDocumentRole.CONTRACT);
        completedTemplate.complete();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(templateRepository.findById(101L)).willReturn(Optional.of(completedTemplate));
        given(ceremonyTemplateRepository.existsByCeremonyEventIdAndTemplateId(EVENT_ID, 101L)).willReturn(false);

        CeremonyEventDto.Request.MapTemplate request =
                new CeremonyEventDto.Request.MapTemplate(101L, "CONTRACT");

        // when
        eventService.mapTemplate(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request);

        // then
        verify(ceremonyTemplateRepository).save(any(CeremonyTemplate.class));
    }

    @Test
    @DisplayName("같은 배타 그룹(exclusivityGroup)의 선택옵션을 동시에 적용하려 하면 거부된다")
    void updateOptionalFeatures_conflictingExclusivityGroup_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        OptionalFeature blueHighlight = optionalFeature(201L, "파란 하이라이트", "SIGNER_HIGHLIGHT_COLOR");
        OptionalFeature redHighlight = optionalFeature(202L, "빨간 하이라이트", "SIGNER_HIGHLIGHT_COLOR");

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyService.retrievePurchasedOptionalFeatureIds(ceremony)).willReturn(List.of(201L, 202L));
        given(optionalFeatureRepository.findAllByIdIn(List.of(201L, 202L))).willReturn(List.of(blueHighlight, redHighlight));

        CeremonyEventDto.Request.UpdateOptionalFeatures request =
                new CeremonyEventDto.Request.UpdateOptionalFeatures(List.of(201L, 202L));

        // when & then
        assertThatThrownBy(() -> eventService.updateOptionalFeatures(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.OPTIONAL_FEATURE_GROUP_CONFLICT);
        verify(ceremonyEventOptionalFeatureRepository, never()).save(any(CeremonyEventOptionalFeature.class));
    }

    @Test
    @DisplayName("배타 그룹이 없거나(null) 서로 다른 선택옵션은 동시에 적용할 수 있다")
    void updateOptionalFeatures_noGroupConflict_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        OptionalFeature signerFieldZoom = optionalFeature(201L, "서명 하이라이트", null);
        OptionalFeature fireworks = optionalFeature(203L, "폭죽", null);

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyService.retrievePurchasedOptionalFeatureIds(ceremony)).willReturn(List.of(201L, 203L));
        given(optionalFeatureRepository.findAllByIdIn(List.of(201L, 203L))).willReturn(List.of(signerFieldZoom, fireworks));

        CeremonyEventDto.Request.UpdateOptionalFeatures request =
                new CeremonyEventDto.Request.UpdateOptionalFeatures(List.of(201L, 203L));

        // when
        eventService.updateOptionalFeatures(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID, request);

        // then
        verify(ceremonyEventOptionalFeatureRepository, times(2)).save(any(CeremonyEventOptionalFeature.class));
    }

    /**
     * 레거시 CeremonyEventMappingConsistencyTest가 전시용(EXHIBITION) 문서에 서명자를 하나도
     * 배정하지 않은 채로 뒀던 문제를 고친 데이터 — 계약서/전시용 양쪽 모두에 필수 서명란을
     * 배정해야 "두 집합이 같은가"를 실제로 검사하는 의미가 생긴다(한쪽이 비어 있으면
     * EVENT_REQUIRED_FIELD_UNASSIGNED만 검사하고 끝나 버려 서명자 매핑 일치 검증 자체가
     * 실행되지 않는다).
     */
    @Test
    @DisplayName("계약서/전시용 문서의 필수 서명자 구성이 일치하면 READY로 전이된다")
    void transitionToReady_signerMappingConsistent_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        Signer signer = signer(1L, ceremony, "서명자1");

        Template contractTemplate = template(101L, ceremony, TemplateDocumentRole.CONTRACT);
        Template exhibitionTemplate = template(102L, ceremony, TemplateDocumentRole.EXHIBITION);

        TemplateField contractField = TemplateField.builder()
                .template(contractTemplate).signer(signer).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();
        TemplateField exhibitionField = TemplateField.builder()
                .template(exhibitionTemplate).signer(signer).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();

        CeremonyTemplate contractMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(contractTemplate).documentRole(TemplateDocumentRole.CONTRACT).build();
        CeremonyTemplate exhibitionMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(exhibitionTemplate).documentRole(TemplateDocumentRole.EXHIBITION).build();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.CONTRACT))
                .willReturn(List.of(contractMapping));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.EXHIBITION))
                .willReturn(List.of(exhibitionMapping));
        given(templateFieldRepository.findAllByTemplateId(101L)).willReturn(List.of(contractField));
        given(templateFieldRepository.findAllByTemplateId(102L)).willReturn(List.of(exhibitionField));

        // when
        CeremonyEventDto.Response.CeremonyEventSummary result =
                eventService.transitionToReady(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID);

        // then
        assertThat(result.getStatus()).isEqualTo("READY");
    }

    @Test
    @DisplayName("계약서/전시용 문서의 필수 서명자 구성이 다르면 READY 전이가 거부된다")
    void transitionToReady_signerMappingMismatch_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony);
        Signer contractSigner = signer(1L, ceremony, "서명자1");
        Signer exhibitionSigner = signer(2L, ceremony, "서명자2");

        Template contractTemplate = template(101L, ceremony, TemplateDocumentRole.CONTRACT);
        Template exhibitionTemplate = template(102L, ceremony, TemplateDocumentRole.EXHIBITION);

        TemplateField contractField = TemplateField.builder()
                .template(contractTemplate).signer(contractSigner).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();
        TemplateField exhibitionField = TemplateField.builder()
                .template(exhibitionTemplate).signer(exhibitionSigner).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();

        CeremonyTemplate contractMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(contractTemplate).documentRole(TemplateDocumentRole.CONTRACT).build();
        CeremonyTemplate exhibitionMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(exhibitionTemplate).documentRole(TemplateDocumentRole.EXHIBITION).build();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.CONTRACT))
                .willReturn(List.of(contractMapping));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.EXHIBITION))
                .willReturn(List.of(exhibitionMapping));
        given(templateFieldRepository.findAllByTemplateId(101L)).willReturn(List.of(contractField));
        given(templateFieldRepository.findAllByTemplateId(102L)).willReturn(List.of(exhibitionField));

        // when & then
        assertThatThrownBy(() -> eventService.transitionToReady(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_SIGNER_MAPPING_MISMATCH);
    }

    /**
     * 강제종료(FORCE_FINISHED, 2026-08-27 legacy 포팅) — 서명 완료 여부와 무관하게 STARTED인
     * TEST/REHEARSAL 행사만 끝낼 수 있고 MAIN은 거부한다({@link CeremonyEventService#forceFinishEvent}).
     */
    @Test
    @DisplayName("STARTED 상태의 TEST 행사는 서명 완료 여부와 무관하게 강제종료할 수 있다")
    void forceFinishEvent_startedTestEvent_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony).name("리허설").eventType(CeremonyEventType.TEST).accessKey("access-key").build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.transitionToReady();
        event.transitionToStarted();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when
        CeremonyEventDto.Response.CeremonyEventSummary result =
                eventService.forceFinishEvent(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID);

        // then
        assertThat(result.getStatus()).isEqualTo("FORCE_FINISHED");
    }

    @Test
    @DisplayName("본행사(MAIN)는 강제종료할 수 없다")
    void forceFinishEvent_mainEvent_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony); // eventType == MAIN
        event.transitionToReady();
        event.transitionToStarted();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.forceFinishEvent(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_FORCE_FINISH_NOT_ALLOWED);
    }

    @Test
    @DisplayName("진행 중(STARTED)이 아닌 행사는 강제종료할 수 없다")
    void forceFinishEvent_notStarted_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony).name("리허설").eventType(CeremonyEventType.TEST).accessKey("access-key").build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID); // DRAFT

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.forceFinishEvent(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_FORCE_FINISH_NOT_ALLOWED);
    }

    /**
     * 서명 일괄 초기화(SIGNATURE_BULK_RESET, 2026-09-02 legacy 포팅) — STARTED인 TEST/REHEARSAL
     * 행사에서 매핑된 모든 서명자의 서명을 한 번에 초기화한다({@link CeremonyEventService#resetAllSignatures}).
     */
    @Test
    @DisplayName("STARTED 상태의 리허설 행사는 매핑된 모든 서명자의 서명을 일괄 초기화할 수 있다")
    void resetAllSignatures_startedRehearsalEvent_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony).name("리허설").eventType(CeremonyEventType.REHEARSAL).accessKey("access-key").build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.transitionToReady();
        event.transitionToStarted();

        Signer signer = signer(1L, ceremony, "서명자1");
        Template contractTemplate = template(101L, ceremony, TemplateDocumentRole.CONTRACT);
        Template exhibitionTemplate = template(102L, ceremony, TemplateDocumentRole.EXHIBITION);
        TemplateField contractField = TemplateField.builder()
                .template(contractTemplate).signer(signer).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();
        TemplateField exhibitionField = TemplateField.builder()
                .template(exhibitionTemplate).signer(signer).fieldKey("f1")
                .pageIndex(0).fieldIndex(0).fieldName("서명").isRequired(true).build();
        CeremonyTemplate contractMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(contractTemplate).documentRole(TemplateDocumentRole.CONTRACT).build();
        CeremonyTemplate exhibitionMapping = CeremonyTemplate.builder()
                .ceremonyEvent(event).template(exhibitionTemplate).documentRole(TemplateDocumentRole.EXHIBITION).build();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.CONTRACT))
                .willReturn(List.of(contractMapping));
        given(ceremonyTemplateRepository.findAllByCeremonyEventIdAndDocumentRole(EVENT_ID, TemplateDocumentRole.EXHIBITION))
                .willReturn(List.of(exhibitionMapping));
        given(templateFieldRepository.findAllByTemplateId(101L)).willReturn(List.of(contractField));
        given(templateFieldRepository.findAllByTemplateId(102L)).willReturn(List.of(exhibitionField));
        given(signerRepository.findById(1L)).willReturn(Optional.of(signer));

        // when
        eventService.resetAllSignatures(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID);

        // then
        verify(strokeDataRepository).deleteAllByCeremonyEventIdAndSignerId(EVENT_ID, 1L);
        verify(ceremonyRealtimeNotifier).notifySignatureReplaced(EVENT_ID, 1L, "서명자1");
    }

    @Test
    @DisplayName("본행사(MAIN)는 서명을 일괄 초기화할 수 없다")
    void resetAllSignatures_mainEvent_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = event(EVENT_ID, ceremony); // eventType == MAIN
        event.transitionToReady();
        event.transitionToStarted();

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.resetAllSignatures(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_BULK_RESET_NOT_ALLOWED);
        verify(strokeDataRepository, never()).deleteAllByCeremonyEventIdAndSignerId(any(), any());
    }

    @Test
    @DisplayName("진행 중(STARTED)이 아닌 행사는 서명을 일괄 초기화할 수 없다")
    void resetAllSignatures_notStarted_fail() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony).name("리허설").eventType(CeremonyEventType.REHEARSAL).accessKey("access-key").build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID); // DRAFT

        stubAccess(ceremony);
        given(ceremonyEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.resetAllSignatures(ORGANIZATION_ID, CEREMONY_ID, EVENT_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.EVENT_BULK_RESET_NOT_ALLOWED);
        verify(strokeDataRepository, never()).deleteAllByCeremonyEventIdAndSignerId(any(), any());
    }

    @Test
    @DisplayName("하위 행사 표시 순서를 일괄 변경하면 그 순서대로 목록이 정렬돼 돌아온다")
    void updateEventDisplayOrders_success() {
        // given
        Ceremony ceremony = ceremony(CEREMONY_ID);
        CeremonyEvent first = event(101L, ceremony);
        CeremonyEvent second = event(102L, ceremony);

        stubAccess(ceremony);
        given(ceremonyEventRepository.findAllById(List.of(101L, 102L))).willReturn(List.of(first, second));
        given(ceremonyEventRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(CEREMONY_ID))
                .willReturn(List.of(second, first));

        com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest.UpdateDisplayOrders request =
                new com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest.UpdateDisplayOrders(List.of(
                        new com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest.Item(101L, 1),
                        new com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest.Item(102L, 0)
                ));

        // when
        List<CeremonyEventDto.Response.CeremonyEventSummary> result =
                eventService.updateEventDisplayOrders(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, request);

        // then
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(0);
        assertThat(result).extracting(CeremonyEventDto.Response.CeremonyEventSummary::getId)
                .containsExactly(102L, 101L);
    }
}
