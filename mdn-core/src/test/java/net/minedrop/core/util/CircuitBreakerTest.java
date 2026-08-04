package net.minedrop.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    @DisplayName("Successful calls keep circuit closed")
    void successKeepsClosed() {
        var cb = new CircuitBreaker("test", 3, Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) {
            String result = cb.execute(() -> "ok");
            assertEquals("ok", result);
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    @DisplayName("Consecutive failures open the circuit")
    void failuresOpenCircuit() {
        var cb = new CircuitBreaker("test", 3, Duration.ofSeconds(10));
        for (int i = 0; i < 3; i++) {
            cb.execute(() -> { throw new RuntimeException("fail"); });
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    @DisplayName("Open circuit rejects requests immediately")
    void openCircuitRejects() {
        var cb = new CircuitBreaker("test", 2, Duration.ofHours(1));
        cb.execute(() -> { throw new RuntimeException("fail"); });
        cb.execute(() -> { throw new RuntimeException("fail"); });

        AtomicInteger callCount = new AtomicInteger(0);
        String result = cb.execute(() -> { callCount.incrementAndGet(); return "should not run"; });

        assertNull(result);
        assertEquals(0, callCount.get());
    }

    @Test
    @DisplayName("Manual reset returns circuit to closed")
    void manualReset() {
        var cb = new CircuitBreaker("test", 2, Duration.ofHours(1));
        cb.execute(() -> { throw new RuntimeException("fail"); });
        cb.execute(() -> { throw new RuntimeException("fail"); });
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        cb.reset();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        String result = cb.execute(() -> "ok");
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("Success after some failures resets the counter")
    void successResetsCounter() {
        var cb = new CircuitBreaker("test", 5, Duration.ofSeconds(10));
        cb.execute(() -> { throw new RuntimeException("fail"); });
        cb.execute(() -> { throw new RuntimeException("fail"); });
        cb.execute(() -> "ok");
        cb.execute(() -> { throw new RuntimeException("fail"); });
        // Should still be closed — only 2 consecutive failures now
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    @DisplayName("executeVoid returns false when circuit is open")
    void executeVoidOpen() {
        var cb = new CircuitBreaker("test", 1, Duration.ofHours(1));
        cb.executeVoid(() -> { throw new RuntimeException("fail"); });
        assertFalse(cb.executeVoid(() -> {}));
    }

    @Test
    @DisplayName("executeVoid returns true on success")
    void executeVoidSuccess() {
        var cb = new CircuitBreaker("test", 3, Duration.ofSeconds(10));
        assertTrue(cb.executeVoid(() -> {}));
    }
}
