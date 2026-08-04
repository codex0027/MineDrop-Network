package net.minedrop.core.packet;

import net.minedrop.api.packet.MDNPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minedrop.core.util.DeadLetterQueue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dispatches incoming Redis Pub/Sub messages to registered packet handlers.
 * <p>
 * Supports multiple handlers per packet type (fixes M-13). Other plugins
 * (MDN-Economy, MDN-Social, etc.) register handlers for specific packet types.
 * When a packet arrives on the Redis bus, it's deserialized and routed to
 * all matching handlers.
 */
public final class PacketDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PacketDispatcher.class);

    /** Multi-handler: one packet type → many consumers. */
    private final Map<String, List<Consumer<MDNPacket>>> handlers = new ConcurrentHashMap<>();
    private DeadLetterQueue deadLetterQueue;

    /**
     * Registers a handler for a specific packet type.
     * Multiple handlers can be registered for the same type (fixes M-13).
     *
     * @param packetType the packet type string (e.g. "ECONOMY_SYNC")
     * @param handler    callback invoked when a packet of this type arrives
     */
    public void registerHandler(String packetType, Consumer<MDNPacket> handler) {
        handlers.computeIfAbsent(packetType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("Packet handler registered for type: {} (total: {})",
                packetType, handlers.get(packetType).size());
    }

    /**
     * Unregisters a specific handler for a packet type.
     */
    public void unregisterHandler(String packetType, Consumer<MDNPacket> handler) {
        List<Consumer<MDNPacket>> list = handlers.get(packetType);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) handlers.remove(packetType);
        }
    }

    /**
     * Sets the dead letter queue for failed packet handling.
     */
    public void setDeadLetterQueue(DeadLetterQueue dlq) {
        this.deadLetterQueue = dlq;
    }

    /**
     * Handles an incoming raw JSON message from Redis.
     * Deserializes it and dispatches to all matching handlers.
     * Each handler failure is caught individually — one buggy handler
     * won't break others (fixes M-13).
     *
     * @param rawJson the raw JSON string from Redis Pub/Sub
     */
    public void dispatch(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;

        try {
            // Quick peek at packet type without full deserialization
            String packetType = extractPacketType(rawJson);
            if (packetType == null) {
                log.warn("Received packet with no identifiable type: {}", truncate(rawJson));
                return;
            }

            List<Consumer<MDNPacket>> handlerList = handlers.get(packetType);
            if (handlerList == null || handlerList.isEmpty()) {
                log.debug("No handler registered for packet type: {}", packetType);
                return;
            }

            // Full deserialization via MDNPacket's polymorphic mapper
            MDNPacket packet = MDNPacket.deserialize(rawJson);

            // Dispatch to all handlers — each in its own try/catch
            for (Consumer<MDNPacket> handler : handlerList) {
                try {
                    handler.accept(packet);
                } catch (Exception handlerError) {
                    log.error("Handler for '{}' threw — enqueuing to DLQ", packetType, handlerError);
                    if (deadLetterQueue != null) {
                        deadLetterQueue.enqueue(rawJson, handlerError);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to deserialize packet: {}", truncate(rawJson), e);
            if (deadLetterQueue != null) {
                deadLetterQueue.enqueue(rawJson, e);
            }
        }
    }

    /**
     * Quickly extracts the packetType from JSON without full deserialization.
     */
    private String extractPacketType(String json) {
        int idx = json.indexOf("\"packetType\"");
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx);
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
