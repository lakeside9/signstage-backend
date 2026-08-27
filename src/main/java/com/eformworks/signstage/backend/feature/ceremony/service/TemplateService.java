package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.ceremony.dto.TemplateDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEventStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.Template;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateDocumentRole;
import com.eformworks.signstage.backend.feature.ceremony.entity.TemplateField;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * 페이지 이미지 사전 렌더링(캐시 예열) 배율. {@code MappedDocumentPreview}(프로젝터/행사제어/
     * 문서매핑 화면이 공유하는 컴포넌트)가 페이지 이미지를 요청할 때 항상 이 값을 쓰므로,
     * 업로드/복제 직후 이 배율로 미리 캐시를 채워두면 첫 조회부터 캐시 히트가 된다.
     */
    private static final float PAGE_IMAGE_CACHE_PRERENDER_SCALE = 1.5f;

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
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);

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
                .displayOrder((int) currentCount)
                .build();
        templateRepository.save(template);
        preRenderPageImageCache(template);

        return toSummary(template);
    }

    public List<TemplateDto.Response.TemplateSummary> findTemplates(Long organizationId, Long ceremonyId, Long currentUserId) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyReadAccess(ceremony, actingMember, currentUserId);

        return templateRepository.findAllByCeremonyIdOrderByDisplayOrderAscIdAsc(ceremonyId).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 문서 양식 목록의 위/아래 이동 버튼이 호출한다 — 전체 배열을 원하는 순서로 다시 인덱싱해
     * 통째로 보낸다. 잠금(isTemplateLockedByStartedEvent) 여부와 무관하게 항상 허용한다.
     */
    @Transactional
    public List<TemplateDto.Response.TemplateSummary> updateTemplateDisplayOrders(
            Long organizationId,
            Long ceremonyId,
            Long currentUserId,
            DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        Ceremony ceremony = ceremonyService.findCeremonyInOrganizationOrThrow(organizationId, ceremonyId);
        Member actingMember = ceremonyService.findActiveMemberOrThrow(organizationId, currentUserId);
        ceremonyService.checkCeremonyManageAccess(ceremony, actingMember, currentUserId);
        ceremonyService.checkCeremonyEditable(ceremony);

        Map<Long, Template> templatesById = templateRepository
                .findAllById(request.getItems().stream().map(DisplayOrderRequest.Item::getId).toList())
                .stream()
                .collect(Collectors.toMap(Template::getId, Function.identity()));

        for (DisplayOrderRequest.Item item : request.getItems()) {
            Template template = templatesById.get(item.getId());
            if (template == null || !template.getCeremony().getId().equals(ceremonyId)) {
                throw new ApplicationException(CeremonyErrorCode.TEMPLATE_NOT_FOUND);
            }
            template.updateDisplayOrder(item.getDisplayOrder());
        }

        return findTemplates(organizationId, ceremonyId, currentUserId);
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
        return buildTemplateInfo(template);
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
        return renderPage(template, pageIndex, scale);
    }

    /**
     * accessKey 기반 공개 경로(프로젝터)가 JWT+조직 소속 검사 없이 재사용하는 핵심 로직 —
     * {@link ProjectorService}가 이미 조회해 둔 {@link Template}을 그대로 넘긴다. 두 public
     * 메서드 위와 코드 복제 없이 로직 하나만 공유한다.
     */
    TemplateDto.Response.TemplateInfo buildTemplateInfo(Template template) {
        try (PDDocument document = Loader.loadPDF(readTemplateBytes(template))) {
            Float width = null;
            Float height = null;
            List<TemplateDto.Response.TemplatePageInfo> pages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                TemplateDto.Response.TemplatePageInfo pageInfo = resolvePageInfo(document, pageIndex);
                pages.add(pageInfo);
                if (pageIndex == 0) {
                    width = pageInfo.getWidth();
                    height = pageInfo.getHeight();
                }
            }
            return new TemplateDto.Response.TemplateInfo(document.getNumberOfPages(), width, height, pages);
        } catch (IOException e) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_STORAGE_FAILED, e);
        }
    }

    /**
     * CropBox(MediaBox보다 실제 표시 영역에 가깝다)와 회전을 반영해, 화면에 실제로 찍힐 페이지
     * 크기(pt)를 계산한다. 90도/270도로 회전된 페이지는 가로/세로가 뒤집힌다.
     */
    private TemplateDto.Response.TemplatePageInfo resolvePageInfo(PDDocument document, int pageIndex) {
        PDRectangle cropBox = document.getPage(pageIndex).getCropBox();
        int rotation = Math.floorMod(document.getPage(pageIndex).getRotation(), 360);
        boolean rotated = rotation == 90 || rotation == 270;
        float width = rotated ? cropBox.getHeight() : cropBox.getWidth();
        float height = rotated ? cropBox.getWidth() : cropBox.getHeight();
        return new TemplateDto.Response.TemplatePageInfo(pageIndex, width, height, rotation);
    }

    /**
     * {@link #buildTemplateInfo(Template)}와 같은 이유로 package-private. 같은 template/
     * pageIndex/scale 조합은 {@code documentStoragePort}에 PNG로 캐시해 둔다 — 매 요청마다
     * 원본 PDF를 다시 렌더링하지 않기 위해서다(2026-08-24 legacy 포팅: 템플릿 페이지 이미지
     * 캐시). 캐시 저장/조회가 실패해도 렌더링 자체는 계속 성공해야 하므로, 캐시 관련 예외는
     * 요청을 막지 않고 무시한다 — 원본 렌더링 경로로 자연히 폴백된다.
     */
    byte[] renderPage(Template template, int pageIndex, float scale) {
        String cacheKey = buildPageImageCacheKey(template.getId(), pageIndex, scale);
        try {
            if (documentStoragePort.exists(cacheKey)) {
                return documentStoragePort.loadAsResource(cacheKey).getContentAsByteArray();
            }
        } catch (StorageException | IOException e) {
            // 캐시 조회 실패 — 아래에서 원본 PDF로 다시 렌더링한다.
        }

        byte[] image = renderPageUncached(template, pageIndex, scale);
        try {
            documentStoragePort.storeAt(cacheKey, image);
        } catch (StorageException e) {
            // 캐시 저장 실패 — 이번 요청은 이미 렌더링된 image를 그대로 응답한다.
        }
        return image;
    }

    private byte[] renderPageUncached(Template template, int pageIndex, float scale) {
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

    private String buildPageImageCacheKey(Long templateId, int pageIndex, float scale) {
        return String.format(Locale.ROOT, "template-pages/%d/page-%d@%.2f.png", templateId, pageIndex, scale);
    }

    /**
     * 업로드/복제 직후 자주 쓰이는 배율(프로젝터·행사제어·문서매핑 화면이 공용으로 쓰는
     * {@code MappedDocumentPreview}의 고정 배율)로 페이지 이미지를 미리 렌더링해 캐시를
     * 채워둔다. 실패해도(예: 손상된 PDF, 저장소 일시 오류) 업로드/복제 자체를 막지 않는다 —
     * 다음 조회 시 {@link #renderPage}가 원본에서 다시 렌더링하면 그만이다.
     */
    private void preRenderPageImageCache(Template template) {
        try {
            try (PDDocument document = Loader.loadPDF(readTemplateBytes(template))) {
                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    renderPage(template, pageIndex, PAGE_IMAGE_CACHE_PRERENDER_SCALE);
                }
            }
        } catch (Exception e) {
            // 사전 렌더링은 성능 최적화일 뿐이라 실패해도 업로드/복제 흐름을 막지 않는다.
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
        if (isTemplateLockedByStartedEvent(templateId)) {
            throw new ApplicationException(CeremonyErrorCode.TEMPLATE_LOCKED_BY_EVENT);
        }
        template.updateInfo(request.getTitle(), parseDocumentRole(request.getDocumentRole()));
        return toSummary(template);
    }

    /**
     * 이 문서 양식이 시작(STARTED)됐거나 종료(FINISHED)된 하위 행사에 매핑돼 있으면 잠긴
     * 것으로 본다 — 제목/문서 역할이 바뀌면 이미 진행 중인 현장 운영·결과물과 어긋나기
     * 때문이다. 서명란 자체의 잠금({@link #completeTemplate}, {@code TEMPLATE_LOCKED})과는
     * 별개 조건이다.
     */
    private boolean isTemplateLockedByStartedEvent(Long templateId) {
        return ceremonyTemplateRepository.findAllByTemplateId(templateId).stream()
                .map(ceremonyTemplate -> ceremonyTemplate.getCeremonyEvent().getStatus())
                .anyMatch(status -> status == CeremonyEventStatus.STARTED
                        || status == CeremonyEventStatus.FINISHED
                        || status == CeremonyEventStatus.FORCE_FINISHED);
    }

    /**
     * 하위 행사(CeremonyEvent)에 매핑돼 있는지 — {@link #deleteTemplate}의 차단 조건이자
     * {@link #toSummary}가 목록 화면의 삭제 버튼 노출 여부(deletable)를 계산하는 데도
     * 재사용한다. 상태(STARTED/FINISHED)를 가리지 않는다 — DRAFT/READY 이벤트에 매핑돼 있어도
     * FK가 깨지므로 삭제할 수 없다({@link #isTemplateLockedByStartedEvent}와는 별개 조건).
     */
    private boolean isTemplateInUse(Long templateId) {
        return ceremonyTemplateRepository.existsByTemplateId(templateId);
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
        if (isTemplateInUse(templateId)) {
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
     * 원본 파일과 서명란(TemplateField, signer 매핑 포함)을 그대로 복사해 새 문서 양식을
     * 만든다 — legacy(~/Works/eform/source/signstage/signstage-frontend)
     * TemplateCloneService와 같은 동작(2026-08-21 결정: 복제본을 서명란 0개로 비워두면 배치를
     * 매번 처음부터 다시 해야 해서, 좌표는 그대로 이어받고 검토 후 "설정 완료"만 다시 누르는
     * 쪽으로 정리). 복제본 상태는 {@link Template#complete()}를 호출하지 않으므로 서명란이
     * 있어도 항상 DRAFT로 시작한다 — "설정 완료"는 관리자가 배치를 확인한 뒤 별도로 눌러야
     * 한다. 업로드와 같은 템플릿 개수 한도를 적용한다.
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
        ceremonyService.checkCeremonyPlanConfirmed(ceremony);

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
                .displayOrder((int) currentCount)
                .build();
        templateRepository.save(duplicated);

        List<TemplateField> originalFields = templateFieldRepository.findAllByTemplateId(original.getId());
        if (!originalFields.isEmpty()) {
            List<TemplateField> clonedFields = originalFields.stream()
                    .map(field -> TemplateField.builder()
                            .template(duplicated)
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
            templateFieldRepository.saveAll(clonedFields);
        }

        preRenderPageImageCache(duplicated);

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
                template.getDisplayOrder(),
                fieldCount,
                isTemplateLockedByStartedEvent(template.getId()),
                !isTemplateInUse(template.getId()),
                template.getCreatedAt()
        );
    }

    /** 컨트롤러가 {@code Content-Disposition} 헤더를 만드는 데 필요한 파일명을 함께 돌려준다. */
    public record DownloadedTemplate(Resource resource, String originalFilename) {
    }
}
