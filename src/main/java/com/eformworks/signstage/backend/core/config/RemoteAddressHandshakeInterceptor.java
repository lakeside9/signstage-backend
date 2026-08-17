package com.eformworks.signstage.backend.core.config;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 핸드셰이크 시점의 접속 IP를 STOMP 세션 attributes에 저장한다. accessKey 짐작
 * 시도는 SUBSCRIBE를 반복하는 방식이 주 경로라, {@code CeremonyTopicAuthInterceptor}가
 * SUBSCRIBE마다 이 IP로 레이트 리밋을 검사하는 데 쓴다(signstage-docs
 * business/ceremony-feature-migration-review.md §4.5).
 */
public class RemoteAddressHandshakeInterceptor implements HandshakeInterceptor {

    public static final String REMOTE_ADDR_ATTRIBUTE = "remoteAddr";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            attributes.put(REMOTE_ADDR_ATTRIBUTE, request.getRemoteAddress().getAddress().getHostAddress());
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // 할 일 없음.
    }
}
