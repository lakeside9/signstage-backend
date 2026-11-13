package com.eformworks.signstage.backend.feature.order.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 고객 취소 수수료(C03)와 파트너 취소 보상(C04) 비율 — signstage-docs business/platform-order-pending-items.md.
 * 행사 시작일까지 남은 일수로 구간을 고른다. 구간은 "최소 남은 일수:비율(%)"을 쉼표로 잇는다 — 예를 들어
 * {@code 30:0,14:30,7:50,0:100}이면 30일 이상 남았으면 0%, 14일 이상 30%, 7일 이상 50%, 그보다 가까우면 100%다.
 * 행사일이 지났으면(남은 일수가 음수) 가장 가까운 구간을 쓴다. 행사일이 없으면 0%다.
 *
 * <p>기본값은 고객 약관·파트너 기본계약 확정 전 가정값이다. 약관이 정해지면 설정
 * {@code signstage.platform-order.customer-cancellation-fee-tiers}, {@code partner-cancellation-compensation-tiers}로 바꾼다.
 */
@Component
public class CustomerCancellationPolicy {

    private final List<Tier> feeTiers;
    private final List<Tier> compensationTiers;

    public CustomerCancellationPolicy(
            @Value("${signstage.platform-order.customer-cancellation-fee-tiers:30:0,14:30,7:50,0:100}") String feeTiers,
            @Value("${signstage.platform-order.partner-cancellation-compensation-tiers:14:0,7:20,0:50}") String compensationTiers
    ) {
        this.feeTiers = parse(feeTiers);
        this.compensationTiers = parse(compensationTiers);
    }

    /** 고객 취소 수수료 비율(%) — 계약 공급가에 곱한다. */
    public BigDecimal feeRatePercent(Long daysBeforeEvent) {
        return rate(feeTiers, daysBeforeEvent);
    }

    /** 파트너 취소 보상 비율(%) — 수락한 발주 금액에 곱한다. */
    public BigDecimal compensationRatePercent(Long daysBeforeEvent) {
        return rate(compensationTiers, daysBeforeEvent);
    }

    private static BigDecimal rate(List<Tier> tiers, Long daysBeforeEvent) {
        if (daysBeforeEvent == null) {
            return BigDecimal.ZERO;
        }
        return tiers.stream()
                .filter(tier -> daysBeforeEvent >= tier.minDays())
                .findFirst()
                .orElse(tiers.getLast())
                .ratePercent();
    }

    private static List<Tier> parse(String value) {
        List<Tier> tiers = Arrays.stream(value.split(","))
                .map(String::trim)
                .map(part -> {
                    String[] pair = part.split(":");
                    if (pair.length != 2) {
                        throw new IllegalArgumentException("cancellation tier must be 'minDays:percent': " + part);
                    }
                    BigDecimal percent = new BigDecimal(pair[1].trim());
                    if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                        throw new IllegalArgumentException("cancellation tier percent must be in [0, 100]: " + part);
                    }
                    return new Tier(Long.parseLong(pair[0].trim()), percent);
                })
                .sorted(Comparator.comparingLong(Tier::minDays).reversed())
                .toList();
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("cancellation tiers must not be empty");
        }
        return tiers;
    }

    private record Tier(long minDays, BigDecimal ratePercent) {
    }
}
