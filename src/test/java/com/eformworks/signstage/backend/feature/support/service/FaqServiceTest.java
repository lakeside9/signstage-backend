package com.eformworks.signstage.backend.feature.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import com.eformworks.signstage.backend.feature.support.dto.FaqDto;
import com.eformworks.signstage.backend.feature.support.entity.Faq;
import com.eformworks.signstage.backend.feature.support.error.SupportErrorCode;
import com.eformworks.signstage.backend.feature.support.repository.FaqRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link FaqService} 단위 테스트 — signstage-docs business/partner-support-center-review.md 4장. */
@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqRepository faqRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    @Mock
    private RolePermissionService rolePermissionService;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqRepository, platformAdminAuditLogRecorder, rolePermissionService);
        lenient().when(rolePermissionService.isAllowed("PLATFORM_OPS", "ACTION_FAQ_MANAGE")).thenReturn(true);
    }

    private Faq faq(long id, int displayOrder) {
        Faq faq = Faq.builder().category("일반").question("질문").answer("답변").displayOrder(displayOrder).build();
        ReflectionTestUtils.setField(faq, "id", id);
        return faq;
    }

    @Test
    @DisplayName("PLATFORM_OPS 미만 등급은 FAQ를 등록할 수 없다")
    void createFaq_insufficientRole_fail() {
        given(rolePermissionService.isAllowed("PLATFORM_SUPPORT", "ACTION_FAQ_MANAGE")).willReturn(false);

        assertThatThrownBy(() -> faqService.createFaq(
                "PLATFORM_SUPPORT", 1L, new FaqDto.Request.CreateFaq("일반", "질문", "답변")
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        verify(faqRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 — 기존 최대 표시 순서 + 10을 부여한다")
    void createFaq_assignsNextDisplayOrder() {
        given(faqRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(List.of(faq(1L, 10), faq(2L, 20)));
        given(faqRepository.save(any())).willAnswer(invocation -> {
            Faq saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        FaqDto.Response.FaqSummary response =
                faqService.createFaq("PLATFORM_OPS", 1L, new FaqDto.Request.CreateFaq("일반", "새 질문", "새 답변"));

        assertThat(response.getDisplayOrder()).isEqualTo(30);
    }

    @Test
    @DisplayName("표시 순서 일괄 변경 — 요청 id 집합이 전체 목록과 다르면 거부된다")
    void updateDisplayOrders_mismatchedIds_fail() {
        given(faqRepository.findAllByOrderByDisplayOrderAscIdAsc()).willReturn(List.of(faq(1L, 10), faq(2L, 20)));

        DisplayOrderRequest.UpdateDisplayOrders request = new DisplayOrderRequest.UpdateDisplayOrders(
                List.of(new DisplayOrderRequest.Item(1L, 10), new DisplayOrderRequest.Item(999L, 20))
        );

        assertThatThrownBy(() -> faqService.updateDisplayOrders("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(SupportErrorCode.FAQ_ORDER_GROUP_MISMATCH);
    }
}
