package com.eformworks.signstage.backend.feature.notification.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 한 명에게 가는 앱 안 알림. 받는 사람마다 한 줄을 만들고 읽음은 사람별로 기록한다.
 * {@code linkPath}는 프론트 경로(예: {@code /partner-orders/3/12})다.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "link_path", length = 300)
    private String linkPath;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private Notification(Long userId, NotificationType type, String title, String message, String linkPath) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.linkPath = linkPath;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
