package com.eformworks.signstage.backend.feature.permission.entity;

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
 * 관리자가 런타임에 언어별로 직접 편집한 메뉴 표시명. {@link Menu#getLabelKey()}(배포 관리형
 * 번역 키)와 달리 이 테이블에 해당 언어 행이 있으면 그 값이 {@code label_key} 해석보다
 * 우선한다 — signstage-docs business/multilingual-content-and-error-handling-review.md 6장,
 * business/menu-and-action-permission-management-review.md 12장 #10(2026-09-05, 이름 편집
 * 허용 결정)에 따라 신설한다.
 */
@Entity
@Table(name = "menu_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"menu_id", "language_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuTranslation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false, length = 150)
    private String label;

    @Builder
    private MenuTranslation(Menu menu, String languageCode, String label) {
        this.menu = menu;
        this.languageCode = languageCode;
        this.label = label;
    }

    public void changeLabel(String label) {
        this.label = label;
    }
}
