package com.eformworks.signstage.backend.feature.order.entity;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.order.error.OrderErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가격표의 유효 기간. 시작일·종료일 모두 포함하고, 종료일이 없으면 열린 기간이다
 * ({@code UnitProductPricePeriod}와 같은 규칙). 가격은 고치지 않고 기간을 닫은 뒤 새 기간을
 * 만든다 — 이미 만든 수주·발주는 금액을 스냅샷하므로 영향받지 않는다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EffectivePeriod {

    /** 겹침 검사에서 열린 종료일 대신 쓰는 값. */
    public static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public EffectivePeriod(LocalDate effectiveFrom, LocalDate effectiveTo) {
        checkOrder(effectiveFrom, effectiveTo);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public EffectivePeriod close(LocalDate effectiveTo) {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    public LocalDate effectiveToOrOpenEnd() {
        return effectiveTo != null ? effectiveTo : OPEN_END;
    }

    private static void checkOrder(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom == null || (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))) {
            throw new ApplicationException(OrderErrorCode.PRICE_PERIOD_INVALID);
        }
    }
}
