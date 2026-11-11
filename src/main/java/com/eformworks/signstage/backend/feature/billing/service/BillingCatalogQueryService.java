package com.eformworks.signstage.backend.feature.billing.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanDiscountPeriod;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlanUnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.billing.error.BillingErrorCode;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanDiscountPeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanRepository;
import com.eformworks.signstage.backend.feature.billing.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.billing.repository.UnitProductRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 feature(주로 ceremony)가 과금 카탈로그를 읽을 때 쓰는 조회 전용 서비스. 다른 feature는
 * billing Repository를 직접 쓰지 않고 이 서비스를 거친다(backend-coding-convention.md 3.4절,
 * backend-package-structure-consistency-review.md 11.8절).
 *
 * <p>판매 가능 여부·통화 같은 업무 판정은 하지 않고 조회만 한다. 판정은 호출하는 쪽
 * (예: {@code CeremonyCatalogService})이 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingCatalogQueryService {

    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final BillingPlanDiscountPeriodRepository billingPlanDiscountPeriodRepository;
    private final UnitProductRepository unitProductRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;

    public BillingPlan findBillingPlanOrThrow(Long billingPlanId) {
        return billingPlanRepository.findById(billingPlanId)
                .orElseThrow(() -> new ApplicationException(BillingErrorCode.BILLING_PLAN_NOT_FOUND));
    }

    /** 플랜에 포함된 단위 상품 구성(플랜×단위 상품 줄). */
    public List<BillingPlanUnitProduct> findPlanUnitProducts(Long billingPlanId) {
        return billingPlanUnitProductRepository.findAllByBillingPlanId(billingPlanId);
    }

    /** {@code asOfDate}에 유효한 플랜 할인 기간. 판매 기간이 아니면 비어 있다. */
    public Optional<BillingPlanDiscountPeriod> findEffectivePlanDiscountPeriod(Long billingPlanId, LocalDate asOfDate) {
        return billingPlanDiscountPeriodRepository.findEffective(billingPlanId, asOfDate);
    }

    public UnitProduct findUnitProductOrThrow(Long unitProductId) {
        return unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(BillingErrorCode.UNIT_PRODUCT_NOT_FOUND));
    }

    /** 없는 id는 결과에서 빠진다. 순서는 보장하지 않는다. */
    public List<UnitProduct> findUnitProducts(List<Long> unitProductIds) {
        return unitProductRepository.findAllById(unitProductIds);
    }

    public List<UnitProduct> findAllUnitProducts() {
        return unitProductRepository.findAll();
    }

    /** 해당 종류의 단위 상품 중 id가 가장 작은 것. */
    public Optional<UnitProduct> findFirstUnitProductByType(UnitProductType type) {
        return unitProductRepository.findFirstByTypeOrderByIdAsc(type);
    }

    /** {@code asOfDate}에 유효한 단위 상품 가격 기간. 판매 기간이 아니면 비어 있다. */
    public Optional<UnitProductPricePeriod> findEffectiveUnitProductPricePeriod(Long unitProductId, LocalDate asOfDate) {
        return unitProductPricePeriodRepository.findEffective(unitProductId, asOfDate);
    }
}
