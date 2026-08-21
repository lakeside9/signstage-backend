package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import com.eformworks.signstage.backend.feature.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 마스터. {@code organization_id}만 갖고 하위 엔티티(CeremonyEvent 등)는 ceremony_id FK
 * 체인으로 조직에 스코핑된다 — signstage-docs business/ceremony-feature-migration-review.md
 * 4.1절 결정. {@code creator}를 별도로 두지 않고 {@link BaseEntity#getCreatedBy()}로 충분하다
 * (같은 문서 4.6절) — "본인/배정 건" 판정은 {@link CeremonyAssignment}가 담당한다(4.7절).
 *
 * <p>{@code billingPlan}은 DB 컬럼 자체는 nullable이다 — 이 기능 배포 전 만들어진 기존 행사만
 * 예외로 NULL을 허용하고, 신규 생성은 서비스 레이어가 필수로 강제한다(signstage-docs
 * business/ceremony-billing-options-review.md 4.10절).
 *
 * <p>{@code status}는 {@link CeremonyStatus} 참고 — 새로 만든 Ceremony는 항상 DRAFT로 시작하고
 * 플랜 확정({@link #confirmPlan()}) 후 IN_PROGRESS로 넘어간다(이 기능 배포 전 기존 행은
 * 마이그레이션에서 소급 적용 없이 IN_PROGRESS DEFAULT로만 채운다 — signstage-docs
 * business/ceremony-plan-confirmation-review.md 4.1절).
 *
 * <p>{@code finalDiscountType}/{@code finalDiscountValue}는 품목 할인과 별개로 이 행사 건에만
 * 매기는 관리자 재량 할인이다 — signstage-docs
 * business/organization-event-discount-pricing-review.md 4.2 결정. NULL sentinel 없이 항상
 * 구체적인 값을 갖고("할인 없음"은 discountValue=0으로 표현), 플랫폼 관리자(PLATFORM_OPS
 * 이상)만 바꿀 수 있다(4.4 결정). 품목 자체의 할인(조직×품목 오버라이드 포함,
 * {@link com.eformworks.signstage.backend.feature.ceremony.service.OrganizationDiscountService})은
 * 이 필드와 별개로 각 스냅샷 컬럼(예: {@link CeremonyPlanHistory#getPlanDiscountType()})에
 * 담긴다(같은 문서 4.1절, 2026-08-21 재검토).
 */
@Entity
@Table(name = "ceremonies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ceremony extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id")
    private BillingPlan billingPlan;

    @Column(nullable = false, length = 200)
    private String title;

    /** 생성 시에는 받지 않고 행사 수정 화면에서만 채운다 — 그래서 nullable이다. */
    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CeremonyStatus status;

    /** 아래 6개 필드는 전부 description과 같은 이유로 nullable이다(생성 시엔 안 받고 수정 화면에서만 채운다). */
    @Column(name = "organizing_institution", length = 200)
    private String organizingInstitution;

    @Column(name = "organizing_department", length = 200)
    private String organizingDepartment;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_title", length = 100)
    private String contactTitle;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_discount_type", nullable = false, length = 20)
    private DiscountType finalDiscountType;

    @Column(name = "final_discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalDiscountValue;

    @Builder
    private Ceremony(Organization organization, BillingPlan billingPlan, String title) {
        this.organization = organization;
        this.billingPlan = billingPlan;
        this.title = title;
        this.status = CeremonyStatus.DRAFT;
        this.finalDiscountType = DiscountType.FIXED_AMOUNT;
        this.finalDiscountValue = BigDecimal.ZERO;
    }

    /**
     * 행사 수정 화면에서 기본 정보를 바꿀 때 쓴다. 플랜은 여기서 바꾸지 않는다({@link #changePlan}을
     * 쓴다). 주관 기관/부서, 담당자 정보는 전부 선택 입력이라 null을 그대로 허용한다.
     */
    public void updateInfo(
            String title,
            String description,
            String organizingInstitution,
            String organizingDepartment,
            String contactName,
            String contactTitle,
            String contactPhone,
            String contactEmail
    ) {
        this.title = title;
        this.description = description;
        this.organizingInstitution = organizingInstitution;
        this.organizingDepartment = organizingDepartment;
        this.contactName = contactName;
        this.contactTitle = contactTitle;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
    }

    /**
     * 하위 행사(MAIN 전체)가 완료되면 자동으로, 또는 플랫폼 관리자가 수동으로 상태를 바꿀 때 쓴다
     * (signstage-docs business/ceremony-feature-migration-review.md 참고).
     */
    public void changeStatus(CeremonyStatus status) {
        this.status = status;
    }

    /**
     * 플랫폼 관리자가 이 행사 건에만 적용되는 재량 할인을 설정/변경할 때 사용한다(PLATFORM_OPS
     * 이상 — organization-event-discount-pricing-review.md 4.2/4.4 결정). {@link #updateInfo}
     * (OWNER도 쓸 수 있는 기본 정보 수정)와 분리된 이유도 같은 결정 참고 — 가격표를 깎는 행위라
     * 셀프서비스로 허용하지 않는다.
     */
    public void applyFinalDiscount(DiscountType finalDiscountType, BigDecimal finalDiscountValue) {
        this.finalDiscountType = finalDiscountType;
        this.finalDiscountValue = finalDiscountValue;
    }

    /**
     * DRAFT 상태에서만 플랜을 바꾼다 — 상태 검증은 서비스({@code CeremonyService#changePlan})가
     * 하고 이 메서드는 필드만 바꾼다(다른 상태 전이 메서드와 같은 컨벤션). 호출할 때마다
     * {@code CeremonyPlanHistory}에 이력 한 행을 남기는 것도 서비스 몫이다 —
     * signstage-docs business/ceremony-plan-confirmation-review.md 3.2/3.4절.
     */
    public void changePlan(BillingPlan billingPlan) {
        this.billingPlan = billingPlan;
    }

    /**
     * "플랜 확정" — DRAFT → IN_PROGRESS로 단방향 전이한다. 이후 {@link #changePlan}은 서비스
     * 레이어에서 거부된다(플랜이 고정됨), 서명자/문서/하위 행사 등록이 열린다 — signstage-docs
     * business/ceremony-plan-confirmation-review.md 3.1절.
     */
    public void confirmPlan() {
        this.status = CeremonyStatus.IN_PROGRESS;
    }
}
