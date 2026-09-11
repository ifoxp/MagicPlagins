package dev.magic.fire.ability;

/**
 * Tracks the Shift + left-click channel.
 *
 * <p>The server never sees "left mouse is down" - only swing packets, which the
 * vanilla client repeats solely while digging a block. So the channel is driven
 * by the player clicking repeatedly with Shift held: each swing inside
 * {@link #HOLD_GAP_MS} keeps it alive, and how fast they click sets its power.</p>
 *
 * <p>Two phases. Early on it is a charge - release and a single bolt fires,
 * scaled by how long it built. Past {@link #CHANNEL_AT_TICKS} it becomes a
 * sustained stream that keeps going for as long as the clicking does.</p>
 */
public final class ChargeState {

    /** Gap above which two swings are separate clicks rather than a stream. */
    private static final long HOLD_GAP_MS = 400L;

    /** No swing for this long means the player stopped. */
    private static final long RELEASE_AFTER_MS = 350L;

    /** Charge is full at this many ticks. */
    public static final int FULL_CHARGE_TICKS = 40;

    /** Past this, the charge becomes a sustained stream. */
    public static final int CHANNEL_AT_TICKS = 45;

    /** Clicks per second that counts as full intensity. */
    private static final double PEAK_CPS = 6.0D;

    private boolean active = false;
    private long lastSwingAt = 0L;
    private int ticks = 0;
    private int swingsInRun = 0;
    private int swingsThisSecond = 0;
    private long secondStartedAt = 0L;
    private double intensity = 0.0D;

    /** Records a swing. Returns true if the channel is running. */
    public boolean onSwing() {
        long now = System.currentTimeMillis();
        boolean continuing = now - lastSwingAt <= HOLD_GAP_MS;
        lastSwingAt = now;

        if (!continuing) {
            swingsInRun = 1;
            swingsThisSecond = 1;
            secondStartedAt = now;
            return active;
        }

        swingsInRun++;
        swingsThisSecond++;

        // Rolling click rate, so harder clicking measurably raises intensity.
        long window = now - secondStartedAt;
        if (window >= 500L) {
            double cps = swingsThisSecond * 1000.0D / window;
            intensity = Math.clamp(cps / PEAK_CPS, 0.0D, 1.0D);
            swingsThisSecond = 0;
            secondStartedAt = now;
        }

        if (!active && swingsInRun >= 2) {
            active = true;
            ticks = 0;
        }
        return active;
    }

    /** Advances the channel. Returns true while it is still running. */
    public boolean tick() {
        if (!active) {
            return false;
        }
        if (System.currentTimeMillis() - lastSwingAt > RELEASE_AFTER_MS) {
            return false;
        }
        ticks++;
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public int ticks() {
        return ticks;
    }

    /** True once the charge has converted into a sustained stream. */
    public boolean isChannelling() {
        return active && ticks >= CHANNEL_AT_TICKS;
    }

    /** 0..1 across the charge window, capped once full. */
    public double power() {
        return Math.min(1.0D, (double) ticks / FULL_CHARGE_TICKS);
    }

    /**
     * How hard the player is clicking, 0..1.
     *
     * <p>Drives the stream's reach and damage, so mashing is rewarded once the
     * charge has already converted - the difference between a pilot light and a
     * blowtorch.</p>
     */
    public double intensity() {
        return intensity;
    }

    /** Consumes the channel and returns how charged it was. */
    public double release() {
        double power = power();
        reset();
        return power;
    }

    public void reset() {
        active = false;
        ticks = 0;
        swingsInRun = 0;
        swingsThisSecond = 0;
        intensity = 0.0D;
        lastSwingAt = 0L;
    }
}
