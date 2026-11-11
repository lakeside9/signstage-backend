package com.eformworks.signstage.backend.feature.ceremony.service;

import com.eformworks.signstage.backend.feature.ceremony.entity.Ceremony;
import com.eformworks.signstage.backend.feature.ceremony.entity.CeremonyEvent;
import com.eformworks.signstage.backend.feature.ceremony.entity.Signer;
import com.eformworks.signstage.backend.feature.ceremony.repository.CeremonyEventRepository;
import com.eformworks.signstage.backend.feature.ceremony.repository.SignerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import static org.mockito.Mockito.*;

class SignerPresenceListenerTest {
    @Test
    void countsOnlyMatchingSignerAndEventAndIgnoresObservers() {
        var events = mock(CeremonyEventRepository.class);
        var signers = mock(SignerRepository.class);
        var presence = mock(SignerPresenceService.class);
        var listener = new SignerPresenceListener(events, signers, presence);
        listener.subscribed(subscription(null));
        verifyNoInteractions(events, signers, presence);

        var ceremony = mock(Ceremony.class);
        when(ceremony.getId()).thenReturn(5L);
        var event = mock(CeremonyEvent.class);
        when(event.getId()).thenReturn(1L);
        when(event.getCeremony()).thenReturn(ceremony);
        var signer = mock(Signer.class);
        when(signer.getId()).thenReturn(10L);
        when(signer.getCeremony()).thenReturn(ceremony);
        when(events.findByAccessKey("event-key")).thenReturn(Optional.of(event));
        when(signers.findByAccessKey("signer-key")).thenReturn(Optional.of(signer));
        listener.subscribed(subscription("signer-key"));
        verify(presence).register("session", "sub", 1L, 10L);

        clearInvocations(presence);
        var other = mock(Ceremony.class);
        when(other.getId()).thenReturn(6L);
        when(signer.getCeremony()).thenReturn(other);
        listener.subscribed(subscription("signer-key"));
        listener.subscribed(subscription("unknown-key"));
        verifyNoInteractions(presence);
    }

    private SessionSubscribeEvent subscription(String signerKey) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId("session");
        headers.setSubscriptionId("sub");
        headers.setDestination("/topic/events/1/state");
        headers.setNativeHeader("eventAccessKey", "event-key");
        if (signerKey != null) headers.setNativeHeader("signerAccessKey", signerKey);
        return new SessionSubscribeEvent(this,
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }
}
