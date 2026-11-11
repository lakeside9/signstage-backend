package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class SignerPresenceListener {
    private final CeremonyEventRepository eventRepository;
    private final SignerRepository signerRepository;
    private final SignerPresenceService presence;

    @EventListener
    @Transactional(readOnly = true)
    public void subscribed(SessionSubscribeEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        String eventKey = headers.getFirstNativeHeader("eventAccessKey");
        String signerKey = headers.getFirstNativeHeader("signerAccessKey");
        if (eventKey == null || signerKey == null || headers.getSessionId() == null) return;
        var ceremonyEvent = eventRepository.findByAccessKey(eventKey).orElse(null);
        var signer = signerRepository.findByAccessKey(signerKey).orElse(null);
        if (ceremonyEvent == null || signer == null
                || !signer.getCeremony().getId().equals(ceremonyEvent.getCeremony().getId())
                || !("/topic/events/" + ceremonyEvent.getId() + "/state").equals(headers.getDestination())) return;
        presence.register(headers.getSessionId(), headers.getSubscriptionId(), ceremonyEvent.getId(), signer.getId());
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        presence.disconnect(event.getSessionId());
    }

    @EventListener
    public void unsubscribed(SessionUnsubscribeEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        presence.unsubscribe(headers.getSessionId(), headers.getSubscriptionId());
    }
}
