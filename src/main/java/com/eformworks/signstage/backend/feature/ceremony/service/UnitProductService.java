package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.core.i18n.InternationalizationDefaults;
import com.eformworks.signstage.backend.feature.ceremony.dto.DisplayOrderRequest;
import com.eformworks.signstage.backend.feature.ceremony.dto.UnitProductDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinition;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEffectDefinitionOption;
import com.eformworks.signstage.backend.feature.ceremony.entity.ProductPriceInfo;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriod;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductPricePeriodHistory;
import com.eformworks.signstage.backend.feature.ceremony.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.BillingPlanUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionOptionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEffectDefinitionRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventOptionalFeatureRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyPlanHistoryUnitProductRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyUnitProductPurchaseLineRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductPricePeriodRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.UnitProductRepository;
import com.eformworks.signstage.backend.feature.permission.service.RolePermissionService;
import com.eformworks.signstage.backend.feature.platformadmin.entity.PlatformAdminAction;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 단위 상품(서명자/템플릿 문서/테스트·리허설·본 행사/태블릿/현장지원/온라인지원/이벤트 효과 묶음)
 * — signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정
 * (2026-09-10)에 따라 {@code OptionalFeatureService}와 {@code CapacityAddOnService}를 하나로
 * 합친 자리다. 등록은 플랫폼 관리자 전용, 조회는 인증된 사용자 누구나 가능하다(행사 생성 화면에서
 * 상품을 고를 때 필요).
 *
 * <p>{@code type}은 {@code unit_products.type}에 UNIQUE 제약을 두지 않는다(같은 문서 §3.1 결정)
 * — {@code EVENT_EFFECT_BUNDLE}처럼 관리자가 계속 새 상품 행을 만들 수 있어야 하는 종류가 있어서,
 * 옛 두 서비스에 있던 코드/유형 중복 검사를 이 서비스는 아예 두지 않는다.
 *
 * <p>가격정보는 이 서비스가 {@link UnitProductPricePeriod} 기간 단위 CRUD로 관리한다
 * (signstage-docs business/billing-catalog-price-validity-period-review.md 결정, 2026-09-09,
 * 다중버전 채택) — {@code BillingPlanService}의 기간 CRUD와 같은 패턴. {@link ProductPriceInfo}는
 * 할인 필드가 없다 — 단위 상품은 할인을 갖지 않는다(할인은 오직 BillingPlan에만 있다, 같은 문서
 * §3.5 결정).
 *
 * <p><b>{@code effectDefinitionIds} 읽기/쓰기 완성(2단계, 2026-09-10)</b> —
 * {@code CeremonyEffectDefinitionOption}이 이미 {@code unit_product_id}를 참조하도록 전환돼
 * 있었는데(2026-09-10 카탈로그 재설계), 단위 상품 삭제 기능 추가 작업 중 두 가지 결함을
 * 발견했다: {@code toSummary}의 {@code usageCount}/{@code effectDefinitionIds}가 항상
 * 0/빈 배열을 내려주던 것(읽기, 그때 고침), 생성/수정 요청이 {@code effectDefinitionIds}를
 * 실어 보내도 {@link #checkEffectDefinitionIdsAllowed}가 항상 거부해 이 매핑을 실제로 만드는
 * API가 코드베이스 어디에도 없던 것(쓰기, 이번에 고침 — 사용자가 이벤트 효과 묶음 등록 시
 * "EVENT_EFFECT_BUNDLE 종류에서만 지정할 수 있다"는 오류를 겪어 발견됨). 지금은
 * {@code type=EVENT_EFFECT_BUNDLE}일 때만 값을 허용하고(그 외 타입에 비어있지 않은 값이
 * 오면 거부), 생성 시 그대로 저장, 수정 시 통째로 교체(delete-all-then-recreate,
 * {@link UnitProductDto.Request.UpdateUnitProduct} javadoc 참고)한다. 삭제
 * ({@link #deleteUnitProduct})는 이 매핑을 포함해 6곳 어디에도 사용된 적이 없어야만 허용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitProductService {

    private final UnitProductRepository unitProductRepository;
    private final UnitProductHistoryRepository unitProductHistoryRepository;
    private final UnitProductPricePeriodRepository unitProductPricePeriodRepository;
    private final UnitProductPricePeriodHistoryRepository unitProductPricePeriodHistoryRepository;
    private final BillingPlanUnitProductRepository billingPlanUnitProductRepository;
    private final BillingPlanHistoryUnitProductRepository billingPlanHistoryUnitProductRepository;
    private final CeremonyPlanHistoryUnitProductRepository ceremonyPlanHistoryUnitProductRepository;
    private final CeremonyUnitProductPurchaseLineRepository ceremonyUnitProductPurchaseLineRepository;
    private final CeremonyEventOptionalFeatureRepository ceremonyEventOptionalFeatureRepository;
    private final CeremonyEffectDefinitionOptionRepository ceremonyEffectDefinitionOptionRepository;
    private final CeremonyEffectDefinitionRepository ceremonyEffectDefinitionRepository;
    private final PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;
    private final RolePermissionService rolePermissionService;

    @Transactional
    public UnitProductDto.Response.UnitProductSummary createUnitProduct(
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.CreateUnitProduct request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        UnitProductType type = parseType(request.getType());
        checkEffectDefinitionIdsAllowed(type, request.getEffectDefinitionIds());
        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());

        UnitProduct unitProduct = UnitProduct.builder()
                .type(type)
                .name(request.getName())
                .category(parseCategory(request.getCategory()))
                .exclusivityGroup(request.getExclusivityGroup())
                .build();
        unitProductRepository.save(unitProduct);
        recordProductHistory(unitProduct);
        saveEffectDefinitionOptions(unitProduct, request.getEffectDefinitionIds());

        UnitProductPricePeriod period = UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        unitProductPricePeriodRepository.save(period);
        recordPeriodHistory(unitProduct, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId,
                PlatformAdminAction.CREATE_UNIT_PRODUCT,
                null,
                null,
                "unitProductId=" + unitProduct.getId() + ", type=" + unitProduct.getType()
        );

        return toSummary(unitProduct);
    }

    @Transactional
    public UnitProductDto.Response.UnitProductSummary updateUnitProduct(
            Long unitProductId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.UpdateUnitProduct request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        UnitProduct unitProduct = unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        checkEffectDefinitionIdsAllowed(unitProduct.getType(), request.getEffectDefinitionIds());

        String detail = "unitProductId=" + unitProductId
                + ", name: " + unitProduct.getName() + " -> " + request.getName();

        unitProduct.updateInfo(request.getName(), parseCategory(request.getCategory()), request.getExclusivityGroup());
        recordProductHistory(unitProduct);

        // null이면(생략) 기존 구성을 그대로 두고, 값이 오면(빈 배열 포함) 통째로 교체한다
        // (UnitProductDto.Request.UpdateUnitProduct javadoc 참고). flush()가 반드시 필요하다 —
        // deleteAllByUnitProductId는 파생 delete 쿼리라 DELETE SQL이 즉시 나가지 않는데,
        // CeremonyEffectDefinitionOption은 IDENTITY 채번이라 재저장 시 즉시 INSERT를 실행한다.
        // 그 사이 flush가 없으면 이번 교체에도 그대로 남는 효과(같은 effect_definition_id+
        // unit_product_id 조합)의 INSERT가 아직 DB에 남은 옛 행과 충돌해
        // uq_cedo_definition_product 유니크 제약 위반으로 실패한다(BillingPlanService#updatePlan/
        // CeremonyEventService#applyOptionalFeatures와 같은 패턴, 2026-09-10에 발견해 고친 버그).
        if (request.getEffectDefinitionIds() != null) {
            ceremonyEffectDefinitionOptionRepository.deleteAllByUnitProductId(unitProductId);
            ceremonyEffectDefinitionOptionRepository.flush();
            saveEffectDefinitionOptions(unitProduct, request.getEffectDefinitionIds());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null, detail
        );

        return toSummary(unitProduct);
    }

    /**
     * 단위 상품을 완전히 삭제한다 — 사용한 적이 전혀 없는 상품만 지울 수 있다(signstage-docs
     * business/billing-catalog-unit-product-model-redesign-review.md 결정, 2026-09-10 삭제
     * 기능 추가). "사용"은 {@link #checkNeverUsed}가 6곳(현재 플랜 구성/플랜 구성 이력/행사
     * 플랜 스냅샷/추가구매/행사 적용/이벤트 효과 묶음 매핑)을 전부 확인해 판정한다 — 이미 다른
     * 곳에서 참조된 적이 있으면 소급 삭제로 그 기록의 정합성이 깨지므로 하나라도 걸리면 거부한다.
     * 통과하면 이 상품 자신의 가격 기간/가격 기간 이력/상품 이력까지 함께 지운다(FK 위반 없이
     * 부모 행을 지우려면 자식부터 지워야 한다) — append-only 이력이라도 "그 상품이 아예 없었던
     * 것"으로 완전히 정리하는 게 맞다(어떤 실사용 기록도 참조하지 않는 이력이라 보존할 가치가
     * 없다).
     */
    @Transactional
    public void deleteUnitProduct(Long unitProductId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        UnitProduct unitProduct = unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        checkNeverUsed(unitProductId);

        unitProductPricePeriodHistoryRepository.deleteAllByUnitProductId(unitProductId);
        unitProductPricePeriodRepository.deleteAllByUnitProductId(unitProductId);
        unitProductHistoryRepository.deleteAllByUnitProductId(unitProductId);
        unitProductRepository.delete(unitProduct);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.DELETE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", name=" + unitProduct.getName()
        );
    }

    /** 새 판매가격 기간을 추가한다. */
    @Transactional
    public UnitProductDto.Response.UnitProductPeriodSummary createPeriod(
            Long unitProductId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.CreatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        UnitProduct unitProduct = unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        LocalDate effectiveFrom = resolveEffectiveFrom(request.getEffectiveFrom());
        checkPeriodValid(effectiveFrom, request.getEffectiveTo());
        checkNoOverlap(unitProductId, null, effectiveFrom, request.getEffectiveTo());

        UnitProductPricePeriod period = UnitProductPricePeriod.builder()
                .unitProduct(unitProduct)
                .currencyCode(request.getCurrencyCode())
                .supplyPrice(request.getSupplyPrice())
                .salePrice(request.getSalePrice())
                .taxCode(request.getTaxCode())
                .active(request.getActive())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(request.getEffectiveTo())
                .build();
        unitProductPricePeriodRepository.save(period);
        recordPeriodHistory(unitProduct, period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", 판매가격 기간 생성: " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 이미 있는 판매가격 기간 하나를 고친다. */
    @Transactional
    public UnitProductDto.Response.UnitProductPeriodSummary updatePeriod(
            Long unitProductId,
            Long periodId,
            String actingPlatformRole,
            Long adminUserId,
            UnitProductDto.Request.UpdatePeriod request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        UnitProductPricePeriod period = unitProductPricePeriodRepository
                .findByIdAndUnitProductId(periodId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        checkPeriodValid(request.getEffectiveFrom(), request.getEffectiveTo());
        checkNoOverlap(unitProductId, periodId, request.getEffectiveFrom(), request.getEffectiveTo());

        String previous = describe(period);
        period.update(
                request.getCurrencyCode(), request.getSupplyPrice(), request.getSalePrice(),
                request.getTaxCode(), request.getActive(), request.getEffectiveFrom(), request.getEffectiveTo()
        );
        recordPeriodHistory(period.getUnitProduct(), period, false);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", periodId=" + periodId + ", 판매가격 기간: "
                        + previous + " -> " + describe(period)
        );
        return toPeriodSummary(period);
    }

    /** 판매가격 기간을 제거한다 — 마지막 남은 기간 하나는 지울 수 없다. */
    @Transactional
    public void removePeriod(Long unitProductId, Long periodId, String actingPlatformRole, Long adminUserId) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");
        unitProductRepository.findById(unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND));
        UnitProductPricePeriod period = unitProductPricePeriodRepository
                .findByIdAndUnitProductId(periodId, unitProductId)
                .orElseThrow(() -> new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_NOT_FOUND));
        if (unitProductPricePeriodRepository.countByUnitProductId(unitProductId) <= 1) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_REQUIRED);
        }

        recordPeriodHistory(period.getUnitProduct(), period, true);
        unitProductPricePeriodRepository.delete(period);

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.UPDATE_UNIT_PRODUCT, null, null,
                "unitProductId=" + unitProductId + ", periodId=" + periodId + ", 판매가격 기간 제거: " + describe(period)
        );
    }

    /** 이 단위 상품의 판매가격 기간 전체(과거/현재/예정) — 오래된 순. */
    public List<UnitProductDto.Response.UnitProductPeriodSummary> findUnitProductPeriods(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductPricePeriodRepository.findAllByUnitProductIdOrderByEffectiveFromAsc(unitProductId).stream()
                .map(this::toPeriodSummary)
                .toList();
    }

    /** 판매가격 기간의 생성/수정/삭제 이력 — 최신순. */
    public List<UnitProductDto.Response.UnitProductPeriodHistorySummary> findUnitProductPeriodHistory(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductPricePeriodHistoryRepository.findAllByUnitProductIdOrderByCreatedAtDesc(unitProductId).stream()
                .map(h -> new UnitProductDto.Response.UnitProductPeriodHistorySummary(
                        h.getId(),
                        h.getPriceInfo().getCurrencyCode(),
                        h.getPriceInfo().getSupplyPrice(),
                        h.getPriceInfo().getSalePrice(),
                        h.getPriceInfo().getTaxCode(),
                        h.isActive(),
                        h.getEffectiveFrom(),
                        h.getEffectiveTo(),
                        h.isRemoved(),
                        h.getCreatedBy(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    /**
     * {@code EVENT_EFFECT_BUNDLE} 종류가 아닌데 비어있지 않은 {@code effectDefinitionIds}가
     * 오면 거부한다 — 옛 {@code OptionalFeatureService#checkEffectDefinitionIdsAllowed}와 같은
     * 규칙(생략/빈 배열은 항상 허용, 값이 있을 때만 타입을 검사한다).
     */
    private void checkEffectDefinitionIdsAllowed(UnitProductType type, List<Long> effectDefinitionIds) {
        if (effectDefinitionIds != null && !effectDefinitionIds.isEmpty() && type != UnitProductType.EVENT_EFFECT_BUNDLE) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_EFFECT_BUNDLE_ONLY);
        }
    }

    /**
     * {@code checkEffectDefinitionIdsAllowed}를 통과한 뒤 호출한다 — 생략/빈 배열이면 아무것도
     * 만들지 않는다(생성 시 "빈 묶음으로 시작"). {@code CeremonyEffectDefinitionOption}은 효과
     * 하나가 여러 묶음에 속할 수 있는 N:N 매핑이라, 여기서 만드는 행은 이 단위 상품 쪽만 새로
     * 추가한다 — 이미 다른 묶음에 속한 효과라도 그대로 둔 채 이 상품에도 추가된다.
     */
    private void saveEffectDefinitionOptions(UnitProduct unitProduct, List<Long> effectDefinitionIds) {
        if (effectDefinitionIds == null || effectDefinitionIds.isEmpty()) {
            return;
        }
        for (CeremonyEffectDefinition definition : resolveEffectDefinitions(effectDefinitionIds)) {
            ceremonyEffectDefinitionOptionRepository.save(
                    CeremonyEffectDefinitionOption.builder().effectDefinition(definition).unitProduct(unitProduct).build()
            );
        }
    }

    /** 중복 없이 전부 존재하는지 확인하고 {@code CeremonyEffectDefinition} 목록으로 정규화한다. */
    private List<CeremonyEffectDefinition> resolveEffectDefinitions(List<Long> effectDefinitionIds) {
        List<Long> distinctIds = effectDefinitionIds.stream().distinct().toList();
        if (distinctIds.size() != effectDefinitionIds.size()) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
        List<CeremonyEffectDefinition> found = ceremonyEffectDefinitionRepository.findAllById(distinctIds);
        if (found.size() != distinctIds.size()) {
            throw new ApplicationException(CeremonyErrorCode.EFFECT_DEFINITION_NOT_FOUND);
        }
        return found;
    }

    public List<UnitProductDto.Response.UnitProductSummary> findUnitProducts() {
        return unitProductRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 카탈로그 목록의 위/아래 이동 버튼이 호출한다 — {@code Signer}/{@code Template}/
     * {@code CeremonyEvent}와 같은 패턴이다({@link DisplayOrderRequest.UpdateDisplayOrders}
     * javadoc 참고): 전체 목록을 원하는 순서로 다시 인덱싱해 통째로 보낸다. displayOrder
     * 변경은 {@link UnitProductHistory}에 남기지 않는다 — 이름/분류/배타그룹과 달리 업무적
     * 의미가 없는 화면 표시 순서일 뿐이라, {@code Signer}/{@code Template} 재정렬도 이력화하지
     * 않는 것과 같은 이유다.
     */
    @Transactional
    public List<UnitProductDto.Response.UnitProductSummary> updateDisplayOrders(
            String actingPlatformRole,
            Long adminUserId,
            DisplayOrderRequest.UpdateDisplayOrders request
    ) {
        checkAllowed(actingPlatformRole, "ACTION_BILLING_CATALOG_MANAGE");

        Map<Long, UnitProduct> byId = unitProductRepository
                .findAllById(request.getItems().stream().map(DisplayOrderRequest.Item::getId).toList())
                .stream()
                .collect(Collectors.toMap(UnitProduct::getId, Function.identity()));

        for (DisplayOrderRequest.Item item : request.getItems()) {
            UnitProduct unitProduct = byId.get(item.getId());
            if (unitProduct == null) {
                throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
            }
            unitProduct.updateDisplayOrder(item.getDisplayOrder());
        }

        platformAdminAuditLogRecorder.record(
                adminUserId, PlatformAdminAction.REORDER_UNIT_PRODUCTS, null, null,
                "unitProductIds=" + request.getItems().stream().map(DisplayOrderRequest.Item::getId).toList()
        );

        return findUnitProducts();
    }

    /** 최신순 — 생성 시점 1건 + 이후 수정할 때마다 1건씩(이름/분류/배타그룹이 바뀔 때). */
    public List<UnitProductDto.Response.UnitProductHistorySummary> findUnitProductHistory(Long unitProductId) {
        if (!unitProductRepository.existsById(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_NOT_FOUND);
        }
        return unitProductHistoryRepository.findAllByUnitProductIdOrderByCreatedAtDesc(unitProductId).stream()
                .map(this::toHistorySummary)
                .toList();
    }

    /** 생성 시(최초 상태)와 {@link #updateUnitProduct}에서 매 변경마다 호출한다. */
    private void recordProductHistory(UnitProduct unitProduct) {
        unitProductHistoryRepository.save(UnitProductHistory.builder().unitProduct(unitProduct).build());
    }

    /** 판매가격 기간 설정(생성/수정) 시점마다, 그리고 {@link #removePeriod}에서 제거 시점마다 호출한다. */
    private void recordPeriodHistory(UnitProduct unitProduct, UnitProductPricePeriod period, boolean removed) {
        unitProductPricePeriodHistoryRepository.save(
                UnitProductPricePeriodHistory.builder().unitProduct(unitProduct).period(period).removed(removed).build()
        );
    }

    private UnitProductType parseType(String type) {
        try {
            return UnitProductType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private UnitProductCategory parseCategory(String category) {
        try {
            return UnitProductCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * effectiveFrom 생략(null) 시 "오늘"로 채운다 — signstage-docs
     * business/organization-discount-override-security-and-validity-period-review.md 결정
     * #5(2026-09-10) — 단위 상품 카탈로그는 조직/행사 스코프가 없어 플랫폼 기본 타임존
     * (Asia/Seoul)을 쓴다. 이미 있는 기간을 고치는 {@code UpdatePeriod}는 이 헬퍼를 쓰지
     * 않는다 — 편집 중인 기간의 시작일을 묵시적으로 오늘로 되돌리면 안 되므로 여전히 필수
     * 입력이다.
     */
    private LocalDate resolveEffectiveFrom(LocalDate requested) {
        return requested != null ? requested : InternationalizationDefaults.today();
    }

    private void checkPeriodValid(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApplicationException(CeremonyErrorCode.DISCOUNT_PERIOD_INVALID);
        }
    }

    private void checkNoOverlap(Long unitProductId, Long excludePeriodId, LocalDate newFrom, LocalDate newTo) {
        boolean overlaps = unitProductPricePeriodRepository
                .findAllByUnitProductIdOrderByEffectiveFromAsc(unitProductId).stream()
                .filter(p -> excludePeriodId == null || !p.getId().equals(excludePeriodId))
                .anyMatch(p -> rangesOverlap(newFrom, newTo, p.getEffectiveFrom(), p.getEffectiveTo()));
        if (overlaps) {
            throw new ApplicationException(CeremonyErrorCode.CATALOG_PRICE_PERIOD_OVERLAPPING);
        }
    }

    private boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private String computeStatus(boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate today = InternationalizationDefaults.today();
        if (today.isBefore(effectiveFrom)) {
            return "PENDING";
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return "EXPIRED";
        }
        return active ? "ON_SALE" : "INACTIVE";
    }

    private void checkNeverUsed(Long unitProductId) {
        if (hasAnyUsage(unitProductId)) {
            throw new ApplicationException(CeremonyErrorCode.UNIT_PRODUCT_IN_USE);
        }
    }

    /**
     * "사용한 적이 있는가"를 판정하는 6곳 — {@link #deleteUnitProduct}와 {@link #toSummary}의
     * {@code canDelete} 계산이 공유한다.
     */
    private boolean hasAnyUsage(Long unitProductId) {
        return billingPlanUnitProductRepository.existsByUnitProductId(unitProductId)
                || billingPlanHistoryUnitProductRepository.existsByUnitProductId(unitProductId)
                || ceremonyPlanHistoryUnitProductRepository.existsByUnitProductId(unitProductId)
                || ceremonyUnitProductPurchaseLineRepository.existsByUnitProduct_Id(unitProductId)
                || ceremonyEventOptionalFeatureRepository.existsByUnitProductId(unitProductId)
                || ceremonyEffectDefinitionOptionRepository.existsByUnitProductId(unitProductId);
    }

    private String describe(UnitProductPricePeriod period) {
        return period.getPriceInfo().getSalePrice() + " " + period.getPriceInfo().getCurrencyCode()
                + " (" + period.getEffectiveFrom() + " ~ " + (period.getEffectiveTo() == null ? "무기한" : period.getEffectiveTo()) + ")";
    }

    private UnitProductDto.Response.UnitProductSummary toSummary(UnitProduct unitProduct) {
        Optional<UnitProductPricePeriod> effective =
                unitProductPricePeriodRepository.findEffective(unitProduct.getId(), InternationalizationDefaults.today());
        List<Long> effectDefinitionIds = ceremonyEffectDefinitionOptionRepository.findAllByUnitProductId(unitProduct.getId())
                .stream()
                .map(option -> option.getEffectDefinition().getId())
                .toList();
        return new UnitProductDto.Response.UnitProductSummary(
                unitProduct.getId(),
                unitProduct.getType().name(),
                unitProduct.getName(),
                unitProduct.getCategory().name(),
                unitProduct.getExclusivityGroup(),
                effective.map(p -> p.getPriceInfo().getCurrencyCode()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSupplyPrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getSalePrice()).orElse(null),
                effective.map(p -> p.getPriceInfo().getTaxCode()).orElse(null),
                effective.map(UnitProductPricePeriod::isActive).orElse(null),
                ceremonyUnitProductPurchaseLineRepository.countByUnitProduct_IdAndPurchase_Status(
                        unitProduct.getId(), PurchaseStatus.APPROVED
                ),
                effectDefinitionIds,
                unitProduct.getCreatedAt(),
                effective.map(UnitProductPricePeriod::getEffectiveFrom).orElse(null),
                effective.map(UnitProductPricePeriod::getEffectiveTo).orElse(null),
                effective.map(p -> computeStatus(p.isActive(), p.getEffectiveFrom(), p.getEffectiveTo())).orElse("NO_ACTIVE_PERIOD"),
                unitProduct.getDisplayOrder(),
                !hasAnyUsage(unitProduct.getId())
        );
    }

    private UnitProductDto.Response.UnitProductHistorySummary toHistorySummary(UnitProductHistory history) {
        return new UnitProductDto.Response.UnitProductHistorySummary(
                history.getId(),
                history.getType().name(),
                history.getName(),
                history.getCategory().name(),
                history.getExclusivityGroup(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private UnitProductDto.Response.UnitProductPeriodSummary toPeriodSummary(UnitProductPricePeriod period) {
        return new UnitProductDto.Response.UnitProductPeriodSummary(
                period.getId(),
                period.getPriceInfo().getCurrencyCode(),
                period.getPriceInfo().getSupplyPrice(),
                period.getPriceInfo().getSalePrice(),
                period.getPriceInfo().getTaxCode(),
                period.isActive(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                computeStatus(period.isActive(), period.getEffectiveFrom(), period.getEffectiveTo()),
                period.getCreatedAt()
        );
    }

    /** signstage-docs business/menu-and-action-permission-management-review.md 10장 참고. */
    private void checkAllowed(String actingPlatformRole, String permissionKey) {
        if (!rolePermissionService.isAllowed(actingPlatformRole, permissionKey)) {
            throw new ApplicationException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
