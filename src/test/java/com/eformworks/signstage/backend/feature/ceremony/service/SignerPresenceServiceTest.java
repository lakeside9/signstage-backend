package com.eformworks.signstage.backend.feature.ceremony.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SignerPresenceServiceTest {
    @Test
    void countsSessionsAndNotifiesPartialDisconnectWithoutDoubleCounting() {
        var notifier = mock(CeremonyRealtimeNotifier.class);
        var service = new SignerPresenceService(notifier);
        service.register("a", "sub", 1L, 10L);
        service.register("a", "sub", 1L, 10L);
        service.register("b", "sub", 1L, 10L);
        assertThat(service.countConnections(1L, 10L)).isEqualTo(2);
        service.unsubscribe("a", "unrelated");
        assertThat(service.countConnections(1L, 10L)).isEqualTo(2);
        service.unsubscribe("a", "sub");
        assertThat(service.countConnections(1L, 10L)).isEqualTo(1);
        service.disconnect("b");
        service.disconnect("b");
        assertThat(service.countConnections(1L, 10L)).isZero();
        var order = inOrder(notifier);
        order.verify(notifier).notifySignerPresence(1L, 10L, 1);
        order.verify(notifier).notifySignerPresence(1L, 10L, 2);
        order.verify(notifier).notifySignerPresence(1L, 10L, 1);
        order.verify(notifier).notifySignerPresence(1L, 10L, 0);
        order.verifyNoMoreInteractions();
    }

    @Test
    void separatesEventsAndSigners() {
        var service = new SignerPresenceService(mock(CeremonyRealtimeNotifier.class));
        service.register("a", "sub", 1L, 10L);
        service.register("b", "sub", 2L, 10L);
        service.register("c", "sub", 1L, 11L);
        assertThat(service.countConnections(1L, 10L)).isEqualTo(1);
        assertThat(service.countConnections(2L, 10L)).isEqualTo(1);
        assertThat(service.countConnections(1L, 12L)).isZero();
    }
}
