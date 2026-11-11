package com.eformworks.signstage.backend.feature.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.error.BillingErrorCode;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BillingCatalogQueryService} 단위 테스트 — 단건 조회 실패 시 오류 코드. 나머지 메서드는
 * Repository 위임이라 ceremony 서비스 테스트({@code CeremonyServiceTestSupport})에서 함께 검증된다.
 */
@ExtendWith(MockitoExtension.class)
class BillingCatalogQueryServiceTest {

    @Mock
    private BillingPlanRepository billingPlanRepository;
    @Mock
    private BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    @Mock
    private BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    @Mock
    private UnitProductRepository unitProductRepository;
    @Mock
    private UnitProductPricePeriodRepository unitProductPricePeriodRepository;

    @InjectMocks
    private BillingCatalogQueryService billingCatalogQueryService;

    @Test
    @DisplayName("플랜 단건 조회 — 있으면 돌려주고 없으면 BILLING_PLAN_NOT_FOUND")
    void findBillingPlanOrThrow() {
        BillingPlan plan = BillingPlan.builder().build();
        given(billingPlanRepository.findById(1L)).willReturn(Optional.of(plan));
        given(billingPlanRepository.findById(2L)).willReturn(Optional.empty());

        assertThat(billingCatalogQueryService.findBillingPlanOrThrow(1L)).isSameAs(plan);
        assertThatThrownBy(() -> billingCatalogQueryService.findBillingPlanOrThrow(2L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.BILLING_PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("단위 상품 단건 조회 — 있으면 돌려주고 없으면 UNIT_PRODUCT_NOT_FOUND")
    void findUnitProductOrThrow() {
        UnitProduct unitProduct = UnitProduct.builder().build();
        given(unitProductRepository.findById(1L)).willReturn(Optional.of(unitProduct));
        given(unitProductRepository.findById(2L)).willReturn(Optional.empty());

        assertThat(billingCatalogQueryService.findUnitProductOrThrow(1L)).isSameAs(unitProduct);
        assertThatThrownBy(() -> billingCatalogQueryService.findUnitProductOrThrow(2L))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(BillingErrorCode.UNIT_PRODUCT_NOT_FOUND);
    }
}
