package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.integration.storage.common.error.StorageException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 양식(Template). {@code Ceremony} 직속이다(signstage-docs
 * business/ceremony-feature-migration-review.md 4.2절). 이번 라운드는 업로드/조회/다운로드까지만
 * 다루고, 수정·삭제·{@code COMPLETED} 전환은 아직 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final DocumentStoragePort documentStoragePort;
    private final CeremonyService ceremonyService;

    @Transactional
    public TemplateDto.Response.TemplateSummary uploadTemplate(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            String title,
            String documentRole,
            MultipartFile file
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);

        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.TEMPLATES);
        long currentCount = templateRepository.countByCeremonyId(ceremonyId);
        if (currentCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_TEMPLATE_LIMIT_EXCEEDED);
        }

        TemplateDocumentRole role = parseDocumentRole(documentRole);
        checkPdfExtension(file.getOriginalFilename());

        StoredFile storedFile;
        try {
            storedFile = documentStoragePort.store("templates/" + ceremonyId, file);
        } catch (StorageException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }

        Template template = Template.builder()
                .ceremony(ceremony)
                .title(title)
                .documentRole(role)
                .storageKey(storedFile.storageKey())
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFile.storedFilename())
                .build();
        templateRepository.save(template);

        return toSummary(template);
    }

    public List<TemplateDto.Response.TemplateSummary> findTemplates(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return templateRepository.findAllByCeremonyId(ceremonyId).stream().map(this::toSummary).toList();
    }

    public TemplateDto.Response.TemplateSummary retrieveTemplate(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return toSummary(findTemplateInCeremonyOrThrow(ceremonyId, templateId));
    }

    public DownloadedTemplate downloadTemplateFile(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        Resource resource;
        try {
            resource = documentStoragePort.loadAsResource(template.getStorageKey());
        } catch (StorageException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
        return new DownloadedTemplate(resource, template.getOriginalFilename());
    }

    private Template findTemplateInCeremonyOrThrow(Long ceremonyId, Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.getCeremony().getId().equals(ceremonyId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private TemplateDocumentRole parseDocumentRole(String documentRole) {
        try {
            return TemplateDocumentRole.valueOf(documentRole);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void checkPdfExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private TemplateDto.Response.TemplateSummary toSummary(Template template) {
        return new TemplateDto.Response.TemplateSummary(
                template.getId(),
                template.getCeremony().getId(),
                template.getTitle(),
                template.getDocumentRole().name(),
                template.getOriginalFilename(),
                template.getStatus().name(),
                template.getCreatedAt()
        );
    }

    /** 컨트롤러가 {@code Content-Disposition} 헤더를 만드는 데 필요한 파일명을 함께 돌려준다. */
    public record DownloadedTemplate(Resource resource, String originalFilename) {
    }
}
