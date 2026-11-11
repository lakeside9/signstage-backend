package com.eformworks.signstage.backend.core.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 행사 실시간 상태 동기화용 STOMP 엔드포인트. 단일 서버 in-memory 브로커로 시작한다
 * (다중 서버 확장은 signstage-docs business/ceremony-feature-migration-review.md §5.3 —
 * 아직 미결이라 범위 밖). 구독 인가는 각 feature가 {@link ChannelInterceptor} 빈으로 등록한
 * 인터셉터(예: {@code feature.ceremony.support.CeremonyTopicAuthInterceptor})가 담당한다 —
 * core가 feature 클래스를 직접 알지 않도록 타입으로만 주입받는다.
 *
 * <p>SockJS 폴백은 두지 않는다 — 구형 브라우저 호환용일 뿐 요구사항에 없고, 순수
 * STOMP-over-WebSocket 엔드포인트 하나로 시작한다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<ChannelInterceptor> inboundChannelInterceptors;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-signstage")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new RemoteAddressHandshakeInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundChannelInterceptors.toArray(ChannelInterceptor[]::new));
    }
}
