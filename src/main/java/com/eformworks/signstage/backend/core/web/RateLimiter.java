package com.eformworks.signstage.backend.core.web;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인메모리 fixed-window 레이트 리미터. Redis 등 새 인프라 없이(WebSocket도 아직 단일 서버
 * in-memory 브로커 전제라 일관됨, signstage-docs business/ceremony-feature-migration-review.md
 * §5.3은 별도 미결) accessKey/IP 단위 요청 속도를 제한한다(같은 문서 §4.5 결정).
 *
 * <p>REST 포털 API({@code RateLimitInterceptor})와 WebSocket SUBSCRIBE
 * ({@code CeremonyTopicAuthInterceptor})가 이 컴포넌트를 공유해 같은 IP 예산을 쓴다.
 */
@Component
public class RateLimiter {

    /** 이 값보다 자주 청소를 시도하면 오버헤드만 늘어난다 — "가끔 한 번" 정리하면 충분하다. */
    private static final long CLEANUP_INTERVAL = 1000L;

    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimiter(
            @Value("${rate-limit.portal.max-requests:60}") int maxRequests,
            @Value("${rate-limit.portal.window-seconds:60}") long windowSeconds
    ) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /** 이번 요청을 허용하면 {@code true}, 한도를 넘겨 거부하면 {@code false}. */
    public boolean tryAcquire(String key) {
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanupExpired();
        }

        long currentWindow = currentWindow();
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(currentWindow));

        synchronized (counter) {
            if (counter.windowStart != currentWindow) {
                counter.windowStart = currentWindow;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() <= maxRequests;
        }
    }

    private long currentWindow() {
        return Instant.now().getEpochSecond() / windowSeconds;
    }

    private void cleanupExpired() {
        long currentWindow = currentWindow();
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart < currentWindow);
    }

    private static final class WindowCounter {
        private volatile long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
