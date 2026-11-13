package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.audit.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.audit.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.order.dto.QuoteTemplateDto;
import com.eformworks.signstage.backend.feature.order.entity.EffectivePeriod;
import com.eformworks.signstage.backend.feature.order.entity.QuoteTemplate;
import com.eformworks.signstage.backend.feature.order.entity.SalesOrder;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import com.eformworks.signstage.backend.feature.order.repository.QuoteTemplateRepository;
import com.eformworks.signstage.backend.feature.order.repository.SalesOrderRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 견적 공통 문구 — 기간 겹침 금지, 견적에 쓰인 문구는 고치거나 지우지 못한다({@link QuoteTemplateService}). */
@ExtendWith(MockitoExtension.class)
class QuoteTemplateServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ROLE = "PLATFORM_OPS";
    private static final LocalDate FROM = LocalDate.of(2026, 12, 1);

    @Mock
    private QuoteTemplateRepository quoteTemplateRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PlatformAdminAuditLogRecorder auditLogRecorder;

    @InjectMocks
    private QuoteTemplateService quoteTemplateService;

    @Test
    @DisplayName("등록 — 문구 앞뒤 공백을 지우고 빈 문구는 null로 저장한다")
    void createQuoteTemplate_trimsTexts() {
        allowManage();
        given(quoteTemplateRepository.existsOverlap(FROM, EffectivePeriod.OPEN_END, null)).willReturn(false);
        given(quoteTemplateRepository.save(any(QuoteTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));

        QuoteTemplateDto.Response.AdminQuoteTemplate saved = quoteTemplateService.createQuoteTemplate(
                ADMIN_ID, ROLE, new QuoteTemplateDto.Request.SaveQuoteTemplate("  - 전자서명 제공\n", "   ", FROM, null)
        );

        assertThat(saved.getServiceDescription()).isEqualTo("- 전자서명 제공");
        assertThat(saved.getFooterNotice()).isNull();
        verify(auditLogRecorder).record(eq(ADMIN_ID), eq(PlatformAdminAction.CREATE_QUOTE_TEMPLATE), any(), any(), anyString());
    }

    @Test
    @DisplayName("등록 — 다른 문구와 기간이 겹치면 거부한다")
    void createQuoteTemplate_rejectsOverlap() {
        allowManage();
        given(quoteTemplateRepository.existsOverlap(FROM, EffectivePeriod.OPEN_END, null)).willReturn(true);

        assertThatThrownBy(() -> quoteTemplateService.createQuoteTemplate(
                ADMIN_ID, ROLE, new QuoteTemplateDto.Request.SaveQuoteTemplate("설명", "안내", FROM, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.QUOTE_TEMPLATE_PERIOD_OVERLAP);
        verify(quoteTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리 권한이 없으면 등록할 수 없다")
    void createQuoteTemplate_requiresPermission() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_ORDER_PRICE_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> quoteTemplateService.createQuoteTemplate(
                ADMIN_ID, ROLE, new QuoteTemplateDto.Request.SaveQuoteTemplate("설명", "안내", FROM, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("수정 — 견적에 쓰이지 않은 문구는 자기 자신을 빼고 기간 겹침을 검사한 뒤 고친다")
    void updateQuoteTemplate_updatesUnused() {
        allowManage();
        QuoteTemplate template = template(10L);
        given(quoteTemplateRepository.findById(10L)).willReturn(Optional.of(template));
        given(salesOrderRepository.existsByQuoteTemplateId(10L)).willReturn(false);
        LocalDate to = FROM.plusMonths(1);
        given(quoteTemplateRepository.existsOverlap(FROM, to, 10L)).willReturn(false);

        quoteTemplateService.updateQuoteTemplate(
                10L, ADMIN_ID, ROLE, new QuoteTemplateDto.Request.SaveQuoteTemplate("새 설명", "새 안내", FROM, to)
        );

        assertThat(template.getServiceDescription()).isEqualTo("새 설명");
        assertThat(template.getFooterNotice()).isEqualTo("새 안내");
        assertThat(template.getPeriod().getEffectiveTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("수정·삭제 — 견적에 쓰인 문구는 거부한다")
    void updateAndDelete_rejectUsed() {
        allowManage();
        QuoteTemplate template = template(10L);
        given(quoteTemplateRepository.findById(10L)).willReturn(Optional.of(template));
        given(salesOrderRepository.existsByQuoteTemplateId(10L)).willReturn(true);

        assertThatThrownBy(() -> quoteTemplateService.updateQuoteTemplate(
                10L, ADMIN_ID, ROLE, new QuoteTemplateDto.Request.SaveQuoteTemplate("새 설명", null, FROM, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.QUOTE_TEMPLATE_IN_USE);
        assertThatThrownBy(() -> quoteTemplateService.deleteQuoteTemplate(10L, ADMIN_ID, ROLE))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.QUOTE_TEMPLATE_IN_USE);
        assertThat(template.getServiceDescription()).isEqualTo("설명");
        verify(quoteTemplateRepository, never()).delete(any());
    }

    @Test
    @DisplayName("견적은 공통 문구를 복사하고 어떤 문구를 썼는지 기억한다 — 유효한 문구가 없으면 둘 다 비운다")
    void salesOrder_copiesQuoteTemplate() {
        SalesOrder salesOrder = OrderFixtures.salesOrder(100L, OrderFixtures.customer(1L), OrderFixtures.plan(1L));

        salesOrder.copyQuoteTemplate(template(10L));
        assertThat(salesOrder.getQuoteTemplateId()).isEqualTo(10L);
        assertThat(salesOrder.getServiceDescription()).isEqualTo("설명");
        assertThat(salesOrder.getQuoteFooterNotice()).isEqualTo("안내");

        salesOrder.copyQuoteTemplate(null);
        assertThat(salesOrder.getQuoteTemplateId()).isNull();
        assertThat(salesOrder.getServiceDescription()).isNull();
        assertThat(salesOrder.getQuoteFooterNotice()).isNull();
    }

    private void allowManage() {
        given(rolePermissionService.isAllowed(ROLE, "ACTION_ORDER_PRICE_MANAGE")).willReturn(true);
    }

    private static QuoteTemplate template(Long id) {
        QuoteTemplate template = QuoteTemplate.builder()
                .serviceDescription("설명")
                .footerNotice("안내")
                .period(new EffectivePeriod(FROM, null))
                .build();
        ReflectionTestUtils.setField(template, "id", id);
        return template;
    }
}
