package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventLogDto;
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
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventLogRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

        CeremonyEventType eventType = parseEventType(request.getEventType());
        CapacityType capacityType = eventType == CeremonyEventType.TEST
                ? CapacityType.TEST_EVENTS
                : CapacityType.MAIN_EVENTS;

        // 한도 하드 블록(4.5절) — 유효 한도 = 플랜 기본값 + Σ 추가구매.
        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, capacityType);
        long currentCount = ceremonyEventRepository.countByCeremonyIdAndEventType(ceremonyId, eventType);
        if (currentCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_LIMIT_EXCEEDED);
        }

        CeremonyEvent event = CeremonyEvent.builder()
                .ceremony(ceremony)
                .name(request.getName())
                .eventType(eventType)
                .venue(request.getVenue())
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .accessKey(generateUniqueAccessKey())
                .description(request.getDescription())
                .build();
        ceremonyEventRepository.save(event);

        return toSummary(event, List.of());
    }

    public List<CeremonyEventDto.Response.CeremonyEventSummary> findCeremonyEvents(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return ceremonyEventRepository.findAllByCeremonyId(ceremonyId).stream()
                .map(event -> toSummary(event, retrieveAppliedOptionalFeatureIds(event)))
                .toList();
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

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);

        List<Long> requestedIds = request.getOptionalFeatureIds() == null ? List.of() : request.getOptionalFeatureIds();
        List<Long> purchasedIds = ceremonyService.retrievePurchasedOptionalFeatureIds(ceremony);
        if (!purchasedIds.containsAll(requestedIds)) {
            throw new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_PURCHASED);
        }

        List<OptionalFeature> features = requestedIds.isEmpty()
                ? List.of()
                : optionalFeatureRepository.findAllByIdIn(requestedIds);

        ceremonyEventOptionalFeatureRepository.deleteAllByCeremonyEventId(eventId);
        for (OptionalFeature feature : features) {
            ceremonyEventOptionalFeatureRepository.save(
                    CeremonyEventOptionalFeature.builder().ceremonyEvent(event).optionalFeature(feature).build()
            );
        }

        return toSummary(event, requestedIds);
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

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_IN_CEREMONY);
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

    private void validateFinishConditions(CeremonyEvent event) {
        List<CeremonyTemplate> contractMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.CONTRACT);
        List<CeremonyTemplate> exhibitionMappings = ceremonyTemplateRepository
                .findAllByCeremonyEventIdAndDocumentRole(event.getId(), TemplateDocumentRole.EXHIBITION);

        Set<Long> requiredSignerIds = new HashSet<>(collectRequiredSignerIds(contractMappings));
        requiredSignerIds.addAll(collectRequiredSignerIds(exhibitionMappings));
        requiredSignerIds.remove(null);

        for (Long signerId : requiredSignerIds) {
            boolean completed = ceremonyEventLogRepository.existsByCeremonyEventIdAndActorTypeAndActorIdAndEventAction(
                    event.getId(), ActorType.SIGNER, signerId, CeremonyEventAction.SIGNATURE_COMPLETE
            );
            if (!completed) {
                throw new ApplicationException(CeremonyErrorCode.EVENT_FINISH_CONDITION_NOT_MET);
            }
        }
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
                log.getMessage(),
                log.getCreatedAt()
        );
    }

    private void checkEventNotLocked(CeremonyEvent event) {
        if (event.getStatus() == CeremonyEventStatus.STARTED || event.getStatus() == CeremonyEventStatus.FINISHED) {
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
                event.getCreatedAt()
        );
    }
}
