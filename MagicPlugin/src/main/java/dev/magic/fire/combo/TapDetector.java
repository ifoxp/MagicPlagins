package dev.magic.fire.combo;

import org.bukkit.Input;

import java.util.EnumMap;
import java.util.Map;

/**
 * Turns raw key state into double-tap gestures.
 *
 * <p>Windows follow action-game convention: a tap pair has to land inside
 * ~250ms, which is the range where a double tap still feels deliberate but not
 * sluggish. Anything slower reads as two separate presses.</p>
 *
 * <p>Fed from {@link org.bukkit.event.player.PlayerInputEvent}, so the presses
 * are the client's real key events rather than something inferred from
 * velocity.</p>
 */
public final class TapDetector {

    /** Maximum gap between the two taps of a pair. */
    private static final long DOUBLE_TAP_MS = 250L;

    /** After firing, ignore the same key briefly so a hold cannot repeat it. */
    private static final long REARM_MS = 220L;

    private enum Key { FORWARD, BACKWARD, LEFT, RIGHT, JUMP }

    private final Map<Key, Boolean> down = new EnumMap<>(Key.class);
    private final Map<Key, Long> lastTapAt = new EnumMap<>(Key.class);
    private final Map<Key, Long> firedAt = new EnumMap<>(Key.class);

    /** Whether Shift is currently held, for gestures that care. */
    private boolean sneaking = false;

    public TapDetector() {
        for (Key key : Key.values()) {
            down.put(key, false);
            lastTapAt.put(key, 0L);
            firedAt.put(key, 0L);
        }
    }

    /**
     * Applies a fresh input snapshot.
     *
     * @return the double-tap gesture this snapshot completed, or null
     */
    public InputType update(Input input) {
        sneaking = input.isSneak();

        InputType hit = null;
        hit = check(Key.FORWARD, input.isForward(), InputType.TAP_FORWARD, hit);
        hit = check(Key.BACKWARD, input.isBackward(), InputType.TAP_BACKWARD, hit);
        hit = check(Key.LEFT, input.isLeft(), InputType.TAP_LEFT, hit);
        hit = check(Key.RIGHT, input.isRight(), InputType.TAP_RIGHT, hit);
        hit = check(Key.JUMP, input.isJump(), InputType.TAP_JUMP, hit);
        return hit;
    }

    private InputType check(Key key, boolean isDown, InputType gesture, InputType alreadyHit) {
        boolean was = down.get(key);
        down.put(key, isDown);

        // Only the press edge matters.
        if (!isDown || was) {
            return alreadyHit;
        }

        long now = System.currentTimeMillis();

        // Still in the cooldown from the last fire - treat as a fresh first tap.
        if (now - firedAt.get(key) < REARM_MS) {
            lastTapAt.put(key, now);
            return alreadyHit;
        }

        long since = now - lastTapAt.get(key);
        lastTapAt.put(key, now);

        if (since <= DOUBLE_TAP_MS) {
            firedAt.put(key, now);
            lastTapAt.put(key, 0L);
            // First gesture in a snapshot wins; simultaneous taps are ambiguous
            // anyway, and picking one beats firing two spells at once.
            return alreadyHit != null ? alreadyHit : gesture;
        }
        return alreadyHit;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void clear() {
        for (Key key : Key.values()) {
            down.put(key, false);
            lastTapAt.put(key, 0L);
            firedAt.put(key, 0L);
        }
    }
}
