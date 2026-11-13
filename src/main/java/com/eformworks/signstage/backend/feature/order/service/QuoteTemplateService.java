package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.PlatformAdminOrderPriceDto;
import com.eformworks.signstage.backend.feature.order.dto.QuoteTemplateDto;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.QuoteTemplate;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.QuoteTemplateRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 견적 공통 문구 관리 — 수주 가격표({@link OrderPriceService})와 같은 규칙이다. 기간은 겹치지 않고, 견적에 쓰인 문구는
 * 고치거나 지우지 못한다(기간을 닫고 새 문구를 만든다). 등록·수정·종료·삭제는 {@code ACTION_ORDER_PRICE_MANAGE}가 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteTemplateService {

    private final QuoteTemplateRepository quoteTemplateRepository;
    private final SalesOrderRepository salesOrderRepository;
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
