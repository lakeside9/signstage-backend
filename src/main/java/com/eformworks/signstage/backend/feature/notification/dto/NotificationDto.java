package com.eformworks.signstage.backend.feature.notification.dto;

import com.eformworks.signstage.backend.feature.notification.entity.Notification;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class NotificationDto {

    private NotificationDto() {
    }

    public static final class Response {
        private Response() {
        }

        @Getter
        @AllArgsConstructor
        public static class NotificationSummary {

            private final Long id;
            private final String type;
            private final String title;
            private final String message;
            private final String linkPath;
            private final boolean read;
            private final LocalDateTime createdAt;

            public static NotificationSummary from(Notification notification) {
                return new NotificationSummary(
                        notification.getId(),
                        notification.getType().name(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getLinkPath(),
                        notification.isRead(),
                        notification.getCreatedAt()
                );
            }
        }

        @Getter
        @AllArgsConstructor
        public static class UnreadCount {

            private final long count;
        }
    }
}
