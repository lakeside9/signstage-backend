package com.eformworks.signstage.backend.feature.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link OrderReminderService} 단위 테스트 — 기한 알림을 한 번씩 보내고 보낸 시각을 남긴다. */
@ExtendWith(MockitoExtension.class)
class OrderReminderServiceTest {

    @Mock
    private PartnerOrderRepository partnerOrderRepository;
    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private OrderNotificationService orderNotificationService;

    @InjectMocks
    private OrderReminderService orderReminderService;

    @Test
    @DisplayName("응답 기한 24시간 안의 발주와 입금 기한이 지난 청구를 알리고 보낸 시각을 남긴다")
    void sendReminders_notifiesAndMarks() {
        PartnerOrder dueSoon = mock(PartnerOrder.class);
        CustomerInvoice overdue = mock(CustomerInvoice.class);
        given(partnerOrderRepository.findDueSoonWithoutReminder(any(), any())).willReturn(List.of(dueSoon));
        given(customerInvoiceRepository.findAllByStatusAndDueDateBeforeAndOverdueNotifiedAtIsNull(
                eq(CustomerInvoiceStatus.ISSUED), any())).willReturn(List.of(overdue));

        int sent = orderReminderService.sendReminders();

        assertThat(sent).isEqualTo(2);
        verify(orderNotificationService).partnerOrderDueSoon(dueSoon);
        verify(dueSoon).markDueReminderSent();
        verify(orderNotificationService).invoiceOverdue(overdue);
        verify(overdue).markOverdueNotified();
    }

    @Test
    @DisplayName("발주는 지금부터 24시간 뒤까지, 청구는 오늘(Asia/Seoul) 전 입금 기한으로 찾는다")
    void sendReminders_queriesWindows() {
        given(partnerOrderRepository.findDueSoonWithoutReminder(any(), any())).willReturn(List.of());
        given(customerInvoiceRepository.findAllByStatusAndDueDateBeforeAndOverdueNotifiedAtIsNull(any(), any()))
                .willReturn(List.of());

        int sent = orderReminderService.sendReminders();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(partnerOrderRepository).findDueSoonWithoutReminder(now.capture(), until.capture());
        assertThat(Duration.between(now.getValue(), until.getValue())).isEqualTo(Duration.ofHours(OrderReminderService.DUE_SOON_HOURS));
        verify(customerInvoiceRepository).findAllByStatusAndDueDateBeforeAndOverdueNotifiedAtIsNull(
                CustomerInvoiceStatus.ISSUED, LocalDate.now(SalesOrderService.PLATFORM_ZONE));
        assertThat(sent).isZero();
        verifyNoInteractions(orderNotificationService);
    }
}
