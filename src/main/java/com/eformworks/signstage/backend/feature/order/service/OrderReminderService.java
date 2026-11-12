package com.eformworks.signstage.backend.feature.order.service;

import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoice;
import com.eformworks.signstage.backend.feature.order.entity.CustomerInvoiceStatus;
import com.eformworks.signstage.backend.feature.order.entity.PartnerOrder;
import com.eformworks.signstage.backend.feature.order.repository.CustomerInvoiceRepository;
import com.eformworks.signstage.backend.feature.order.repository.PartnerOrderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기한 알림 — 발주 응답 기한 24시간 전(파트너)과 입금 기한이 지난 청구(플랫폼)를 한 번씩 알린다
 * (signstage-docs business/platform-order-partner-subcontract-review.md 12장). 보낸 시각을 남겨 다시
 * 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderReminderService {

    static final int DUE_SOON_HOURS = 24;

    private final PartnerOrderRepository partnerOrderRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final OrderNotificationService orderNotificationService;

    /** 보낸 알림 건수(발주·청구 기준)를 돌려준다. */
    @Transactional
    public int sendReminders() {
        LocalDateTime now = LocalDateTime.now(SalesOrderService.PLATFORM_ZONE);
        List<PartnerOrder> dueSoon = partnerOrderRepository.findDueSoonWithoutReminder(now, now.plusHours(DUE_SOON_HOURS));
        for (PartnerOrder order : dueSoon) {
            orderNotificationService.partnerOrderDueSoon(order);
            order.markDueReminderSent();
        }
        List<CustomerInvoice> overdue = customerInvoiceRepository.findAllByStatusAndDueDateBeforeAndOverdueNotifiedAtIsNull(
                CustomerInvoiceStatus.ISSUED, LocalDate.now(SalesOrderService.PLATFORM_ZONE)
        );
        for (CustomerInvoice invoice : overdue) {
            orderNotificationService.invoiceOverdue(invoice);
            invoice.markOverdueNotified();
        }
        return dueSoon.size() + overdue.size();
    }
}
