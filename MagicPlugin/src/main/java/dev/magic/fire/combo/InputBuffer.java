package dev.magic.fire.combo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Per-player ring buffer of recent inputs. Entries older than {@link #WINDOW_MS}
 * are dropped, so a combo has to be performed as one continuous gesture.
 *
 * <p>The window is deliberately generous: inputs reach the server with network
 * latency, so tight timing windows would simply break for anyone above ~100ms
 * ping.</p>
 */
public final class InputBuffer {

    /** How long a single input stays relevant. */
    public static final long WINDOW_MS = 1200L;

    /** Longest pattern we support - keeps the buffer bounded. */
    private static final int MAX_SIZE = 8;

    private final Deque<Entry> entries = new ArrayDeque<>();

    public record Entry(InputType type, long at) {}

    public void push(InputType type) {
        long now = System.currentTimeMillis();
        entries.addLast(new Entry(type, now));
        while (entries.size() > MAX_SIZE) {
            entries.removeFirst();
        }
        prune(now);
    }

    /** Drops inputs that fell out of the timing window. */
    public void prune(long now) {
        while (!entries.isEmpty() && now - entries.peekFirst().at() > WINDOW_MS) {
            entries.removeFirst();
        }
    }

    /** The current gesture, oldest first. */
    public List<InputType> snapshot() {
        prune(System.currentTimeMillis());
        List<InputType> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(e.type());
        }
        return out;
    }

    /** Milliseconds since the newest input, or -1 if the buffer is empty. */
    public long sinceLastInputMs() {
        if (entries.isEmpty()) {
            return -1L;
        }
        return System.currentTimeMillis() - entries.peekLast().at();
    }

    /** Milliseconds between the last two inputs, or -1 if there are fewer than two. */
    public long lastGapMs() {
        if (entries.size() < 2) {
            return -1L;
        }
        Entry[] arr = entries.toArray(new Entry[0]);
        return arr[arr.length - 1].at() - arr[arr.length - 2].at();
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
