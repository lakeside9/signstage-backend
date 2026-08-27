package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventLogDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.ceremony.dto.StrokeDataDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.ActorType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventAction;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventLog;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyTemplate;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.StrokeData;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하위 행사(CeremonyEvent, TEST/MAIN). signstage-docs
 * business/ceremony-feature-migration-review.md 2.2절(라이프사이클), business/
 * ceremony-billing-options-review.md 4.5절(한도 하드 블록)/4.11절(구매·적용 분리) 참고.
 *
 * <p>조직/행사 접근 검사와 유효 한도 계산은 {@link CeremonyService}의 package-private
 * 헬퍼를 그대로 재사용한다 — 같은 패키지 내 서비스 간 공유는 기존
 * {@code PlatformAdminOrganizationService.saveOrganizationWithOwner}와 같은 관례다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyEventService {

    private final CeremonyEventRepository ceremonyEventRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
    private final TemplateRepository templateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final CeremonyEventLogRepository ceremonyEventLogRepository;
    private final StrokeDataRepository strokeDataRepository;
    private final SignerRepository signerRepository;
    private final CeremonyRealtimeNotifier ceremonyRealtimeNotifier;
    private final CeremonyService ceremonyService;

    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary createCeremonyEvent(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            CeremonyEventDto.Request.CreateCeremonyEvent request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);

        CeremonyEventType eventType = parseEventType(request.getEventType());
        // REHEARSAL은 별도 카탈로그 한도를 새로 만들지 않고 TEST와 같은 용량 버킷을 공유한다
        // (2026-08-27 legacy 포팅 시 판단 — signstage-docs business/ceremony-feature-migration-review.md
        // 최신 라운드 참고. 리허설도 "정식 본행사가 아닌" 연습성 하위 행사라는 점에서 TEST에
        // 가깝다고 봤다).
        CapacityType capacityType = eventType == CeremonyEventType.MAIN
                ? CapacityType.MAIN_EVENTS
                : CapacityType.TEST_EVENTS;
        Set<CeremonyEventType> countedEventTypes = eventType == CeremonyEventType.MAIN
                ? Set.of(CeremonyEventType.MAIN)
                : Set.of(CeremonyEventType.TEST, CeremonyEventType.REHEARSAL);

        // 한도 하드 블록(4.5절) — 유효 한도 = 플랜 기본값 + Σ 추가구매.
        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, capacityType);
        long typeCount = ceremonyEventRepository.countByCeremonyIdAndEventTypeIn(ceremonyId, countedEventTypes);
        if (typeCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_LIMIT_EXCEEDED);
        }

        // 표시 순서는 구분(TEST/REHEARSAL/MAIN)과 무관하게 이 Ceremony의 하위 행사 전체가 하나의
        // 순서를 공유한다 — 새로 등록되는 행사는 항상 목록 맨 끝에 붙는다.
        long totalCount = ceremonyEventRepository.countByCeremonyId(ceremonyId);

        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony)
                .name(request.getName())
                .eventType(eventType)
                .venue(request.getVenue())
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .accessKey(generateUniqueAccessKey())
                .description(request.getDescription())
                .displayOrder((int) totalCount)
                .build();
        ceremonyEventRepository.save(event);

        // 등록 화면에서도 적용 선택옵션을 바로 켤 수 있게 한다 — null이면(요청에 필드 자체가
        // 없으면) 예전처럼 아무것도 적용하지 않는다.
        List<Long> appliedIds = request.getOptionalFeatureIds() == null
                ? List.of()
                : applyOptionalFeatures(ceremony, event, request.getOptionalFeatureIds());

        return toSummary(event, appliedIds);
    }

    public List<CeremonyEventDto.Response.CeremonyEventSummary> findCeremonyEvents(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyEventRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(ceremonyId).stream()
                .map(event -> toSummary(event, retrieveAppliedOptionalFeatureIds(event)))
                .toList();
    }

    /**
     * 하위 행사 목록의 위/아래 이동 버튼이 호출한다 — 전체 배열을 원하는 순서로 다시 인덱싱해
     * 통째로 보낸다. {@link #checkEventNotLocked}와 달리 STARTED/FINISHED/FORCE_FINISHED
     * 여부와 무관하게 항상 허용한다 — 표시 순서는 화면 정리일 뿐 행사 진행 상태와 무관해서다.
     */
    @Transactional
    public List<CeremonyEventDto.Response.CeremonyEventSummary> updateEventDisplayOrders(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Map<Long, CeremonyEvent> eventsById = ceremonyEventRepository
                .findAllById(request.getItems().stream().map(DisplayOrderRequest.Item::getId).toList())
                .stream()
                .collect(Collectors.toMap(CeremonyEvent::getId, Function.identity()));

        for (DisplayOrderRequest.Item item : request.getItems()) {
            CeremonyEvent event = eventsById.get(item.getId());
            if (event == null || !event.getCeremony().getId().equals(ceremonyId)) {
                throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND);
            }
            event.updateDisplayOrder(item.getDisplayOrder());
        }

        return findCeremonyEvents(organizationId, ceremonyId, currentUserId);
    }

    public CeremonyEventDto.Response.CeremonyEventSummary retrieveCeremonyEvent(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        return toSummary(event, retrieveAppliedOptionalFeatureIds(event));
    }

    /**
     * 이름/장소/일정/설명만 바꾼다. {@code STARTED}/{@code FINISHED}는 잠긴 상태라 바꿀 수 없다
     * (문서 매핑과 같은 규칙 — {@link #checkEventNotLocked}).
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary updateCeremonyEvent(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId,
            CeremonyEventDto.Request.UpdateCeremonyEvent request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        event.updateInfo(
                request.getName(), request.getVenue(), request.getScheduledStartAt(), request.getScheduledEndAt(),
                request.getDescription()
        );

        // 수정 화면에서도 적용 선택옵션을 바꿀 수 있게 한다 — null이면(요청에 필드 자체가
        // 없으면) 기존 적용 목록을 그대로 둔다. 빈 리스트를 명시적으로 보내면 전부 해제한다.
        List<Long> optionalFeatureIds = request.getOptionalFeatureIds() == null
                ? retrieveAppliedOptionalFeatureIds(event)
                : applyOptionalFeatures(ceremony, event, request.getOptionalFeatureIds());

        return toSummary(event, optionalFeatureIds);
    }

    /**
     * {@code STARTED}/{@code FINISHED}는 잠긴 상태라 삭제할 수 없다 — 서명이 실제로 진행됐거나
     * 끝난 이벤트를 지우면 결과물/로그와 정합이 깨진다. {@code DRAFT}/{@code READY}는 아직
     * 서명이 시작되지 않아 매핑된 문서(ceremony_templates)/적용 선택옵션
     * (ceremony_event_optional_features)만 정리하면 된다.
     */
    @Transactional
    public void deleteCeremonyEvent(Long organizationId, Long ceremonyId, Long eventId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        ceremonyTemplateRepository.deleteAllByCeremonyEventId(eventId);
        ceremonyEventOptionalFeatureRepository.deleteAllByCeremonyEventId(eventId);
        ceremonyEventRepository.delete(event);
    }

    /**
     * 이벤트에 적용할 선택옵션을 전체 교체한다. 요청 목록은 그 Ceremony가 "구매한" 집합의
     * 부분집합이어야 한다(4.11절) — 아니면 {@code OPTIONAL_FEATURE_NOT_PURCHASED}.
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary updateOptionalFeatures(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId,
            CeremonyEventDto.Request.UpdateOptionalFeatures request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);

        List<Long> requestedIds = request.getOptionalFeatureIds() == null ? List.of() : request.getOptionalFeatureIds();
        List<Long> appliedIds = applyOptionalFeatures(ceremony, event, requestedIds);

        return toSummary(event, appliedIds);
    }

    /**
     * 이벤트에 적용할 선택옵션을 전체 교체한다. 요청 목록은 그 Ceremony가 "구매한"(플랜 포함분+
     * 승인된 추가구매) 집합의 부분집합이어야 한다(4.11절) — 아니면
     * {@code OPTIONAL_FEATURE_NOT_PURCHASED}. 등록/수정/적용옵션 교체 세 경로가 전부 이
     * 검증·저장 로직을 공유한다.
     */
    private List<Long> applyOptionalFeatures(Ceremony ceremony, CeremonyEvent event, List<Long> requestedIds) {
        List<Long> purchasedIds = ceremonyService.retrievePurchasedOptionalFeatureIds(ceremony);
        if (!purchasedIds.containsAll(requestedIds)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_PURCHASED);
        }

        List<OptionalFeature> features = requestedIds.isEmpty()
                ? List.of()
                : optionalFeatureRepository.findAllByIdIn(requestedIds);
        checkExclusivityGroups(features);

        ceremonyEventOptionalFeatureRepository.deleteAllByCeremonyEventId(event.getId());
        for (OptionalFeature feature : features) {
            ceremonyEventOptionalFeatureRepository.save(
                    CeremonyEventOptionalFeature.builder().ceremonyEvent(event).optionalFeature(feature).build()
            );
        }

        return requestedIds;
    }

    /**
     * 같은 {@code exclusivityGroup}을 가진 선택옵션이 요청에 2개 이상 섞여 있으면 거부한다 —
     * signstage-docs business/ceremony-billing-options-review.md 참고(2026-08-21 추가: 옵션이
     * 늘어나도 코드 변경 없이 배타 관계를 카탈로그 등록만으로 구성하기 위한 필드). 그룹 없음
     * (null)은 다른 옵션과 배타 관계가 아니므로 검사 대상에서 뺀다 — 지금 있는 두 옵션(서명
     * 하이라이트/폭죽)은 전부 null이라 이 검사가 추가돼도 기존 동작은 그대로다.
     */
    private void checkExclusivityGroups(List<OptionalFeature> features) {
        Set<String> seenGroups = new HashSet<>();
        for (OptionalFeature feature : features) {
            String group = feature.getExclusivityGroup();
            if (group == null) continue;
            if (!seenGroups.add(group)) {
                throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_GROUP_CONFLICT);
            }
        }
    }

    /**
     * 문서를 이벤트에 매핑한다. {@code DRAFT}/{@code READY}일 때만 가능하다 — {@code STARTED}/
     * {@code FINISHED}는 잠긴 상태다(레거시 LOCKED_EVENT_STATUSES).
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyTemplateSummary mapTemplate(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId,
            CeremonyEventDto.Request.MapTemplate request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_IN_CEREMONY);
        }
        if (template.getStatus() != TemplateStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_COMPLETED);
        }
        if (ceremonyTemplateRepository.existsByCeremonyEventIdAndTemplateId(eventId, template.getId())) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_ALREADY_MAPPED);
        }

        CeremonyTemplate mapping = CeremonyTemplate.builder()
                .ceremonyEvent(event)
                .template(template)
                .documentRole(parseDocumentRole(request.getDocumentRole()))
                .build();
        ceremonyTemplateRepository.save(mapping);

        return toMappingSummary(mapping);
    }

    /**
     * 문서 매핑을 해제한다. {@code DRAFT}/{@code READY}일 때만 가능하다 — {@link #mapTemplate}과
     * 같은 잠금 규칙. Template 자신이나 그 서명란(TemplateField)은 건드리지 않는다 — 이
     * 매핑(CeremonyTemplate)만 지운다. 문서 교체는 프런트가 이 메서드로 기존 매핑을 지운 뒤
     * {@link #mapTemplate}으로 새 매핑을 만드는 두 단계로 한다(레거시의 통째 교체 PUT과 달리
     * REST 자원 하나씩 다루는 이 프로젝트 관례를 따른다).
     */
    @Transactional
    public void unmapTemplate(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long mappingId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        CeremonyTemplate mapping = ceremonyTemplateRepository.findById(mappingId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_MAPPING_NOT_FOUND));
        if (!mapping.getCeremonyEvent().getId().equals(eventId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_MAPPING_NOT_FOUND);
        }

        ceremonyTemplateRepository.delete(mapping);
    }

    public List<CeremonyEventDto.Response.CeremonyTemplateSummary> findMappedTemplates(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        findEventInCeremonyOrThrow(ceremonyId, eventId);
        return ceremonyTemplateRepository.findAllByCeremonyEventId(eventId).stream()
                .map(this::toMappingSummary)
                .toList();
    }

    /**
     * DRAFT→READY 전이. 레거시 {@code validateEventConfiguration()}/
     * {@code validateSignerMappingConsistency()}를 그대로 이식했다 — CONTRACT/EXHIBITION 각 1개
     * 이상 매핑, 필수 필드 전원 서명자 배정, CONTRACT/EXHIBITION 필수 서명자 구성 일치.
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary transitionToReady(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.DRAFT) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_INVALID_STATUS_TRANSITION);
        }

        validateReadyConditions(event);
        event.transitionToReady();
        ceremonyRealtimeNotifier.notifyStatusChanged(event.getId(), CeremonyEventStatus.DRAFT, CeremonyEventStatus.READY);

        return toSummary(event, retrieveAppliedOptionalFeatureIds(event));
    }

    /** READY→STARTED 전이. 레거시에 READY 상태 확인 외 추가 조건이 없다. */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary transitionToStart(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.READY) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_INVALID_STATUS_TRANSITION);
        }

        event.transitionToStarted();
        recordLog(event, ActorType.ADMIN, currentUserId, CeremonyEventAction.START_EVENT);
        ceremonyRealtimeNotifier.notifyStatusChanged(event.getId(), CeremonyEventStatus.READY, CeremonyEventStatus.STARTED);

        return toSummary(event, retrieveAppliedOptionalFeatureIds(event));
    }

    /**
     * STARTED→FINISHED 전이. 관리자가 명시적으로 호출한다(마지막 서명 완료 시 자동 전이하지
     * 않는다 — 레거시와 동일). READY 검증에서 이미 계산한 필수 서명자 집합 전원이
     * SIGNATURE_COMPLETE 로그를 가져야 한다.
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary transitionToFinish(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_INVALID_STATUS_TRANSITION);
        }

        validateFinishConditions(event);

        event.transitionToFinished();
        recordLog(event, ActorType.ADMIN, currentUserId, CeremonyEventAction.FINISH_EVENT);
        ceremonyRealtimeNotifier.notifyStatusChanged(event.getId(), CeremonyEventStatus.STARTED, CeremonyEventStatus.FINISHED);

        return toSummary(event, retrieveAppliedOptionalFeatureIds(event));
    }

    /**
     * STARTED→FORCE_FINISHED 전이(2026-08-27 legacy 포팅). 서명 완료 여부와 무관하게 관리자가
     * 강제로 끝낸다 — 리허설 도중 중단처럼 {@link #transitionToFinish}의 전원 완료 조건을 만족할
     * 필요가 없는 상황을 위한 것이다. TEST/REHEARSAL에만 허용하고 MAIN은 거부한다 — 정식
     * 본행사를 서명 미완료 상태로 끝내면 결과물/청구 근거가 어긋나기 때문이다.
     */
    @Transactional
    public CeremonyEventDto.Response.CeremonyEventSummary forceFinishEvent(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.STARTED
                || event.getEventType() == CeremonyEventType.MAIN) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_FORCE_FINISH_NOT_ALLOWED);
        }

        event.forceFinish();
        recordLog(event, ActorType.ADMIN, currentUserId, CeremonyEventAction.FORCE_FINISH_EVENT);
        ceremonyRealtimeNotifier.notifyStatusChanged(event.getId(), CeremonyEventStatus.STARTED, CeremonyEventStatus.FORCE_FINISHED);

        return toSummary(event, retrieveAppliedOptionalFeatureIds(event));
    }

    /**
     * SIGNATURE_REPLACE — 관리자가 한 서명자의 이 이벤트 서명 진행 상황 전체(배정된 모든
     * 서명란의 스트로크)를 초기화한다. STARTED 상태에서만 가능하고, 완료 여부와 무관하게
     * 항상 허용한다("다시 서명하게 하기"가 목적). 스트로크가 지워지면 배정된 모든 필드의
     * hasStroke가 자동으로 false가 되므로 completeSignature의 필수 필드 체크는 그대로 쓴다.
     */
    @Transactional
    public void replaceSignerSignature(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long signerId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        if (event.getStatus() != CeremonyEventStatus.STARTED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_NOT_IN_PROGRESS);
        }

        Signer signer = signerRepository.findById(signerId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND));
        if (!signer.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.SIGNER_NOT_FOUND);
        }

        strokeDataRepository.deleteAllByCeremonyEventIdAndSignerId(eventId, signerId);

        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(ActorType.ADMIN)
                        .actorId(currentUserId)
                        .eventAction(CeremonyEventAction.SIGNATURE_REPLACE)
                        .targetSigner(signer)
                        .message("signerId=" + signerId)
                        .build()
        );

        ceremonyRealtimeNotifier.notifySignatureReplaced(eventId, signerId, signer.getName());
    }

    public List<CeremonyEventLogDto.Response.CeremonyEventLogSummary> findEventLogs(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        findEventInCeremonyOrThrow(ceremonyId, eventId);
        return ceremonyEventLogRepository.findAllByCeremonyEventId(eventId).stream()
                .map(this::toLogSummary)
                .toList();
    }

    /**
     * 행사제어 화면이 늦게 들어와도 이미 그려진 획을 캐치업하는 용도 — 실시간 브로드캐스트
     * ({@code SIGNATURE_STROKE_SUBMITTED})는 그 시점 이후의 획만 잡아내므로, 화면 진입 시
     * 이 목록을 먼저 불러온 뒤 WebSocket으로 이어그린다.
     */
    public List<StrokeDataDto.Response.StrokeSummary> findStrokes(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        findEventInCeremonyOrThrow(ceremonyId, eventId);
        return strokeDataRepository.findAllByCeremonyEventId(eventId).stream()
                .map(this::toStrokeSummary)
                .toList();
    }

    private StrokeDataDto.Response.StrokeSummary toStrokeSummary(StrokeData stroke) {
        return new StrokeDataDto.Response.StrokeSummary(
                stroke.getId(),
                stroke.getSigner().getId(),
                stroke.getTemplateField().getId(),
                stroke.getStrokeSeq(),
                stroke.getRawData(),
                stroke.getCreatedAt()
        );
    }

    private void validateFinishConditions(CeremonyEvent event) {
        for (Long signerId : collectFinishRequiredSignerIds(event)) {
            if (!isSignerSignatureComplete(event.getId(), signerId)) {
                throw new ApplicationException(CeremonyErrorCode.EVENT_FINISH_CONDITION_NOT_MET);
            }
        }
    }

    /**
     * {@link #validateFinishConditions}와 정확히 같은 기준(필수 서명자 전원의 최신 감사 로그가
     * SIGNATURE_COMPLETE인지)으로 "지금 전원 완료 상태인가"만 boolean으로 돌려준다.
     * {@link SignerPortalService#completeSignature}가 서명 완료 처리 직후 "방금 전원 완료로
     * 전환됐는가"를 판정해 폭죽(ALL_SIGNED_FIREWORKS) 브로드캐스트 여부를 정하는 데 쓴다 —
     * 두 서비스가 인가 모델은 다르지만(4.5절), 이 계산 자체는 순수 조회라 조직 스코프 검사가
     * 없으므로 예외적으로 공유한다(package-private).
     */
    boolean isAllRequiredSignersComplete(CeremonyEvent event) {
        return collectFinishRequiredSignerIds(event).stream()
                .allMatch(signerId -> isSignerSignatureComplete(event.getId(), signerId));
    }

    /** {@code POST .../finish}가 완료를 요구하는 서명자 집합 — CONTRACT+EXHIBITION 매핑의 필수 서명란이 참조하는 signerId. */
    private Set<Long> collectFinishRequiredSignerIds(CeremonyEvent event) {
        List<CeremonyTemplate> contractMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.CONTRACT);
        List<CeremonyTemplate> exhibitionMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.EXHIBITION);

        Set<Long> requiredSignerIds = new HashSet<>(collectRequiredSignerIds(contractMappings));
        requiredSignerIds.addAll(collectRequiredSignerIds(exhibitionMappings));
        requiredSignerIds.remove(null);
        return requiredSignerIds;
    }

    /**
     * 행사제어 화면의 "서명자 모니터링"/"행사 종료" 활성화 판정용 — {@link #validateFinishConditions}가
     * 실제로 검사하는 것과 정확히 같은 기준(감사 로그의 최신 SIGNATURE_COMPLETE 여부)으로
     * 서명자별 완료 여부를 돌려준다. 화면이 스트로크 존재만으로 자체 근사 판정을 하면, 스트로크는
     * 있지만 `/complete` 호출이 실패해 감사 로그엔 완료가 안 남은 경우를 놓쳐 "화면엔 완료로
     * 보이는데 행사 종료를 누르면 거부되는" 불일치가 생긴다 — 그 근사 판정을 없애기 위한
     * 엔드포인트다.
     */
    public List<CeremonyEventDto.Response.SignerCompletionStatus> findSignatureStatus(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        return collectFinishRequiredSignerIds(event).stream()
                .map(signerId -> new CeremonyEventDto.Response.SignerCompletionStatus(
                        signerId, isSignerSignatureComplete(eventId, signerId)
                ))
                .toList();
    }

    /**
     * {@code SIGNATURE_COMPLETE}/{@code SIGNATURE_REPLACE}/{@code SIGNATURE_CLEAR} 중 이
     * 서명자의 가장 최근 로그가 {@code SIGNATURE_COMPLETE}인지로 "지금 완료 상태인가"를
     * 판정한다 — {@link SignerPortalService}의 같은 이름 메서드와 동일한 판정이지만, 두
     * 서비스가 서로 다른 인가 모델(조직 스코프 vs JWT-free 포털)이라 헬퍼를 공유하지 않는
     * 기존 관례를 따른다. CLEAR를 목록에 넣은 이유도 그쪽과 같다 — 서명자가 완료 후 다시
     * 지우고 그리는 도중엔(행사 종료 전까지 언제든 가능) 이 이벤트의 완료 조건 검사
     * ({@link #validateFinishConditions})가 "아직 완료 안 됨"으로 정확히 봐야 한다.
     */
    private boolean isSignerSignatureComplete(Long eventId, Long signerId) {
        return ceremonyEventLogRepository
                .findTopByCeremonyEventIdAndTargetSignerIdAndEventActionInOrderByCreatedAtDesc(
                        eventId,
                        signerId,
                        List.of(CeremonyEventAction.SIGNATURE_COMPLETE, CeremonyEventAction.SIGNATURE_REPLACE, CeremonyEventAction.SIGNATURE_CLEAR)
                )
                .map(log -> log.getEventAction() == CeremonyEventAction.SIGNATURE_COMPLETE)
                .orElse(false);
    }

    private void recordLog(CeremonyEvent event, ActorType actorType, Long actorId, CeremonyEventAction action) {
        ceremonyEventLogRepository.save(
                CeremonyEventLog.builder()
                        .ceremonyEvent(event)
                        .actorType(actorType)
                        .actorId(actorId)
                        .eventAction(action)
                        .build()
        );
    }

    private CeremonyEventLogDto.Response.CeremonyEventLogSummary toLogSummary(CeremonyEventLog log) {
        return new CeremonyEventLogDto.Response.CeremonyEventLogSummary(
                log.getId(),
                log.getCeremonyEvent().getId(),
                log.getActorType().name(),
                log.getActorId(),
                log.getEventAction().name(),
                log.getTargetSigner() != null ? log.getTargetSigner().getId() : null,
                log.getMessage(),
                log.getCreatedAt()
        );
    }

    private void checkEventNotLocked(CeremonyEvent event) {
        if (event.getStatus() == CeremonyEventStatus.STARTED
                || event.getStatus() == CeremonyEventStatus.FINISHED
                || event.getStatus() == CeremonyEventStatus.FORCE_FINISHED) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_LOCKED);
        }
    }

    private void validateReadyConditions(CeremonyEvent event) {
        List<CeremonyTemplate> contractMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.CONTRACT);
        List<CeremonyTemplate> exhibitionMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.EXHIBITION);
        if (contractMappings.isEmpty() || exhibitionMappings.isEmpty()) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_MISSING_DOCUMENT_ROLE);
        }

        Set<Long> contractRequiredSignerIds = collectRequiredSignerIds(contractMappings);
        Set<Long> exhibitionRequiredSignerIds = collectRequiredSignerIds(exhibitionMappings);

        if (contractRequiredSignerIds.contains(null) || exhibitionRequiredSignerIds.contains(null)) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_REQUIRED_FIELD_UNASSIGNED);
        }
        if (!contractRequiredSignerIds.equals(exhibitionRequiredSignerIds)) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_SIGNER_MAPPING_MISMATCH);
        }
    }

    /** 매핑된 템플릿들의 필수 서명란에 배정된 signerId 집합. 미배정 필드가 있으면 {@code null}을 포함한다. */
    private Set<Long> collectRequiredSignerIds(List<CeremonyTemplate> mappings) {
        Set<Long> signerIds = new HashSet<>();
        for (CeremonyTemplate mapping : mappings) {
            List<TemplateField> fields = templateFieldRepository.findAllByTemplateId(mapping.getTemplate().getId());
            for (TemplateField field : fields) {
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    signerIds.add(field.getSigner() != null ? field.getSigner().getId() : null);
                }
            }
        }
        return signerIds;
    }

    private TemplateDocumentRole parseDocumentRole(String documentRole) {
        try {
            return TemplateDocumentRole.valueOf(documentRole);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private CeremonyEventDto.Response.CeremonyTemplateSummary toMappingSummary(CeremonyTemplate mapping) {
        return new CeremonyEventDto.Response.CeremonyTemplateSummary(
                mapping.getId(),
                mapping.getCeremonyEvent().getId(),
                mapping.getTemplate().getId(),
                mapping.getDocumentRole().name(),
                mapping.getCreatedAt()
        );
    }

    /** {@link CeremonyResultService}도 같은 패키지에서 공유한다(4라운드부터의 관례). */
    CeremonyEvent findEventInCeremonyOrThrow(Long ceremonyId, Long eventId) {
        CeremonyEvent event = ceremonyEventRepository.findById(eventId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND));
        if (!event.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND);
        }
        return event;
    }

    private List<Long> retrieveAppliedOptionalFeatureIds(CeremonyEvent event) {
        return ceremonyEventOptionalFeatureRepository.findAllByCeremonyEventId(event.getId()).stream()
                .map(mapping -> mapping.getOptionalFeature().getId())
                .toList();
    }

    private CeremonyEventType parseEventType(String eventType) {
        try {
            return CeremonyEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private String generateUniqueAccessKey() {
        String accessKey;
        do {
            accessKey = UUID.randomUUID().toString().replace("-", "");
        } while (ceremonyEventRepository.existsByAccessKey(accessKey));
        return accessKey;
    }

    private CeremonyEventDto.Response.CeremonyEventSummary toSummary(CeremonyEvent event, List<Long> optionalFeatureIds) {
        return new CeremonyEventDto.Response.CeremonyEventSummary(
                event.getId(),
                event.getCeremony().getId(),
                event.getName(),
                event.getEventType().name(),
                event.getStatus().name(),
                event.getVenue(),
                event.getScheduledStartAt(),
                event.getScheduledEndAt(),
                event.getActualStartAt(),
                event.getActualEndAt(),
                event.getAccessKey(),
                event.getDescription(),
                optionalFeatureIds,
                event.getDisplayOrder(),
                event.getCreatedAt()
        );
    }
}
