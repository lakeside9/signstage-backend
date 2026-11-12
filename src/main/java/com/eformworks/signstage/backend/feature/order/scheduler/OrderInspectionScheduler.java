package com.eformworks.signstage.backend.feature.order.scheduler;

import com.eformworks.signstage.backend.feature.order.service.OrderInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 검수 생성·승인 간주 배치 — signstage-docs business/platform-order-partner-subcontract-review.md D11.
 * 매일 00:10(Asia/Seoul)에 돈다. 로직은 {@link OrderInspectionService#syncInspections}에 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderInspectionScheduler {

    private final OrderInspectionService orderInspectionService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void syncInspections() {
        int changed = orderInspectionService.syncInspections();
        if (changed > 0) {
            log.info("검수 배치: {}건 생성·승인 간주", changed);
        }
    }
}
