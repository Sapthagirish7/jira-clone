package com.jira.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * PresenceTracker maintains in-memory state of which sessions are viewing which boards.
 *
 * In a multi-node deployment this would need to move to a shared store (e.g. Redis
 * pub/sub + sorted set per projectId) since in-memory state is node-local.
 */
@Component
@Slf4j
public class PresenceTracker {

    // projectId -> set of sessionIds currently viewing
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<String>> projectViewers
            = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        log.debug("WS session connected: {}", event.getMessage().getHeaders().get("simpSessionId"));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        projectViewers.values().forEach(viewers -> viewers.remove(sessionId));
        log.debug("WS session disconnected: {}", sessionId);
    }

    public void addViewer(String projectId, String sessionId) {
        projectViewers.computeIfAbsent(projectId, k -> new CopyOnWriteArraySet<>())
                      .add(sessionId);
    }

    public void removeViewer(String projectId, String sessionId) {
        projectViewers.getOrDefault(projectId, new CopyOnWriteArraySet<>()).remove(sessionId);
    }

    public int getViewerCount(String projectId) {
        return projectViewers.getOrDefault(projectId, new CopyOnWriteArraySet<>()).size();
    }
}
