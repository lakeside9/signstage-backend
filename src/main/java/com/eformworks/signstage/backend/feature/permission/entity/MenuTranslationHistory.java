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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** {@link MenuTranslation} 변경 이력. append-only — signstage-docs 6장 안내를 따른다. */
@Entity
@Table(name = "menu_translation_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class MenuTranslationHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_translation_id", nullable = false)
    private MenuTranslation menuTranslation;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false, length = 150)
    private String label;

    @Builder
    private MenuTranslationHistory(MenuTranslation menuTranslation) {
        this.menuTranslation = menuTranslation;
        this.languageCode = menuTranslation.getLanguageCode();
        this.label = menuTranslation.getLabel();
    }
}
