package com.eformworks.signstage.backend.feature.ceremony.service;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Active signing sessions only; administrators and projectors are not counted. */
@Service
@RequiredArgsConstructor
public class SignerPresenceService {
    private final CeremonyRealtimeNotifier notifier;
    private final Map<String, Connection> sessions = new HashMap<>();

    public synchronized void register(String sessionId, String subscriptionId, Long eventId, Long signerId) {
        var next = new Connection(subscriptionId, eventId, signerId);
        var previous = sessions.put(sessionId, next);
        if (next.equals(previous)) return;
        if (previous != null) publish(previous);
        publish(next);
    }

    public synchronized void disconnect(String sessionId) {
        var previous = sessions.remove(sessionId);
        if (previous != null) publish(previous);
    }

    public synchronized void unsubscribe(String sessionId, String subscriptionId) {
        var connection = sessions.get(sessionId);
        if (connection != null && java.util.Objects.equals(connection.subscriptionId(), subscriptionId)) {
            disconnect(sessionId);
        }
    }

    public synchronized int countConnections(Long eventId, Long signerId) {
        return (int) sessions.values().stream()
                .filter(c -> c.eventId().equals(eventId) && c.signerId().equals(signerId)).count();
    }

    private void publish(Connection connection) {
        notifier.notifySignerPresence(connection.eventId(), connection.signerId(),
                countConnections(connection.eventId(), connection.signerId()));
    }

    private record Connection(String subscriptionId, Long eventId, Long signerId) {}
}
