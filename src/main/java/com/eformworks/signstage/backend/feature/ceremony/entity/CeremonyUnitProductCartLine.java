package com.eformworks.signstage.backend.feature.ceremony.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단위 상품 추가구매 장바구니 한 줄 — 서버에 임시 저장된다(signstage-docs
 * business/unit-product-purchase-self-checkout-review.md 6장 결정, 2026-09-11: "1차는
 * 프런트 상태만" 권장을 뒤집고 서버 저장으로 확정 — 새로고침·탭 전환·다른 세션에서 다시
 * 열어도 담아둔 내용이 유지돼야 한다).
 *
 * <p>{@code (ceremony_id, unit_product_id)} 유니크 제약으로 "같은 항목을 두 번 담으면 수량을
 * 합친다"(같은 문서 6장 결정)를 자연히 구현한다 — upsert 한 번으로 끝난다. 가격은 담지
 * 않는다 — 정가 스냅샷은 실제 구매({@link CeremonyUnitProductPurchaseLine}) 시점에만
 * 만들어지고, 장바구니는 순수하게 "무엇을 몇 개 담았는지"만 기억한다. 행사별로 하나만 있고
 * (회원별이 아니다 — 구매 자체가 행사에 귀속되는 지금 모델과 일관성을 맞춘다), "구매하기"가
 * 성공하면 이 행사의 줄 전체가 삭제된다.
 */
@Entity
@Table(
        name = "ceremony_unit_product_cart_lines",
        uniqueConstraints = @UniqueConstraint(name = "uq_cart_line_ceremony_product", columnNames = {"ceremony_id", "unit_product_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CeremonyUnitProductCartLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceremony_id", nullable = false)
    private Ceremony ceremony;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_product_id", nullable = false)
    private UnitProduct unitProduct;

    @Column(nullable = false)
    private Integer quantity;

    @Builder
    private CeremonyUnitProductCartLine(Ceremony ceremony, UnitProduct unitProduct, Integer quantity) {
        this.ceremony = ceremony;
        this.unitProduct = unitProduct;
        this.quantity = quantity;
    }

    /** "추가 구매하기"로 같은 항목을 다시 담을 때 — 수량을 합친다(신규 줄을 만들지 않는다). */
    public void addQuantity(Integer additional) {
        this.quantity += additional;
    }

    /** 장바구니 검토 화면에서 수량을 직접 고쳐 쓸 때. */
    public void changeQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
