package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe statistics: how many messages were blocked per logger, plus a small
 * ring buffer of recent blocked entries for the in-game status command.
 */
public final class SilencerStats {
    private static final LongAdder TOTAL = new LongAdder();
    private static final Object RING_LOCK = new Object();
    private static final List<Entry> RING = new ArrayList<>();

    public record Entry(String loggerName, Level level, String message, Instant time) {
    }

    private SilencerStats() {
    }

    public static void record(String loggerName, Level level, String message) {
        TOTAL.increment();
        synchronized (RING_LOCK) {
            RING.add(new Entry(loggerName, level, message, Instant.now()));
            while (RING.size() > Constants.RECENT_BUFFER_SIZE) {
                RING.remove(0);
            }
        }
    }

    public static long getTotal() {
        return TOTAL.sum();
    }

    public static List<Entry> getRecent() {
        synchronized (RING_LOCK) {
            return List.copyOf(RING);
        }
    }

    public static void reset() {
        TOTAL.reset();
        synchronized (RING_LOCK) {
            RING.clear();
        }
    }
}
