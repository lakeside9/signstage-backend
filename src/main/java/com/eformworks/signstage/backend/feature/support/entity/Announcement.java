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
 * 공지사항 한 건 — 플랫폼 관리자가 등록·수정한다(signstage-docs
 * business/partner-support-center-review.md 3장). v1은 플랫폼 전체 공개만 지원한다 —
 * 조직별 타겟팅은 범위 밖(같은 문서 9장 결정 #1). 정렬은 {@code pinned} 우선 +
 * {@code createdAt} 내림차순이라 {@code CeremonyEffectDefinition}/{@code Faq}처럼 수동
 * displayOrder를 두지 않는다.
 */
@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private Announcement(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.active = true;
    }

    public void updateInfo(String title, String content, boolean pinned, boolean active) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.active = active;
    }
}
