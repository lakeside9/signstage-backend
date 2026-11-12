package com.eformworks.signstage.backend.feature.order.scheduler;

import com.eformworks.signstage.backend.feature.order.service.OrderReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 발주 응답 기한·미수 알림 배치 — 매시 5분(Asia/Seoul). 로직은 {@link OrderReminderService}에 있다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReminderScheduler {

    private final OrderReminderService orderReminderService;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Seoul")
    public void sendReminders() {
        int sent = orderReminderService.sendReminders();
        if (sent > 0) {
            log.info("기한 알림 배치: {}건 알림", sent);
        }
    }
}
