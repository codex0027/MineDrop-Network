package net.minedrop.core.util;

import net.minedrop.api.packet.MDNPacket;
import net.minedrop.core.redis.RedisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Dead Letter Queue — ensures critical packets are never silently lost.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>When a packet handler throws, the raw JSON is pushed to the DLQ</li>
 *   <li>DLQ retries with exponential backoff: 1s → 2s → 4s → 8s → 16s</li>
 *   <li>After 5 failed retries, the packet moves to a permanent dead letter
 *       list for manual inspection by staff</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * DeadLetterQueue dlq = new DeadLetterQueue(redisManager, packet -> {
 *     // Retry handler — same logic as the original dispatcher
 *     packetDispatcher.dispatch(packet);
 * });
 *
 * // When a handler fails:
 * dlq.enqueue(rawJson, throwable);
 * }</pre>
 */
public final class DeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueue.class);

    /** Redis key for pending retries. */
    public static final String DLQ_KEY = "mdn:dead_letter";

    /** Redis key for permanently failed packets. */
    public static final String DLQ_PERMANENT_KEY = "mdn:dead_letter:permanent";

    /** Maximum retry attempts before moving to permanent DLQ. */
    private static final int MAX_RETRIES = 5;

    /** Exponential backoff delays in milliseconds. */
    private static final long[] BACKOFF_MS = {1_000, 2_000, 4_000, 8_000, 16_000};

    private final RedisManager redisManager;
    private final Consumer<String> retryHandler;
    private final ScheduledExecutorService scheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mdn-dlq");
        t.setDaemon(true);
        return t;
    });

    /** Tracks retry count for each packet (correlationId → attempts). */
    private final ConcurrentHashMap<String, Integer> retryCounts = new ConcurrentHashMap<>();

    public DeadLetterQueue(RedisManager redisManager, Consumer<String> retryHandler) {
        this.redisManager = redisManager;
        this.retryHandler = retryHandler;

        // Periodically process the DLQ
        scheduler.scheduleAtFixedRate(this::processQueue, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Enqueues a failed packet for retry.
     *
     * @param rawJson the raw JSON string that failed
     * @param error   the exception that caused the failure
     */
    public void enqueue(String rawJson, Throwable error) {
        // Push to Redis DLQ list with metadata
        String entry = String.format("{\"json\":%s,\"error\":\"%s\",\"timestamp\":%d}",
                rawJson,
                error != null ? error.getMessage().replace("\"", "'") : "unknown",
                System.currentTimeMillis());
        redisManager.lpush(DLQ_KEY, entry);
        log.warn("Packet enqueued to DLQ: {} (error: {})",
                truncate(rawJson), error != null ? error.getMessage() : "unknown");
    }

    /**
     * Processes pending DLQ entries — retries each with backoff.
     */
    private void processQueue() {
        String entry;
        while ((entry = redisManager.rpop(DLQ_KEY)) != null) {
            final String finalEntry = entry;
            try {
                // Extract the original JSON from the DLQ entry
                String originalJson = extractJson(finalEntry);
                if (originalJson == null) {
                    log.warn("Corrupt DLQ entry, discarding: {}", truncate(finalEntry));
                    continue;
                }

                // Check retry count
                String corrId = extractCorrelationId(originalJson);
                int attempts = retryCounts.getOrDefault(corrId, 0);

                if (attempts >= MAX_RETRIES) {
                    // Permanent failure — move to permanent DLQ
                    redisManager.lpush(DLQ_PERMANENT_KEY, finalEntry);
                    retryCounts.remove(corrId);
                    log.error("Packet permanently failed after {} retries. Moved to {}.",
                            MAX_RETRIES, DLQ_PERMANENT_KEY);
                    continue;
                }

                // Schedule retry with exponential backoff
                final long delay = BACKOFF_MS[attempts];
                final int nextAttempt = attempts + 1;
                retryCounts.put(corrId, nextAttempt);

                scheduler.schedule(() -> {
                    try {
                        log.info("DLQ retry {}/{} for packet {} (delay: {}ms)",
                                nextAttempt, MAX_RETRIES, corrId, delay);
                        retryHandler.accept(originalJson);
                        // Success! Remove from retry tracker
                        retryCounts.remove(corrId);
                        log.info("DLQ retry succeeded for {}", corrId);
                    } catch (Exception e) {
                        log.warn("DLQ retry {}/{} failed for {}: {}",
                                nextAttempt, MAX_RETRIES, corrId, e.getMessage());
                        // Re-enqueue for next retry
                        redisManager.lpush(DLQ_KEY, finalEntry);
                    }
                }, delay, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                log.error("Error processing DLQ entry: {}", truncate(entry), e);
            }
        }
    }

    /**
     * Returns how many items are currently in the dead letter queue.
     */
    public long getPendingCount() {
        return redisManager.llen(DLQ_KEY);
    }

    /**
     * Returns how many items are permanently failed.
     */
    public long getPermanentCount() {
        return redisManager.llen(DLQ_PERMANENT_KEY);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // ── Helpers ──

    private String extractJson(String dlqEntry) {
        try {
            int jsonStart = dlqEntry.indexOf("\"json\":") + 7;
            int jsonEnd = dlqEntry.indexOf(",\"error\"");
            if (jsonStart < 7 || jsonEnd < 0) return null;
            return dlqEntry.substring(jsonStart, jsonEnd)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCorrelationId(String json) {
        try {
            int idx = json.indexOf("\"correlationId\"");
            if (idx < 0) return "unknown";
            int start = json.indexOf('"', idx + 16) + 1;
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 150 ? s.substring(0, 150) + "..." : s;
    }
}
