package com.orders.order_management.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.order_management.transaction.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class KitchenEventService {

    private final ObjectMapper objectMapper;

    public KitchenEventService(@org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper mapper) {
        if (mapper != null) {
            this.objectMapper = mapper;
        } else {
            ObjectMapper fallback = new ObjectMapper();
            try {
                fallback.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                fallback.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            } catch (Throwable ignored) {}
            this.objectMapper = fallback;
        }
    }

    // Emitters keyed by outletId. "all" represents super-admin/consolidated listeners.
    private final Map<String, List<SseEmitter>> emittersByOutlet = new ConcurrentHashMap<>();

    // 30 minute timeout before reconnect
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * Subscribe a client to real-time kitchen updates for an outlet.
     */
    public SseEmitter subscribe(String outletId) {
        final String effectiveKey = (outletId == null || outletId.isBlank() 
                || "all".equalsIgnoreCase(outletId) || "consolidated".equalsIgnoreCase(outletId)) 
                ? "all" 
                : outletId.trim();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emittersByOutlet.computeIfAbsent(effectiveKey, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(effectiveKey, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(effectiveKey, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE emitter error for outlet [{}]: {}", effectiveKey, e.getMessage());
            removeEmitter(effectiveKey, emitter);
        });

        // Send initial handshake so browser EventSource transitions to OPEN immediately
        try {
            String initJson = objectMapper.writeValueAsString(Map.of(
                    "status", "CONNECTED",
                    "outletId", effectiveKey
            ));
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(initJson));
        } catch (Exception e) {
            log.debug("Failed to send INIT event: {}", e.getMessage());
            removeEmitter(effectiveKey, emitter);
        }

        log.info("KDS client subscribed for outlet: [{}]. Current subscriber count: {}", 
                effectiveKey, emittersByOutlet.getOrDefault(effectiveKey, List.of()).size());
        return emitter;
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = emittersByOutlet.get(key);
        if (list != null) {
            list.remove(emitter);
            int remaining = list.size();
            if (list.isEmpty()) {
                emittersByOutlet.remove(key);
            }
            log.info("KDS client disconnected from outlet: [{}]. Remaining subscriber count: {}", key, remaining);
        }
    }

    /**
     * Broadcast order state changes to targeted outlet displays and admin displays.
     */
    public void publishOrderUpdate(OrderResponse order) {
        if (order == null) return;

        try {
            String jsonPayload = objectMapper.writeValueAsString(Map.of(
                    "type", "ORDER_UPDATE",
                    "order", order
            ));

            String outletId = order.getOutletId();

            // 1. Deliver to specific outlet listeners
            if (outletId != null && !outletId.isBlank() && !"all".equalsIgnoreCase(outletId)) {
                sendToKey(outletId.trim(), "ORDER_UPDATE", jsonPayload);
            }

            // 2. Deliver to global / "all" listeners (Admin multi-outlet screen)
            sendToKey("all", "ORDER_UPDATE", jsonPayload);
        } catch (Throwable t) {
            log.error("Failed to publish SSE order update: {}", t.getMessage());
        }
    }

    /**
     * Broadcast queue cleared notification.
     */
    public void publishQueueCleared(String outletId) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(Map.of(
                    "type", "QUEUE_CLEARED",
                    "outletId", outletId != null ? outletId : "all"
            ));

            if (outletId != null && !outletId.isBlank() && !"all".equalsIgnoreCase(outletId)) {
                sendToKey(outletId.trim(), "QUEUE_CLEARED", jsonPayload);
            }
            sendToKey("all", "QUEUE_CLEARED", jsonPayload);
        } catch (Throwable t) {
            log.error("Failed to publish SSE queue cleared: {}", t.getMessage());
        }
    }

    /**
     * Keepalive heartbeat every 25s prevents proxy/ALB/NGINX idle drops (default 60s)
     * and prunes disconnected browser tabs.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (emittersByOutlet.isEmpty()) return;

        emittersByOutlet.forEach((key, list) -> {
            List<SseEmitter> dead = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("PING")
                            .data("keep-alive"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                list.removeAll(dead);
            }
        });
    }

    private void sendToKey(String key, String eventName, String data) {
        List<SseEmitter> list = emittersByOutlet.get(key);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            list.removeAll(deadEmitters);
            if (list.isEmpty()) {
                emittersByOutlet.remove(key);
            }
        }
    }
}
