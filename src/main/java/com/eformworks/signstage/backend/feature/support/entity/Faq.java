package com.eformworks.signstage.backend.feature.support.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FAQ 한 건 — 플랫폼 관리자가 등록·수정한다. {@code CeremonyEffectDefinition}과 같은
 * 카탈로그 CRUD 패턴이다(signstage-docs business/partner-support-center-review.md 4장).
 * {@code category}는 고정 enum이 아니라 자유 문자열이다(nullable) — {@code UnitProduct}의
 * {@code exclusivityGroup}과 같은 원칙으로, 배포 없이 관리자가 새 분류를 바로 쓸 수 있어야
 * 한다. 표시 순서(displayOrder)는 그룹 구분 없이 전체 목록을 통째로 재인덱싱한다
 * ({@code CeremonyEffectDefinition}의 (target, trigger) 그룹 재정규화와 달리 단일 그룹).
 */
@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private Faq(String category, String question, String answer, int displayOrder) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public void updateInfo(String category, String question, String answer, boolean active) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.active = active;
    }

    /** 목록 화면의 위/아래 이동 버튼이 전체 배열을 다시 인덱싱해 통째로 보낼 때 쓴다. */
    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
