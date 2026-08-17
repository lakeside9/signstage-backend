package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateFieldDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이지 내 서명란 좌표(TemplateField). 필드 수는 과금 대상이 아니라 한도 검사가 없다.
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

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
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
