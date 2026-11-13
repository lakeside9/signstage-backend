package com.eformworks.signstage.backend.feature.billing.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingPlanUnitProductTest {

    @Test
    @DisplayName("포함 수량은 묶음 수 — 실제 포함 개수는 판매 단위를 곱한다")
    void totalQuantity_multipliesSaleUnit() {
        UnitProduct templates = mock(UnitProduct.class);
        when(templates.getSaleUnitQuantity()).thenReturn(10);

        BillingPlanUnitProduct line = BillingPlanUnitProduct.builder().unitProduct(templates).includedQuantity(2).build();

        assertThat(line.totalQuantity()).isEqualTo(20);
    }
}
