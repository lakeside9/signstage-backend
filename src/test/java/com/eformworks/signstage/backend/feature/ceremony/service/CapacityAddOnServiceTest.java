package com.eformworks.signstage.backend.feature.ceremony.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.core.error.CommonErrorCode;
import com.eformworks.signstage.backend.feature.ceremony.dto.CapacityAddOnDto;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityAddOn;
import com.eformworks.signstage.backend.feature.ceremony.entity.CapacityType;
import com.eformworks.signstage.backend.feature.ceremony.entity.DiscountType;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnHistoryRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CapacityAddOnRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyCapacityPurchaseRepository;
import com.eformworks.signstage.backend.feature.platformadmin.service.PlatformAdminAuditLogRecorder;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link CapacityAddOnService}의 묶음 상품(secondaryCapacityType/secondaryUnitAmount) 검증
 * 단위 테스트 — signstage-docs business/ceremony-billing-options-review.md 4.7절 후속
 * (2026-08-21, 사용자 요청 — "서명자"/"태블릿"/"서명자+태블릿" 세 종류를 카탈로그에서 고를 수
 * 있게).
 */
@ExtendWith(MockitoExtension.class)
class CapacityAddOnServiceTest {

    @Mock
    private CapacityAddOnRepository capacityAddOnRepository;
    @Mock
    private CapacityAddOnHistoryRepository capacityAddOnHistoryRepository;
    @Mock
    private CeremonyCapacityPurchaseRepository ceremonyCapacityPurchaseRepository;
    @Mock
    private PlatformAdminAuditLogRecorder platformAdminAuditLogRecorder;

    @InjectMocks
    private CapacityAddOnService capacityAddOnService;

    @Test
    @DisplayName("주 용량+보조 용량을 함께 지정하면 묶음 상품(예: 서명자+태블릿)이 등록된다")
    void createCapacityAddOn_withSecondaryCapacity_success() {
        CapacityAddOnDto.Request.CreateCapacityAddOn request = new CapacityAddOnDto.Request.CreateCapacityAddOn(
                "SIGNERS", 10, "TABLETS", 10,
                new BigDecimal("100000"), new BigDecimal("90000"), "FIXED_AMOUNT", new BigDecimal("10000")
        );

        CapacityAddOnDto.Response.CapacityAddOnSummary response =
                capacityAddOnService.createCapacityAddOn("PLATFORM_OPS", 1L, request);

        assertThat(response.getCapacityType()).isEqualTo("SIGNERS");
        assertThat(response.getUnitAmount()).isEqualTo(10);
        assertThat(response.getSecondaryCapacityType()).isEqualTo("TABLETS");
        assertThat(response.getSecondaryUnitAmount()).isEqualTo(10);
    }

    @Test
    @DisplayName("보조 용량 유형만 있고 보조 수량이 없으면 거부된다")
    void createCapacityAddOn_secondaryTypeWithoutAmount_fail() {
        CapacityAddOnDto.Request.CreateCapacityAddOn request = new CapacityAddOnDto.Request.CreateCapacityAddOn(
                "SIGNERS", 10, "TABLETS", null,
                new BigDecimal("100000"), new BigDecimal("90000"), "FIXED_AMOUNT", new BigDecimal("10000")
        );

        assertThatThrownBy(() -> capacityAddOnService.createCapacityAddOn("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("보조 용량 유형이 주 용량 유형과 같으면 거부된다")
    void createCapacityAddOn_secondarySameAsPrimary_fail() {
        CapacityAddOnDto.Request.CreateCapacityAddOn request = new CapacityAddOnDto.Request.CreateCapacityAddOn(
                "SIGNERS", 10, "SIGNERS", 10,
                new BigDecimal("100000"), new BigDecimal("90000"), "FIXED_AMOUNT", new BigDecimal("10000")
        );

        assertThatThrownBy(() -> capacityAddOnService.createCapacityAddOn("PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("보조 용량 없이 단일 상품(예: 태블릿만)도 등록할 수 있다")
    void createCapacityAddOn_withoutSecondaryCapacity_success() {
        CapacityAddOnDto.Request.CreateCapacityAddOn request = new CapacityAddOnDto.Request.CreateCapacityAddOn(
                "TABLETS", 1, null, null,
                new BigDecimal("50000"), new BigDecimal("45000"), "FIXED_AMOUNT", new BigDecimal("5000")
        );

        CapacityAddOnDto.Response.CapacityAddOnSummary response =
                capacityAddOnService.createCapacityAddOn("PLATFORM_OPS", 1L, request);

        assertThat(response.getCapacityType()).isEqualTo("TABLETS");
        assertThat(response.getSecondaryCapacityType()).isNull();
        assertThat(response.getSecondaryUnitAmount()).isNull();
    }

    @Test
    @DisplayName("원래 단일 상품이었던 것을 수정할 때 보조 수량을 넣어도 조용히 무시된다(묶음으로 바꾸려면 새 상품 등록)")
    void updateCapacityAddOn_originallySingle_ignoresSecondaryUnitAmount() {
        CapacityAddOn capacityAddOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .build();
        ReflectionTestUtils.setField(capacityAddOn, "id", 1L);

        given(capacityAddOnRepository.findById(1L)).willReturn(Optional.of(capacityAddOn));

        CapacityAddOnDto.Request.UpdateCapacityAddOn request = new CapacityAddOnDto.Request.UpdateCapacityAddOn(
                20, 20, new BigDecimal("100000"), new BigDecimal("90000"), "FIXED_AMOUNT", new BigDecimal("10000"), true
        );

        CapacityAddOnDto.Response.CapacityAddOnSummary response =
                capacityAddOnService.updateCapacityAddOn(1L, "PLATFORM_OPS", 1L, request);

        assertThat(response.getUnitAmount()).isEqualTo(20);
        assertThat(response.getSecondaryUnitAmount()).isNull();
    }

    @Test
    @DisplayName("원래 묶음 상품이었던 것을 수정할 때 보조 수량을 생략하면 거부된다")
    void updateCapacityAddOn_originallyCombo_requiresSecondaryUnitAmount() {
        CapacityAddOn capacityAddOn = CapacityAddOn.builder()
                .capacityType(CapacityType.SIGNERS)
                .unitAmount(10)
                .secondaryCapacityType(CapacityType.TABLETS)
                .secondaryUnitAmount(10)
                .supplyPrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("90000"))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .build();
        ReflectionTestUtils.setField(capacityAddOn, "id", 1L);

        given(capacityAddOnRepository.findById(1L)).willReturn(Optional.of(capacityAddOn));

        CapacityAddOnDto.Request.UpdateCapacityAddOn request = new CapacityAddOnDto.Request.UpdateCapacityAddOn(
                20, null, new BigDecimal("100000"), new BigDecimal("90000"), "FIXED_AMOUNT", new BigDecimal("10000"), true
        );

        assertThatThrownBy(() -> capacityAddOnService.updateCapacityAddOn(1L, "PLATFORM_OPS", 1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
}
