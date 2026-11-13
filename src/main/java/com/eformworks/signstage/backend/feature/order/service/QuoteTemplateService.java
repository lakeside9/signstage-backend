package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.ceremony.model.StoredFile;
import com.eformworks.signstage.backend.feature.ceremony.port.DocumentStoragePort;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.dto.QuoteTemplateDto;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.QuoteTemplate;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.QuoteTemplateRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 견적 공통 문구 관리 — 수주 가격표({@link OrderPriceService})와 같은 규칙이다. 기간은 겹치지 않고, 견적에 쓰인 문구는
 * 고치거나 지우지 못한다(기간을 닫고 새 문구를 만든다). 등록·수정·종료·삭제는 {@code ACTION_ORDER_PRICE_MANAGE}가 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteTemplateService {

    private static final String SEAL_DIRECTORY = "quote-seals";
    private static final long SEAL_MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> SEAL_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    private final QuoteTemplateRepository quoteTemplateRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final DocumentStoragePort documentStoragePort;
    private final RolePermissionService rolePermissionService;
    private final PlatformAdminAuditLogRecorder auditLogRecorder;

    public List<QuoteTemplateDto.Response.AdminQuoteTemplate> findQuoteTemplates() {
        return quoteTemplateRepository.findAllOrdered().stream()
                .map(template -> QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, isUsed(template.getId())))
                .toList();
    }

    /** 오늘 새 견적을 만들면 복사될 문구. 없으면 빈 값이다 — 견적 작성 화면이 미리 채우고 없으면 안내한다. */
    public Optional<QuoteTemplateDto.Response.AdminQuoteTemplate> findEffectiveQuoteTemplate() {
        return quoteTemplateRepository.findEffective(LocalDate.now(SalesOrderService.PLATFORM_ZONE))
                .map(template -> QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, isUsed(template.getId())));
    }

    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate createQuoteTemplate(
            Long adminUserId,
            String actingPlatformRole,
            QuoteTemplateDto.Request.SaveQuoteTemplate request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        EffectivePeriod period = new EffectivePeriod(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(period, null);
        QuoteTemplate template = quoteTemplateRepository.save(QuoteTemplate.builder()
                .serviceDescription(trimToNull(request.getServiceDescription()))
                .footerNotice(trimToNull(request.getFooterNotice()))
                .period(period)
                .build());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CREATE_QUOTE_TEMPLATE, null, null,
                "quoteTemplateId=" + template.getId() + ", period=" + period.getEffectiveFrom() + "~" + period.getEffectiveTo());
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, false);
    }

    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate updateQuoteTemplate(
            Long quoteTemplateId,
            Long adminUserId,
            String actingPlatformRole,
            QuoteTemplateDto.Request.SaveQuoteTemplate request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        if (isUsed(quoteTemplateId)) {
            throw new ApplicationException(OrderErrorCode.QUOTE_TEMPLATE_IN_USE);
        }
        EffectivePeriod period = new EffectivePeriod(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(period, quoteTemplateId);
        template.update(trimToNull(request.getServiceDescription()), trimToNull(request.getFooterNotice()), period);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_QUOTE_TEMPLATE, null, null,
                "quoteTemplateId=" + quoteTemplateId + ", period=" + period.getEffectiveFrom() + "~" + period.getEffectiveTo());
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, false);
    }

    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate closeQuoteTemplate(
            Long quoteTemplateId,
            Long adminUserId,
            String actingPlatformRole,
            PlatformAdminOrderPriceDto.Request.ClosePeriod request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        template.close(request.getEffectiveTo());
        auditLogRecorder.record(adminUserId, PlatformAdminAction.CLOSE_QUOTE_TEMPLATE, null, null,
                "quoteTemplateId=" + quoteTemplateId + ", effectiveTo=" + request.getEffectiveTo());
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, isUsed(quoteTemplateId));
    }

    @Transactional
    public void deleteQuoteTemplate(Long quoteTemplateId, Long adminUserId, String actingPlatformRole) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        if (isUsed(quoteTemplateId)) {
            throw new ApplicationException(OrderErrorCode.QUOTE_TEMPLATE_IN_USE);
        }
        quoteTemplateRepository.delete(template);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.DELETE_QUOTE_TEMPLATE, null, null,
                "quoteTemplateId=" + quoteTemplateId + ", effectiveFrom=" + template.getPeriod().getEffectiveFrom());
    }

    /**
     * 견적서 공급자 칸을 저장한다. 견적에 쓰인 문구는 공급자 정보가 아직 비어 있을 때 한 번만 채울 수 있다 — 이미 보낸
     * 견적서의 공급자 칸이 바뀌지 않게 하되, 공급자 칸 없이 만든 문구에 처음 넣는 것은 허용한다.
     */
    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate updateSupplier(
            Long quoteTemplateId,
            Long adminUserId,
            String actingPlatformRole,
            QuoteTemplateDto.Request.SaveSupplier request
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        boolean used = isUsed(quoteTemplateId);
        if (used && template.hasSupplier()) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SUPPLIER_IN_USE);
        }
        template.updateSupplier(trimToNull(request.getName()), trimToNull(request.getRepresentative()),
                trimToNull(request.getBusinessNumber()), trimToNull(request.getAddress()), trimToNull(request.getPhone()));
        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPDATE_QUOTE_SUPPLIER, null, null,
                "quoteTemplateId=" + quoteTemplateId + ", supplierName=" + template.getSupplierName());
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, used);
    }

    /** 회사 인감 이미지를 올린다(PNG·JPEG, 2MB 이하). 쓰인 문구는 인감이 없을 때만 올릴 수 있다. */
    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate uploadSeal(
            Long quoteTemplateId,
            Long adminUserId,
            String actingPlatformRole,
            MultipartFile file
    ) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        boolean used = isUsed(quoteTemplateId);
        if (used && template.hasSeal()) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SUPPLIER_IN_USE);
        }
        String contentType = checkSealImage(file);
        String previousKey = template.getSealStorageKey();
        StoredFile stored = documentStoragePort.store(SEAL_DIRECTORY, file);
        template.useSeal(stored.storageKey(), contentType);
        if (previousKey != null) {
            documentStoragePort.delete(previousKey);
        }
        auditLogRecorder.record(adminUserId, PlatformAdminAction.UPLOAD_QUOTE_SEAL, null, null,
                "quoteTemplateId=" + quoteTemplateId + ", bytes=" + file.getSize());
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, used);
    }

    /** 인감을 지운다 — 견적에 쓰이지 않은 문구만. */
    @Transactional
    public QuoteTemplateDto.Response.AdminQuoteTemplate deleteSeal(Long quoteTemplateId, Long adminUserId, String actingPlatformRole) {
        OrderPriceService.checkCanManagePrices(rolePermissionService, actingPlatformRole);
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        if (isUsed(quoteTemplateId)) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SUPPLIER_IN_USE);
        }
        if (!template.hasSeal()) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SEAL_NOT_FOUND);
        }
        String storageKey = template.getSealStorageKey();
        template.useSeal(null, null);
        documentStoragePort.delete(storageKey);
        auditLogRecorder.record(adminUserId, PlatformAdminAction.DELETE_QUOTE_SEAL, null, null, "quoteTemplateId=" + quoteTemplateId);
        return QuoteTemplateDto.Response.AdminQuoteTemplate.from(template, false);
    }

    /** 인감 이미지 — 플랫폼 관리자 인증 API로만 내려준다. */
    public SealImage loadSeal(Long quoteTemplateId) {
        QuoteTemplate template = findOrThrow(quoteTemplateId);
        if (!template.hasSeal()) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SEAL_NOT_FOUND);
        }
        return new SealImage(documentStoragePort.loadAsResource(template.getSealStorageKey()), template.getSealContentType());
    }

    /** 견적서 공급자 칸 — 견적을 저장할 때 쓴 공통 문구의 값이다. 공통 문구 없이 저장된 견적은 빈 값이다. */
    public QuoteTemplateDto.Response.QuoteSupplier findSupplierForSalesOrder(Long salesOrderId) {
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.SALES_ORDER_NOT_FOUND));
        QuoteTemplate template = salesOrder.getQuoteTemplateId() != null
                ? quoteTemplateRepository.findById(salesOrder.getQuoteTemplateId()).orElse(null)
                : null;
        return QuoteTemplateDto.Response.QuoteSupplier.from(template);
    }

    public record SealImage(Resource resource, String contentType) {
    }

    private static String checkSealImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (file.isEmpty() || file.getSize() > SEAL_MAX_BYTES || contentType == null || !SEAL_CONTENT_TYPES.contains(contentType)) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SEAL_INVALID);
        }
        // 확장자·Content-Type만 믿지 않고 실제 이미지로 읽히는지 본다.
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new ApplicationException(OrderErrorCode.QUOTE_SEAL_INVALID);
            }
        } catch (IOException e) {
            throw new ApplicationException(OrderErrorCode.QUOTE_SEAL_INVALID);
        }
        return contentType;
    }

    private QuoteTemplate findOrThrow(Long quoteTemplateId) {
        return quoteTemplateRepository.findById(quoteTemplateId)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.QUOTE_TEMPLATE_NOT_FOUND));
    }

    private void checkNoOverlap(EffectivePeriod period, Long excludeId) {
        if (quoteTemplateRepository.existsOverlap(period.getEffectiveFrom(), period.effectiveToOrOpenEnd(), excludeId)) {
            throw new ApplicationException(OrderErrorCode.QUOTE_TEMPLATE_PERIOD_OVERLAP);
        }
    }

    private boolean isUsed(Long quoteTemplateId) {
        return salesOrderRepository.existsByQuoteTemplateId(quoteTemplateId);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
