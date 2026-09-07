package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEventEffectSettingDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventEffectSetting;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventEffectSettingRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 이벤트별 이벤트 효과 프리셋 선택 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-SETTING. {@code CeremonyEventService}가
 * 아니라 별도 서비스로 뺐다 — {@code CeremonyEventService}가 이미 10개 가까운 repository를 직접
 * 들고 있어(backend-coding-convention.md 8.2절 "5~7개 이상이면 책임 분리 검토") 여기서 더
 * 늘리는 대신 서비스 간 호출로 트랜잭션을 공유한다({@code @Transactional} 기본 전파(REQUIRED)가
 * {@code CeremonyEventService}의 트랜잭션에 그대로 합류한다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyEventEffectSettingService {

    private final CeremonyEventEffectSettingRepository ceremonyEventEffectSettingRepository;
    private final CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final CeremonyEventRepository ceremonyEventRepository;
    private final CeremonyService ceremonyService;

    public List<CeremonyEventEffectSettingDto.Response.EffectSettingSummary> findEffectSettings(
            Long organizationId, Long ceremonyId, Long eventId, Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        return findSummaries(event.getId());
    }

    /** BE-SETTING-03 — 조직 스코프 PUT. 전체 교체이며(선택 안 한 분류는 전부 해제) DRAFT/READY에서만 가능하다. */
    @Transactional
    public List<CeremonyEventEffectSettingDto.Response.EffectSettingSummary> updateEffectSettings(
            Long organizationId,
            Long ceremonyId,
            Long eventId,
            Long currentUserId,
            CeremonyEventEffectSettingDto.Request.UpdateEffectSelections request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        CeremonyEvent event = findEventInCeremonyOrThrow(ceremonyId, eventId);
        checkEventNotLocked(event);

        List<Long> appliedOptionalFeatureIds = retrieveAppliedOptionalFeatureIds(eventId);
        applyEffectSelections(event, appliedOptionalFeatureIds, request.getSelections());
        return findSummaries(eventId);
    }

    /**
     * BE-SETTING-01 핵심 — {@code (event, target, trigger)}별 선택을 전체 교체한다
     * (delete-all-then-recreate, {@code CeremonyEventService#applyOptionalFeatures}와 같은
     * 패턴). {@code selections}에 없는 분류는 전부 해제(NONE)된다. {@code effectId}가 null인
     * 항목도 같은 의미다 — 결과적으로 저장 대상에서 빠진다.
     *
     * <p>{@code CeremonyEventService}의 행사 등록/수정 트랜잭션 안에서도 이 메서드를 그대로
     * 호출한다(BE-SETTING-02) — {@code appliedOptionalFeatureIds}를 그 트랜잭션이 이미 계산해
     * 둔 값(방금 적용한 선택옵션)으로 넘겨받아 재조회 없이 검증에 쓴다.
     */
    @Transactional
    public List<CeremonyEventEffectSettingDto.Response.EffectSettingSummary> applyEffectSelections(
            CeremonyEvent event,
            List<Long> appliedOptionalFeatureIds,
            List<CeremonyEventEffectSettingDto.Request.EffectSelection> selections
    ) {
        Set<Long> appliedFeatureIds = new HashSet<>(appliedOptionalFeatureIds);
        List<CeremonyEffectDefinition> toApply = new ArrayList<>();

        for (CeremonyEventEffectSettingDto.Request.EffectSelection selection : selections) {
            if (selection.getEffectId() == null) {
                continue;
            }
            CeremonyEffectTarget targetType = parseTarget(selection.getTargetType());
            CeremonyEffectTrigger triggerType = parseTrigger(selection.getTriggerType());

            CeremonyEffectDefinition definition = ceremonyEffectDefinitionRepository.findById(selection.getEffectId())
                    .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND));
            if (!definition.isEnabled()) {
                throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_INACTIVE);
            }
            if (definition.getTargetType() != targetType || definition.getTriggerType() != triggerType) {
                throw new ApplicationException(CeremonyErrorCode.EFFECT_SELECTION_CLASSIFICATION_MISMATCH);
            }
            if (!appliedFeatureIds.contains(definition.getRequiredOptionalFeature().getId())) {
                throw new ApplicationException(CeremonyErrorCode.EFFECT_SELECTION_OPTIONAL_FEATURE_NOT_APPLIED);
            }
            toApply.add(definition);
        }

        ceremonyEventEffectSettingRepository.deleteAllByEventId(event.getId());
        for (CeremonyEffectDefinition definition : toApply) {
            ceremonyEventEffectSettingRepository.save(
                    CeremonyEventEffectSetting.builder().event(event).definition(definition).build()
            );
        }

        return findSummaries(event.getId());
    }

    /**
     * BE-SETTING-02 — 선택옵션 해제 시 그 옵션을 요구하는 설정만 지운다(다른 trigger 설정은
     * 유지). {@code CeremonyEventService#applyOptionalFeatures}가 선택옵션을 재적용할 때마다
     * (등록/수정/적용옵션 교체 세 경로 공통) 호출한다.
     */
    @Transactional
    public void pruneSettingsRequiringUnappliedFeatures(Long eventId, List<Long> appliedOptionalFeatureIds) {
        Set<Long> appliedFeatureIds = new HashSet<>(appliedOptionalFeatureIds);
        for (CeremonyEventEffectSetting setting : ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(eventId)) {
            if (!appliedFeatureIds.contains(setting.getDefinition().getRequiredOptionalFeature().getId())) {
                ceremonyEventEffectSettingRepository.delete(setting);
            }
        }
    }

    private List<Long> retrieveAppliedOptionalFeatureIds(Long eventId) {
        return ceremonyEventOptionalFeatureRepository.findAllByCeremonyEventId(eventId).stream()
                .map(mapping -> mapping.getOptionalFeature().getId())
                .toList();
    }

    private CeremonyEvent findEventInCeremonyOrThrow(Long ceremonyId, Long eventId) {
        CeremonyEvent event = ceremonyEventRepository.findById(eventId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND));
        if (!event.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_EVENT_NOT_FOUND);
        }
        return event;
    }

    private void checkEventNotLocked(CeremonyEvent event) {
        if (event.isLocked()) {
            throw new ApplicationException(CeremonyErrorCode.EVENT_LOCKED);
        }
    }

    private CeremonyEffectTarget parseTarget(String targetType) {
        try {
            return CeremonyEffectTarget.valueOf(targetType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private CeremonyEffectTrigger parseTrigger(String triggerType) {
        try {
            return CeremonyEffectTrigger.valueOf(triggerType);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    List<CeremonyEventEffectSettingDto.Response.EffectSettingSummary> findSummaries(Long eventId) {
        return ceremonyEventEffectSettingRepository.findAllByEventIdWithDefinition(eventId).stream()
                .map(this::toSummary)
                .toList();
    }

    private CeremonyEventEffectSettingDto.Response.EffectSettingSummary toSummary(CeremonyEventEffectSetting setting) {
        CeremonyEffectDefinition definition = setting.getDefinition();
        return new CeremonyEventEffectSettingDto.Response.EffectSettingSummary(
                setting.getId().getTargetType().name(),
                setting.getId().getTriggerType().name(),
                definition.getCode(),
                definition.getRendererKey(),
                definition.getDisplayName(),
                setting.isRuntimeEnabled(),
                definition.isManuallyTriggerable()
        );
    }
}
