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
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyTemplateRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateFieldRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.TemplateRepository;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.integration.storage.common.error.StorageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 양식(Template). {@code Ceremony} 직속이다(signstage-docs
 * business/ceremony-feature-migration-review.md 4.2절).
 *
 * <p>{@code status}는 엔티티에 저장된 값을 그대로 내려준다 — 서명란 배치 화면에서 "설정
 * 완료"를 눌러야({@link #completeTemplate}) COMPLETED로 바뀐다({@link Template#complete()}).
 * 완료되면 {@link TemplateFieldService}가 이후 서명란 변경을 막는다(읽기 전용). 한때 서명란
 * 개수로 상태를 매번 계산하는 방식으로 바꿨었지만, 진짜 완료(잠금) 개념이 생기면서 원래
 * 컬럼을 다시 쓰는 쪽으로 되돌렸다 — 컬럼은 처음부터 있었고 지금까지 항상 DRAFT였을 뿐이라
 * 마이그레이션은 필요 없다.
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

    /**
     * PDF 페이지 수/첫 페이지 크기(pt) — 서명란 배치 화면이 캔버스 크기를 잡는 데 쓴다.
     * PDFBox로 원본을 직접 읽는다({@link com.eformworks.signstage.backend.feature.ceremony.support.SignatureOverlayRenderer}와
     * 같은 라이브러리, 새 의존성 아님).
     */
    public TemplateDto.Response.TemplateInfo retrieveTemplateInfo(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        try (PDDocument document = Loader.loadPDF(readTemplateBytes(template))) {
            Float width = null;
            Float height = null;
            if (document.getNumberOfPages() > 0) {
                PDRectangle mediaBox = document.getPage(0).getMediaBox();
                width = mediaBox.getWidth();
                height = mediaBox.getHeight();
            }
            return new TemplateDto.Response.TemplateInfo(document.getNumberOfPages(), width, height);
        } catch (IOException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
    }

    /** 지정한 페이지를 PNG로 렌더링한다. scale이 클수록 더 선명하지만 응답이 커진다. */
    public byte[] renderTemplatePage(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId,
            int pageIndex,
            float scale
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        try (PDDocument document = Loader.loadPDF(readTemplateBytes(template))) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage image = pdfRenderer.renderImage(pageIndex, scale);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
    }

    private byte[] readTemplateBytes(Template template) {
        try {
            return documentStoragePort.loadAsResource(template.getStorageKey()).getContentAsByteArray();
        } catch (StorageException | IOException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
    }

    /**
     * 서명란 배치 화면의 "설정 완료" — 서명란이 1개 이상이어야 하고, 완료하면 이후
     * TemplateFieldService가 이 템플릿의 서명란 변경을 막는다(되돌리는 API는 없다).
     */
    @Transactional
    public TemplateDto.Response.TemplateSummary completeTemplate(
            Long organizationId,
            Long ceremonyId,
            Long templateId,
            Long currentUserId
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Template template = findTemplateInCeremonyOrThrow(ceremonyId, templateId);
        if (templateFieldRepository.countByTemplateId(templateId) == 0) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        template.complete();
        return toSummary(template);
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
        return new TemplateDto.Response.TemplateSummary(
                template.getId(),
                template.getCeremony().getId(),
                template.getTitle(),
                template.getDocumentRole().name(),
                template.getOriginalFilename(),
                template.getStatus().name(),
                fieldCount,
                template.getCreatedAt()
        );
    }

    /** 컨트롤러가 {@code Content-Disposition} 헤더를 만드는 데 필요한 파일명을 함께 돌려준다. */
    public record DownloadedTemplate(Resource resource, String originalFilename) {
    }
}
