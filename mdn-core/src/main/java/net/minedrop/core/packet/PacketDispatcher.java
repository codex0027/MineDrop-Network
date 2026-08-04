package net.minedrop.core.packet;

import net.minedrop.api.packet.MDNPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minedrop.core.util.DeadLetterQueue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Dispatches incoming Redis Pub/Sub messages to registered packet handlers.
 * <p>
 * Other plugins (MDN-Economy, MDN-Social, etc.) register handlers for specific
 * packet types. When a packet arrives on the Redis bus, it's deserialized and
 * routed to all matching handlers.
 */
public final class PacketDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PacketDispatcher.class);

    private final Map<String, Consumer<MDNPacket>> handlers = new ConcurrentHashMap<>();
    private DeadLetterQueue deadLetterQueue;

    /**
     * Registers a handler for a specific packet type.
     *
     * @param packetType the packet type string (e.g. "ECONOMY_SYNC")
     * @param handler    callback invoked when a packet of this type arrives
     */
    public void registerHandler(String packetType, Consumer<MDNPacket> handler) {
        handlers.put(packetType, handler);
        log.info("Packet handler registered for type: {}", packetType);
    }

    /**
     * Unregisters a handler for a packet type.
     */
    public void unregisterHandler(String packetType) {
        handlers.remove(packetType);
    }

    /**
     * Sets the dead letter queue for failed packet handling.
     */
    public void setDeadLetterQueue(DeadLetterQueue dlq) {
        this.deadLetterQueue = dlq;
    }

    /**
     * Handles an incoming raw JSON message from Redis.
     * Deserializes it and dispatches to the appropriate handler.
     * If the handler throws, the packet is enqueued to the Dead Letter Queue.
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

            Consumer<MDNPacket> handler = handlers.get(packetType);
            if (handler == null) {
                log.debug("No handler registered for packet type: {}", packetType);
                return;
            }

            // Full deserialization via MDNPacket's polymorphic mapper
            MDNPacket packet = MDNPacket.deserialize(rawJson);

            // Try to handle — if it throws, push to DLQ
            try {
                handler.accept(packet);
            } catch (Exception handlerError) {
                log.error("Handler for '{}' threw — enqueuing to DLQ", packetType, handlerError);
                if (deadLetterQueue != null) {
                    deadLetterQueue.enqueue(rawJson, handlerError);
                }
            }

        } catch (Exception e) {
            log.error("Failed to deserialize packet: {}", truncate(rawJson), e);
            // If we can't even deserialize, enqueue the raw JSON for manual inspection
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
