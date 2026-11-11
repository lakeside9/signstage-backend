package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eformworks.signstage.backend.feature.ceremony.dto.CeremonyDto;
import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.billing.entity.BillingPlan;
import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductCartLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchase;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyUnitProductPurchaseLine;
import com.eformworks.signstage.backend.feature.ceremony.entity.PurchaseStatus;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProduct;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductCategory;
import com.eformworks.signstage.backend.feature.billing.entity.UnitProductType;
import com.eformworks.signstage.backend.feature.ceremony.error.CeremonyErrorCode;
import com.eformworks.signstage.backend.feature.organization.entity.Member;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberStatus;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CeremonyUnitProductPurchaseService} 단위 테스트 — 장바구니 담기/수정의 수량·판매 단위·효과 묶음 제약, 한 헤더 아래 여러 줄로 저장되는 구매 요청, 플랜 확정 전 구매 차단.
 * 2026-11-11 {@code CeremonyServiceTest}에서 분리했다. 공용 mock과 서비스 조립은
 * {@link CeremonyServiceTestSupport}에 있다.
 */
class CeremonyUnitProductPurchaseServiceTest extends CeremonyServiceTestSupport {

    @Test
    @DisplayName("단위 상품 추가구매(구매하기)는 장바구니에 담긴 여러 줄을 한 번에 하나의 요청 헤더 아래 저장한다")
    void purchaseUnitProducts_multipleLines_savesOneHeaderWithLines() {
        // given
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        // 이 테스트가 의도하는 건 배포 전 레거시(plan 없음, IN_PROGRESS) 행사의 "안 A 큐레이션
        // 미적용" 동작이다 — DRAFT+플랜 없음(신규, 2026-09-10부터 가능)은 별도로 막힌다
        // (purchaseUnitProducts_rejectsWhenDraftWithoutPlanSelected 참고).
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        UnitProduct tablets = unitProduct(202L, UnitProductType.TABLETS);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of(
                cartLine(ceremony, signers, 10),
                cartLine(ceremony, tablets, 5)
        ));
        given(unitProductPricePeriodRepository.findEffective(eq(201L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(signers, new BigDecimal("8000"), new BigDecimal("10000"))));
        given(unitProductPricePeriodRepository.findEffective(eq(202L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("40000"), new BigDecimal("50000"))));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductPurchaseLineRepository.save(any(CeremonyUnitProductPurchaseLine.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        CeremonyDto.Response.UnitProductPurchaseSummary result =
                ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        // then — 옛 "묶음 상품"(secondaryCapacityType)이 하던 역할을 이제 한 요청의 여러 줄이 대신한다.
        // 두 상품 모두 기본 카테고리(ESSENTIAL, 시스템 사용료)라 자가-체크아웃으로 즉시 APPROVED된다.
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(ceremonyUnitProductPurchaseRepository).save(any(CeremonyUnitProductPurchase.class));
        verify(ceremonyUnitProductPurchaseLineRepository, org.mockito.Mockito.times(2)).save(any(CeremonyUnitProductPurchaseLine.class));
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(10L);
    }

    @Test
    @DisplayName("이벤트 효과 묶음(토글형)은 장바구니에 수량 2 이상으로 담겨 있으면 구매 시점에 거부된다")
    void purchaseUnitProducts_effectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        // 이 테스트가 의도하는 건 배포 전 레거시(plan 없음, IN_PROGRESS) 행사의 동작이다 —
        // purchaseUnitProducts_multipleLines_savesOneHeaderWithLines와 같은 이유.
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct bundle = unitProduct(301L, UnitProductType.EVENT_EFFECT_BUNDLE);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L))
                .willReturn(List.of(cartLine(ceremony, bundle, 2)));
        given(unitProductPricePeriodRepository.findEffective(eq(301L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(bundle, new BigDecimal("50000"), new BigDecimal("60000"))));

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        verify(ceremonyUnitProductPurchaseLineRepository, never()).save(any());
        verify(ceremonyUnitProductCartLineRepository, never()).deleteAllByCeremonyId(any());
    }

    /**
     * 자가-체크아웃 범위 단위 테스트 — signstage-docs
     * business/unit-product-purchase-self-checkout-review.md 2·4장 결정(2026-09-11):
     * 시스템 사용료(ESSENTIAL/APPLICATION)만 자가-체크아웃하고, 장비·인력(EQUIPMENT/
     * PERSONNEL)이 섞이면(배포 전 레거시 plan 없는 행사 한정 — 플랜이 있는 행사는 애초에
     * 이 카테고리가 구매 카탈로그에 없다) 예전처럼 PENDING으로 남긴다.
     */
    @Test
    @DisplayName("장바구니에 장비/인력 카테고리가 섞여 있으면 자가-체크아웃하지 않고 PENDING으로 남긴다")
    void purchaseUnitProducts_nonSystemUsageFeeLine_staysPending() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan(); // 레거시(plan 없음, IN_PROGRESS) 흉내 — 카탈로그 필터가 적용되지 않는 유일한 경로.
        Member member = Member.builder().role(MemberRole.OWNER).build();

        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT).build();
        ReflectionTestUtils.setField(tablets, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L))
                .willReturn(List.of(cartLine(ceremony, tablets, 3)));
        given(unitProductPricePeriodRepository.findEffective(eq(401L), any(LocalDate.class)))
                .willReturn(Optional.of(unitProductPeriod(tablets, new BigDecimal("40000"), new BigDecimal("50000"))));
        given(ceremonyUnitProductPurchaseRepository.save(any(CeremonyUnitProductPurchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ceremonyUnitProductPurchaseLineRepository.save(any(CeremonyUnitProductPurchaseLine.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CeremonyDto.Response.UnitProductPurchaseSummary result =
                ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, 10L, CURRENT_USER_ID);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(ceremonyUnitProductCartLineRepository).deleteAllByCeremonyId(10L);
    }

    @Test
    @DisplayName("장바구니 담기 — 같은 상품을 두 번 담으면 새 줄이 아니라 기존 줄의 수량에 더한다")
    void addToCart_sameItemTwice_mergesQuantity() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct signers = unitProduct(201L, UnitProductType.SIGNERS);
        CeremonyUnitProductCartLine existing = cartLine(ceremony, signers, 3);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(201L)).willReturn(Optional.of(signers));
        given(ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(10L, 201L))
                .willReturn(Optional.of(existing));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of(existing));

        ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(201L, 4));

        assertThat(existing.getQuantity()).isEqualTo(7);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 이벤트 효과 묶음(토글형)을 수량 2 이상으로 담으려 하면 거부된다")
    void addToCart_eventEffectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(301L)).willReturn(Optional.of(bundle));

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(301L, 2)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 이미 PENDING/APPROVED로 구매된 이벤트 효과 묶음은 다시 담을 수 없다")
    void addToCart_eventEffectBundleAlreadyPurchased_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(301L)).willReturn(Optional.of(bundle));
        given(ceremonyUnitProductPurchaseLineRepository
                .existsByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(10L, 301L, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)))
                .willReturn(true);

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(301L, 1)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_ALREADY_PURCHASED);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 수량 수정 — 이벤트 효과 묶음(토글형)을 2 이상으로 고치려 하면 거부된다")
    void updateCartLine_eventEffectBundleQuantityOverOne_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct bundle = UnitProduct.builder()
                .type(UnitProductType.EVENT_EFFECT_BUNDLE).name("3종 묶음").category(UnitProductCategory.APPLICATION).build();
        ReflectionTestUtils.setField(bundle, "id", 301L);
        CeremonyUnitProductCartLine existing = cartLine(ceremony, bundle, 1);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(10L, 301L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.updateCartLine(
                ORGANIZATION_ID, 10L, CURRENT_USER_ID, 301L, new CeremonyDto.Request.UpdateCartLine(2)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_TOGGLE_QUANTITY_INVALID);
        assertThat(existing.getQuantity()).isEqualTo(1);
    }

    /**
     * 2026-09-12 사용자 요청("단위 상품을 구매할 수 있는 최대 수량을 관리하려고 합니다") 권장안
     * 구현 — 카탈로그에 설정한 {@code maxPurchaseQuantity}를 넘겨 담으면 거부된다. 과거
     * PENDING/APPROVED 구매 수량 합 + 이번에 담을 수량이 상한을 넘는지 본다(플랜 기본 포함
     * 수량은 이 합계에 넣지 않는다).
     */
    @Test
    @DisplayName("장바구니 담기 — 카탈로그에 설정한 최대 구매 수량을 넘기면 거부된다")
    void addToCart_maxQuantityExceeded_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT)
                .maxPurchaseQuantity(5).build();
        ReflectionTestUtils.setField(tablets, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(401L)).willReturn(Optional.of(tablets));
        given(ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(10L, 401L, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)))
                .willReturn(List.of(purchaseLine(tablets, 3))); // 이미 3개 구매됨.

        // 3(이미 구매) + 3(이번에 담을 양) = 6 > 5(최대) → 거부.
        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(401L, 3)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_MAX_QUANTITY_EXCEEDED);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 과거 구매분 + 이번 수량이 최대 구매 수량과 같으면 통과한다")
    void addToCart_reachingExactMaxQuantity_succeeds() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT)
                .maxPurchaseQuantity(5).build();
        ReflectionTestUtils.setField(tablets, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(401L)).willReturn(Optional.of(tablets));
        given(ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(10L, 401L, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)))
                .willReturn(List.of(purchaseLine(tablets, 3)));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of());

        // 3(이미 구매) + 2(이번에 담을 양) = 5 == 5(최대) → 통과.
        ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(401L, 2));

        verify(ceremonyUnitProductCartLineRepository).save(any());
    }

    /**
     * 2026-09-14 사용자 요청("템플릿 문서 10개를 1묶음 10,000원에 팔고 싶다") 구현 — 카탈로그에
     * 설정한 saleUnitQuantity(판매 단위 수량)의 배수가 아닌 수량은 거부된다.
     */
    @Test
    @DisplayName("장바구니 담기 — 판매 단위 수량의 배수가 아니면 거부된다")
    void addToCart_notMultipleOfSaleUnitQuantity_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct templates = UnitProduct.builder()
                .type(UnitProductType.TEMPLATES).name("템플릿 문서").category(UnitProductCategory.ESSENTIAL)
                .saleUnitQuantity(10).build();
        ReflectionTestUtils.setField(templates, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(401L)).willReturn(Optional.of(templates));

        // 판매 단위(10)의 배수가 아닌 7개는 거부 — maxPurchaseQuantity가 없어 그 검사까지 가지도
        // 않으므로 구매 이력 조회 스텁은 필요 없다.
        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(401L, 7)))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_SALE_UNIT_QUANTITY_INVALID);
        verify(ceremonyUnitProductCartLineRepository, never()).save(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 판매 단위 수량의 배수면 통과한다")
    void addToCart_multipleOfSaleUnitQuantity_succeeds() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct templates = UnitProduct.builder()
                .type(UnitProductType.TEMPLATES).name("템플릿 문서").category(UnitProductCategory.ESSENTIAL)
                .saleUnitQuantity(10).build();
        ReflectionTestUtils.setField(templates, "id", 401L);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(401L)).willReturn(Optional.of(templates));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of());

        // 판매 단위(10)의 배수인 10개는 통과 — maxPurchaseQuantity가 없어 구매 이력 조회 없이 바로 담긴다.
        ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(401L, 10));

        verify(ceremonyUnitProductCartLineRepository).save(any());
    }

    @Test
    @DisplayName("장바구니 수량 수정 — 과거 구매분과 합쳐 최대 구매 수량을 넘기면 거부된다")
    void updateCartLine_maxQuantityExceeded_rejected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct tablets = UnitProduct.builder()
                .type(UnitProductType.TABLETS).name("태블릿").category(UnitProductCategory.EQUIPMENT)
                .maxPurchaseQuantity(5).build();
        ReflectionTestUtils.setField(tablets, "id", 401L);
        CeremonyUnitProductCartLine existing = cartLine(ceremony, tablets, 1);

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(ceremonyUnitProductCartLineRepository.findByCeremonyIdAndUnitProductId(10L, 401L))
                .willReturn(Optional.of(existing));
        given(ceremonyUnitProductPurchaseLineRepository
                .findAllByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(10L, 401L, List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED)))
                .willReturn(List.of(purchaseLine(tablets, 3)));

        // 3(이미 구매) + 3(장바구니 줄을 3으로 고침) = 6 > 5(최대) → 거부.
        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.updateCartLine(
                ORGANIZATION_ID, 10L, CURRENT_USER_ID, 401L, new CeremonyDto.Request.UpdateCartLine(3)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.UNIT_PRODUCT_MAX_QUANTITY_EXCEEDED);
        assertThat(existing.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 구매 수량이 설정되지 않으면(null) 제한 없이 담을 수 있다")
    void addToCart_noMaxQuantitySet_unlimited() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, 10L);
        ceremony.confirmPlan();
        Member member = Member.builder().role(MemberRole.OWNER).build();
        UnitProduct tablets = unitProduct(401L, UnitProductType.TABLETS); // maxPurchaseQuantity 미설정(null).

        given(ceremonyRepository.findById(10L)).willReturn(Optional.of(ceremony));
        given(memberRepository.findByOrganizationIdAndUserIdAndStatus(ORGANIZATION_ID, CURRENT_USER_ID, MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(unitProductRepository.findById(401L)).willReturn(Optional.of(tablets));
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(10L)).willReturn(List.of());

        ceremonyUnitProductPurchaseService.addToCart(ORGANIZATION_ID, 10L, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(401L, 999));

        verify(ceremonyUnitProductCartLineRepository).save(any());
        // 무제한이라 과거 구매 수량 합계를 조회할 필요조차 없어야 한다.
        verify(ceremonyUnitProductPurchaseLineRepository, never())
                .findAllByPurchase_CeremonyIdAndUnitProduct_IdAndPurchase_StatusIn(any(), any(), any());
    }

    @Test
    @DisplayName("추가구매 — 플랜을 선택한 적 없는 신규 DRAFT 행사는 거부한다(안 A 큐레이션 구멍 방지)")
    void purchaseUnitProducts_rejectsWhenDraftWithoutPlanSelected() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        stubOwnerMember(ceremony);

        // 플랜 확정 가드가 장바구니 조회보다 먼저 걸려야 한다 — 장바구니 리포지토리는
        // 아예 안 불려야 한다.
        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(ceremonyUnitProductCartLineRepository, never()).findAllByCeremonyIdOrderByIdAsc(any());
    }

    /**
     * 2026-09-11 사용자 요청 — "플랜 확정 후에 단위 상품 추가 구매를 할 수 있도록 제약을
     * 걸어주세요." 플랜을 고르기만 하고 아직 확정 전인 DRAFT 행사는 담긴 것과 무관하게
     * 거부된다 — {@code checkCeremonyPlanConfirmed}는 billingPlan 값이 아니라 status만 본다.
     */
    @Test
    @DisplayName("추가구매 — 플랜을 선택만 하고 아직 확정 전(DRAFT)인 행사는 거부한다")
    void purchaseUnitProducts_rejectsWhenPlanSelectedButNotConfirmed() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(ceremonyUnitProductCartLineRepository, never()).findAllByCeremonyIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("장바구니 담기 — 플랜을 선택만 하고 아직 확정 전(DRAFT)인 행사는 거부한다")
    void addToCart_rejectsWhenPlanSelectedButNotConfirmed() {
        Organization organization = organization();
        BillingPlan plan = BillingPlan.builder().name("스탠다드").build();
        ReflectionTestUtils.setField(plan, "id", 101L);
        Ceremony ceremony = Ceremony.builder().organization(organization).billingPlan(plan).title("행사").build();
        ReflectionTestUtils.setField(ceremony, "id", CEREMONY_ID);
        stubOwnerMember(ceremony);

        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.addToCart(
                ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID, new CeremonyDto.Request.AddToCart(201L, 1)
        ))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CEREMONY_PLAN_NOT_CONFIRMED);
        verify(unitProductRepository, never()).findById(any());
    }

    @Test
    @DisplayName("추가구매 — 배포 전 레거시 행사(plan 없음, IN_PROGRESS)는 예전처럼 플랜 미선택으로 막지 않는다(장바구니가 비어 있으면 CART_EMPTY)")
    void purchaseUnitProducts_allowsWhenLegacyCeremonyWithoutPlan() {
        Organization organization = organization();
        Ceremony ceremony = ceremony(organization, CEREMONY_ID);
        ceremony.confirmPlan(); // DRAFT -> IN_PROGRESS 전이만 흉내낸다(레거시는 배포 시 이미 IN_PROGRESS로 채워졌다).
        stubOwnerMember(ceremony);
        given(ceremonyUnitProductCartLineRepository.findAllByCeremonyIdOrderByIdAsc(CEREMONY_ID)).willReturn(List.of());

        // 플랜 미선택 가드에는 안 걸린다 — 그 대신 장바구니가 비어 있어 CART_EMPTY로 끝난다
        // (자가-체크아웃 도입으로 "빈 구매"라는 개념 자체가 없어졌다).
        assertThatThrownBy(() -> ceremonyUnitProductPurchaseService.purchaseUnitProducts(ORGANIZATION_ID, CEREMONY_ID, CURRENT_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CeremonyErrorCode.CART_EMPTY);
    }
}
