package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateStatus;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.integration.storage.common.error.StorageException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 양식(Template). {@code Ceremony} 직속이다(signstage-docs
 * business/ceremony-feature-migration-review.md 4.2절).
 *
 * <p>{@code status}는 DB에 저장된 값을 그대로 내려주지 않는다 — 서명란(TemplateField)이
 * 하나라도 있으면 COMPLETED("설정 완료"), 없으면 DRAFT("설정 필요")로 응답 조립 시점에
 * 매번 계산한다. 엔티티의 {@code status} 컬럼 자체는 항상 DRAFT로 남아 있고 아무 데서도
 * 읽지 않는다(예전에 "다음 라운드에 추가"라던 매핑 연동 COMPLETED 전환은 결국 만들어지지
 * 않았다) — 그래서 마이그레이션 없이 이 방식으로 재정의했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final CeremonyTemplateRepository ceremonyTemplateRepository;
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
        ceremonyService.checkCeremonyEditable(ceremony);

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

    /** 제목/문서유형만 바꾼다. PDF 파일 자체는 여기서 바꾸지 않는다(서명란 좌표가 깨지기 때문). */
    @Transactional
    public TemplateDto.Response.TemplateSummary updateTemplate(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId,
            TemplateDto.Request.UpdateTemplate request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        template.updateInfo(request.getTitle(), parseDocumentRole(request.getDocumentRole()));
        return toSummary(template);
    }

    /**
     * 이미 하위 행사(CeremonyEvent)에 매핑된 문서 양식은 삭제할 수 없다 — 막지 않으면
     * ceremony_templates의 FK가 깨진다. 매핑 안 된 문서 양식은 서명란 → 저장소 파일 →
     * Template 행 순으로 지운다.
     */
    @Transactional
    public void deleteTemplate(Long organizationId, Long ceremonyId, Long templateId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        if (ceremonyTemplateRepository.existsByTemplateId(templateId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_IN_USE);
        }

        templateFieldRepository.deleteAllByTemplateId(templateId);
        try {
            documentStoragePort.delete(template.getStorageKey());
        } catch (StorageException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
        templateRepository.delete(template);
    }

    /**
     * 원본 파일을 그대로 복사해 새 문서 양식을 만든다. 서명란은 복제하지 않는다 — 복제본은
     * 항상 서명란 0개("설정 필요")로 시작한다. 업로드와 같은 템플릿 개수 한도를 적용한다.
     */
    @Transactional
    public TemplateDto.Response.TemplateSummary duplicateTemplate(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        int effectiveLimit = ceremonyService.calculateEffectiveCapacity(ceremony, CapacityType.TEMPLATES);
        long currentCount = templateRepository.countByCeremonyId(ceremonyId);
        if (currentCount >= effectiveLimit) {
            throw new ApplicationException(CeremonyErrorCode.CEREMONY_TEMPLATE_LIMIT_EXCEEDED);
        }

        Template original = findTemplateInCeremonyOrThrow(ceremonyId, templateId);

        byte[] content;
        StoredFile storedFile;
        try {
            content = documentStoragePort.loadAsResource(original.getStorageKey()).getContentAsByteArray();
            storedFile = documentStoragePort.store(
                    "templates/" + ceremonyId, original.getOriginalFilename(), content
            );
        } catch (StorageException | IOException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }

        Template duplicated = Template.builder()
                .ceremony(ceremony)
                .title(original.getTitle() + " (복제)")
                .documentRole(original.getDocumentRole())
                .storageKey(storedFile.storageKey())
                .originalFilename(original.getOriginalFilename())
                .storedFilename(storedFile.storedFilename())
                .build();
        templateRepository.save(duplicated);

        return toSummary(duplicated);
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
        long fieldCount = templateFieldRepository.countByTemplateId(template.getId());
        TemplateStatus status = fieldCount > 0 ? TemplateStatus.COMPLETED : TemplateStatus.DRAFT;
        return new TemplateDto.Response.TemplateSummary(
                template.getId(),
                template.getCeremony().getId(),
                template.getTitle(),
                template.getDocumentRole().name(),
                template.getOriginalFilename(),
                status.name(),
                fieldCount,
                template.getCreatedAt()
        );
    }

    /** 컨트롤러가 {@code Content-Disposition} 헤더를 만드는 데 필요한 파일명을 함께 돌려준다. */
    public record DownloadedTemplate(Resource resource, String originalFilename) {
    }
}
