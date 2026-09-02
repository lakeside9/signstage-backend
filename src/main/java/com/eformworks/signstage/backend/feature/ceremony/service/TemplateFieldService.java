package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateFieldDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이지 내 서명란 좌표(TemplateField). 필드 수는 과금 대상이 아니라 한도 검사가 없다.
 * 설정 완료(COMPLETED)된 문서 양식은 서명란을 더 이상 바꿀 수 없다({@link CeremonyErrorCode#TEMPLATE_LOCKED}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateFieldService {

    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateRepository templateRepository;
    private final CeremonyService ceremonyService;
    private final SignerService signerService;

    @Transactional
    public TemplateFieldDto.Response.TemplateFieldSummary createTemplateField(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId,
            TemplateFieldDto.Request.CreateTemplateField request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        checkNotLocked(template);
        Signer signer = request.getSignerId() == null
                ? null
                : signerService.findSignerInCeremonyOrThrow(ceremonyId, request.getSignerId());

        TemplateField field = TemplateField.builder()
                .template(template)
                .signer(signer)
                .fieldKey(request.getFieldKey())
                .pageIndex(request.getPageIndex())
                .fieldIndex(request.getFieldIndex())
                .fieldName(request.getFieldName())
                .roleCode(request.getRoleCode())
                .signOrder(request.getSignOrder())
                .isRequired(request.getIsRequired())
                .xRatio(request.getXRatio())
                .yRatio(request.getYRatio())
                .widthRatio(request.getWidthRatio())
                .heightRatio(request.getHeightRatio())
                .build();
        templateFieldRepository.save(field);

        return toSummary(field);
    }

    /**
     * 서명란 배치 화면의 "저장" — 항상 현재 전체 필드 배열을 통째로 받는다(diff 없음). 기존
     * 필드를 전부 지우고 받은 배열로 다시 채운다(legacy TemplateService.setFields와 같은 규약
     * — signstage-docs 참고용 legacy 소스 feature/template/service/TemplateService.java:198).
     */
    @Transactional
    public List<TemplateFieldDto.Response.TemplateFieldSummary> setFields(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId,
            TemplateFieldDto.Request.SetFields request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        checkNotLocked(template);

        List<TemplateFieldDto.Request.CreateTemplateField> requestedFields = request.getFields();
        Set<Integer> fieldIndexes = new HashSet<>();
        for (TemplateFieldDto.Request.CreateTemplateField field : requestedFields) {
            if (!fieldIndexes.add(field.getFieldIndex())) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
        }

        templateFieldRepository.deleteAllByTemplateId(templateId);
        List<TemplateField> saved = requestedFields.stream()
                .map(field -> TemplateField.builder()
                        .template(template)
                        .signer(field.getSignerId() == null
                                ? null
                                : signerService.findSignerInCeremonyOrThrow(ceremonyId, field.getSignerId()))
                        .fieldKey(field.getFieldKey())
                        .pageIndex(field.getPageIndex())
                        .fieldIndex(field.getFieldIndex())
                        .fieldName(field.getFieldName())
                        .roleCode(field.getRoleCode())
                        .signOrder(field.getSignOrder())
                        .isRequired(field.getIsRequired())
                        .xRatio(field.getXRatio())
                        .yRatio(field.getYRatio())
                        .widthRatio(field.getWidthRatio())
                        .heightRatio(field.getHeightRatio())
                        .build())
                .toList();
        templateFieldRepository.saveAll(saved);

        return saved.stream().map(this::toSummary).toList();
    }

    /**
     * 서명란 복제 — 같은 협약 내 같은 문서 역할(documentRole)의 다른 문서에서 서명란 배치를
     * 통째로 가져와 이 문서의 기존 서명란을 교체한다(2026-09-02 legacy 포팅. legacy
     * ~/Works/eform/source/signstage/signstage-backend TemplateService#cloneFields 참고). 서명자
     * 지정도 그대로 가져온다 — 같은 협약이라 Signer가 그대로 유효하다. {@link #setFields}와
     * 같은 규칙으로, 설정 완료(COMPLETED)된 대상 문서는 막는다.
     */
    @Transactional
    public List<TemplateFieldDto.Response.TemplateFieldSummary> cloneFields(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long sourceTemplateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template target = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        checkNotLocked(target);
        Template source = findTemplateInCeremonyOrThrow(ceremonyId, sourceTemplateId);

        if (target.getId().equals(source.getId())) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        if (target.getDocumentRole() != source.getDocumentRole()) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_DOCUMENT_ROLE_MISMATCH);
        }

        List<TemplateField> sourceFields = templateFieldRepository.findAllByTemplateId(source.getId());
        if (sourceFields.isEmpty()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }

        templateFieldRepository.deleteAllByTemplateId(templateId);
        List<TemplateField> cloned = sourceFields.stream()
                .map(field -> TemplateField.builder()
                        .template(target)
                        .signer(field.getSigner())
                        .fieldKey(field.getFieldKey())
                        .pageIndex(field.getPageIndex())
                        .fieldIndex(field.getFieldIndex())
                        .fieldName(field.getFieldName())
                        .roleCode(field.getRoleCode())
                        .signOrder(field.getSignOrder())
                        .isRequired(field.getIsRequired())
                        .xRatio(field.getXRatio())
                        .yRatio(field.getYRatio())
                        .widthRatio(field.getWidthRatio())
                        .heightRatio(field.getHeightRatio())
                        .build())
                .toList();
        templateFieldRepository.saveAll(cloned);

        return cloned.stream().map(this::toSummary).toList();
    }

    private void checkNotLocked(Template template) {
        if (template.getStatus() == TemplateStatus.COMPLETED) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_LOCKED);
        }
    }

    public List<TemplateFieldDto.Response.TemplateFieldSummary> findTemplateFields(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        return templateFieldRepository.findAllByTemplateId(templateId).stream().map(this::toSummary).toList();
    }

    private Template findTemplateInCeremonyOrThrow(Long ceremonyId, Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private TemplateFieldDto.Response.TemplateFieldSummary toSummary(TemplateField field) {
        return new TemplateFieldDto.Response.TemplateFieldSummary(
                field.getId(),
                field.getTemplate().getId(),
                field.getSigner() != null ? field.getSigner().getId() : null,
                field.getFieldKey(),
                field.getPageIndex(),
                field.getFieldIndex(),
                field.getFieldName(),
                field.getRoleCode(),
                field.getSignOrder(),
                field.getIsRequired(),
                field.getXRatio(),
                field.getYRatio(),
                field.getWidthRatio(),
                field.getHeightRatio(),
                field.getCreatedAt()
        );
    }
}
