package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventOptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventType;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.List;
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

    private CeremonyEvent findEventInCeremonyOrThrow(Long ceremonyId, Long eventId) {
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
