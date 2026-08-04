package net.minedrop.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker pattern — prevents cascading failures when an external
 * service (Redis, MySQL) is down.
 *
 * <h3>States</h3>
 * <ul>
 *   <li><b>CLOSED</b> — Normal operation, requests pass through</li>
 *   <li><b>OPEN</b> — Too many failures, requests are rejected immediately</li>
 *   <li><b>HALF_OPEN</b> — Cooling period over, allowing a probe request</li>
 * </ul>
 *
 * <pre>{@code
 * CircuitBreaker redisBreaker = new CircuitBreaker("redis", 5, Duration.ofSeconds(30));
 * String result = redisBreaker.execute(() -> jedis.get("key"));
 * // Returns null if circuit is open or the callable returns null
 * }</pre>
 */
public final class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String name;
    private final int failureThreshold;
    private final Duration cooldownDuration;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(null);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /**
     * @param name             human-readable name for logging
     * @param failureThreshold consecutive failures before opening the circuit
     * @param cooldownDuration how long to wait before attempting recovery
     */
    public CircuitBreaker(String name, int failureThreshold, Duration cooldownDuration) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownDuration = cooldownDuration;
    }

    /**
     * Returns the current state of the circuit.
     */
    public State getState() {
        Instant opened = openedAt.get();
        if (opened == null) return State.CLOSED;
        if (Duration.between(opened, Instant.now()).compareTo(cooldownDuration) > 0) {
            return State.HALF_OPEN;
        }
        return State.OPEN;
    }

    /**
     * Executes the given callable if the circuit is closed or half-open.
     * Returns null if the circuit is open or the callable throws.
     *
     * @param callable the operation to attempt
     * @param <T>      return type
     * @return the result, or null if rejected/failed
     */
    public <T> T execute(Callable<T> callable) {
        State state = getState();

        if (state == State.OPEN) {
            log.debug("[{}] Circuit OPEN — rejecting request", name);
            return null;
        }

        try {
            T result = callable.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return null;
        }
    }

    /**
     * Executes a runnable. Returns true if it ran successfully.
     */
    public boolean executeVoid(Runnable runnable) {
        return execute(() -> { runnable.run(); return true; }) != null;
    }

    private void onSuccess() {
        failureCount.set(0);
        openedAt.set(null);
    }

    private void onFailure(Exception e) {
        int failures = failureCount.incrementAndGet();
        log.warn("[{}] Failure {}/{}: {}", name, failures, failureThreshold, e.getMessage());

        if (failures >= failureThreshold) {
            openedAt.set(Instant.now());
            log.error("[{}] Circuit OPEN — {} consecutive failures. Cooling down for {}.",
                    name, failures, cooldownDuration);
        }
    }

    /**
     * Manually resets the circuit to CLOSED state.
     */
    public void reset() {
        failureCount.set(0);
        openedAt.set(null);
        log.info("[{}] Circuit manually reset.", name);
    }

    /** Functional interface matching java.util.concurrent.Callable but without checked exceptions. */
    @FunctionalInterface
    public interface Callable<T> {
        T call() throws Exception;
    }
}
