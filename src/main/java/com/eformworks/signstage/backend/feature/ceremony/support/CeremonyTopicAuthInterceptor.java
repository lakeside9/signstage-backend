package com.eformworks.signstage.backend.feature.ceremony.support;

import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 구독 인가. JWT를 쓰지 않는다 — {@code /topic/events/{eventId}/state}를 구독하려면
 * STOMP 네이티브 헤더 {@code eventAccessKey}가 그 이벤트의 진짜 accessKey와 일치해야 한다
 * (signstage-docs business/ceremony-feature-migration-review.md 4.5절 결정). 다른 목적지는
 * 이 인터셉터가 관여하지 않는다.
 *
 * <p>포털(REST)과 같은 인가 원리지만, 채널 인터셉터는 Spring Security 필터에 가까운 저수준
 * 계층이라 {@code SignerPortalService}를 재사용하지 않고 리포지토리를 직접 쓴다.
 */
@Component
@RequiredArgsConstructor
public class CeremonyTopicAuthInterceptor implements ChannelInterceptor {

    private static final Pattern EVENT_STATE_TOPIC_PATTERN = Pattern.compile("^/topic/events/(\\d+)/state$");
    private static final String ACCESS_KEY_HEADER = "eventAccessKey";

    private final CeremonyEventRepository ceremonyEventRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        Matcher matcher = destination != null ? EVENT_STATE_TOPIC_PATTERN.matcher(destination) : null;
        if (matcher == null || !matcher.matches()) {
            return message;
        }

        Long eventId = Long.valueOf(matcher.group(1));
        String accessKey = accessor.getFirstNativeHeader(ACCESS_KEY_HEADER);
        CeremonyEvent event = ceremonyEventRepository.findById(eventId).orElse(null);

        if (event == null || accessKey == null || !accessKey.equals(event.getAccessKey())) {
            throw new MessagingException("이 행사 상태 채널을 구독할 권한이 없습니다.");
        }

        return message;
    }
}
