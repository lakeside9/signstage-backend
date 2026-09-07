package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyEffectDefinitionDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTarget;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectTrigger;
import com.eformworks.signstage.backend.feature.ceremony.entity.OptionalFeature;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.OptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 이벤트 효과 카탈로그 정의 관리 — signstage-docs
 * business/ceremony-event-effect-implementation-tasks.md BE-CATALOG-03. 등록·수정·순서 이동은
 * 플랫폼 관리자 전용({@code ACTION_EFFECT_MANAGE}), 활성+사용자 노출 목록 조회는 인증된 사용자
 * 누구나 가능하다({@code OptionalFeatureService}와 같은 패턴).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CeremonyEffectDefinitionService {

    private static final int DISPLAY_ORDER_STEP = 10;
    private static final String ACTION_EFFECT_MANAGE = "ACTION_EFFECT_MANAGE";
    /**
     * Spring Boot 4.1부터 Jackson 자동설정 빈이 {@code tools.jackson} 계열로 옮겨져
     * {@code com.fasterxml.jackson.databind.ObjectMapper} 빈을 그대로 주입받을 수 없다
     * ({@code SecurityConfig}의 같은 이유 주석 참고, {@code CeremonyResultService}/
     * {@code CeremonyEventService}와 같은 처리).
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    private final OptionalFeatureRepository optionalFeatureRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary createDefinition(
            String actingPlatformRole,
            Long adminUserId,
            CeremonyEffectDefinitionDto.Request.CreateCeremonyEffectDefinition request
    ) {
        checkAllowed(actingPlatformRole);

        if (ceremonyEffectDefinitionRepository.existsByCode(request.getCode())) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_CODE_DUPLICATE);
        }
        CeremonyEffectTarget targetType = parseTarget(request.getTargetType());
        CeremonyEffectTrigger triggerType = parseTrigger(request.getTriggerType());
        OptionalFeature requiredOptionalFeature = optionalFeatureRepository.findById(request.getRequiredOptionalFeatureId())
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.OPTIONAL_FEATURE_NOT_FOUND));

        int displayOrder = nextDisplayOrderInGroup(targetType, triggerType);

        CeremonyEffectDefinition definition = CeremonyEffectDefinition.builder()
                .code(request.getCode())
                .targetType(targetType)
                .triggerType(triggerType)
                .requiredOptionalFeature(requiredOptionalFeature)
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .rendererKey(request.getRendererKey())
                .manuallyTriggerable(request.getManuallyTriggerable())
                .displayOrder(displayOrder)
                .configJson(writeConfigJson(request.getConfigJson()))
                .build();
        ceremonyEffectDefinitionRepository.save(definition);

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_CEREMONY_EFFECT_DEFINITION,
                null,
                null,
                "effectDefinitionId=" + definition.getId() + ", code=" + definition.getCode()
        );

        return toSummary(definition);
    }

    @Transactional
    public CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary updateDefinition(
            Long definitionId,
            String actingPlatformRole,
            Long adminUserId,
            CeremonyEffectDefinitionDto.Request.UpdateCeremonyEffectDefinition request
    ) {
        checkAllowed(actingPlatformRole);

        CeremonyEffectDefinition definition = ceremonyEffectDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND));

        String detail = "effectDefinitionId=" + definitionId
                + ", enabled: " + definition.isEnabled() + " -> " + request.getEnabled()
                + ", userVisible: " + definition.isUserVisible() + " -> " + request.getUserVisible();

        definition.updateInfo(
                request.getDisplayName(),
                request.getDescription(),
                request.getEnabled(),
                request.getUserVisible(),
                request.getManuallyTriggerable(),
                writeConfigJson(request.getConfigJson())
        );

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_CEREMONY_EFFECT_DEFINITION, null, null, detail
        );

        return toSummary(definition);
    }

    public Page<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> findDefinitions(
            String keyword,
            String targetType,
            String triggerType,
            Boolean enabled,
            Boolean userVisible,
            Pageable pageable
    ) {
        return ceremonyEffectDefinitionRepository.search(
                keyword,
                targetType == null ? null : parseTarget(targetType),
                triggerType == null ? null : parseTrigger(triggerType),
                enabled,
                userVisible,
                pageable
        ).map(this::toSummary);
    }

    public CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary findDefinition(Long definitionId) {
        return toSummary(ceremonyEffectDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND)));
    }

    /** {@code /api/ceremony-effects} — 인증된 사용자 누구나, 활성+사용자 노출 정의만. */
    public List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> findPublicDefinitions() {
        return ceremonyEffectDefinitionRepository.findAllByEnabledTrueAndUserVisibleTrueOrderByTargetTypeAscTriggerTypeAscDisplayOrderAsc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 같은 (target, trigger) 그룹 안에서만 순서를 바꾼다. 그룹 전체 행을 비관적 잠금으로 읽어온
     * 뒤, 요청이 그 그룹의 id 전체와 정확히 일치하는지 확인하고 나열된 순서대로 10 단위로
     * 재정규화한다.
     */
    @Transactional
    public List<CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary> reorderDefinitions(
            String actingPlatformRole,
            Long adminUserId,
            CeremonyEffectDefinitionDto.Request.ReorderCeremonyEffectDefinitions request
    ) {
        checkAllowed(actingPlatformRole);

        CeremonyEffectTarget targetType = parseTarget(request.getTargetType());
        CeremonyEffectTrigger triggerType = parseTrigger(request.getTriggerType());

        List<CeremonyEffectDefinition> group =
                ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(targetType, triggerType);
        Map<Long, CeremonyEffectDefinition> byId = group.stream()
                .collect(Collectors.toMap(CeremonyEffectDefinition::getId, d -> d));

        Set<Long> groupIds = byId.keySet();
        if (groupIds.size() != request.getOrderedIds().size() || !groupIds.containsAll(request.getOrderedIds())) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_ORDER_GROUP_MISMATCH);
        }

        int order = DISPLAY_ORDER_STEP;
        for (Long id : request.getOrderedIds()) {
            byId.get(id).updateDisplayOrder(order);
            order += DISPLAY_ORDER_STEP;
        }

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.REORDER_CEREMONY_EFFECT_DEFINITIONS,
                null,
                null,
                "targetType=" + targetType + ", triggerType=" + triggerType + ", orderedIds=" + request.getOrderedIds()
        );

        return group.stream()
                .sorted(Comparator.comparingInt(CeremonyEffectDefinition::getDisplayOrder))
                .map(this::toSummary)
                .toList();
    }

    private int nextDisplayOrderInGroup(CeremonyEffectTarget targetType, CeremonyEffectTrigger triggerType) {
        return ceremonyEffectDefinitionRepository.findAllByGroupForUpdate(targetType, triggerType).stream()
                .mapToInt(CeremonyEffectDefinition::getDisplayOrder)
                .max()
                .orElse(0) + DISPLAY_ORDER_STEP;
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

    /** JSON object만 허용한다 — request DTO가 이미 {@code Map<String, Object>}로 받아 배열/스칼라는 역직렬화 단계에서 걸러진다. */
    private String writeConfigJson(Map<String, Object> configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(configJson);
        } catch (Exception e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST, e);
        }
    }

    private Map<String, Object> readConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new ApplicationException(CommonErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary toSummary(CeremonyEffectDefinition definition) {
        return new CeremonyEffectDefinitionDto.Response.CeremonyEffectDefinitionSummary(
                definition.getId(),
                definition.getCode(),
                definition.getTargetType().name(),
                definition.getTriggerType().name(),
                definition.getRequiredOptionalFeature().getId(),
                definition.getDisplayName(),
                definition.getDescription(),
                definition.getRendererKey(),
                definition.isEnabled(),
                definition.isUserVisible(),
                definition.isManuallyTriggerable(),
                definition.getDisplayOrder(),
                readConfigJson(definition.getConfigJson()),
                definition.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, ACTION_EFFECT_MANAGE)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
