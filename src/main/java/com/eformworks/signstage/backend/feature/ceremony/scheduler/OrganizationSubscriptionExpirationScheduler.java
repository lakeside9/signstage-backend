package com.eformworks.signstage.backend.feature.ceremony.scheduler;

import com.eformworks.signstage.backend.feature.ceremony.service.OrganizationSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조직 구독(PERIOD_AND_COUNT)의 기간 만료를 매일 처리하는 배치 — signstage-docs
 * business/organization-event-discount-pricing-review.md 8.7-2 결정(2026-09-10, 이 프로젝트
 * 최초의 {@code @Scheduled} 배치). 매일 자정(플랫폼 기본 타임존 Asia/Seoul) 실행하고,
 * 실제 판정/전이 로직은 전부 {@link OrganizationSubscriptionService#expireOverdueSubscriptions}에
 * 있다 — 이 클래스는 트리거일 뿐이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationSubscriptionExpirationScheduler {

    private final OrganizationSubscriptionService organizationSubscriptionService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void expireOverdueSubscriptions() {
        int expiredCount = organizationSubscriptionService.expireOverdueSubscriptions();
        if (expiredCount > 0) {
            log.info("조직 구독 만료 배치: {}건 EXPIRED 처리", expiredCount);
        }
    }
}
