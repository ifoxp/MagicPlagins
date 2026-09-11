package dev.magic.fire.mana;

/**
 * Mana drains while the cast stance is held and regenerates once it is released,
 * after a short delay so that tapping RMB on and off is not a free refill.
 */
public final class ManaPool {

    /**
     * Config-backed limits, set once at startup.
     *
     * <p>Static because a pool is created per player and the values are the
     * same for all of them; passing a config object into every constructor
     * would be noise.</p>
     */
    private static double configMax = 1000.0D;
    private static double configRegen = 50.0D;
    private static double configStanceRegen = 15.0D;
    private static long configRegenDelay = 800L;
    private static long configExhaust = 3000L;

    public static void configure(dev.magic.fire.Balance balance) {
        configMax = balance.manaMax();
        configRegen = balance.manaRegen();
        configStanceRegen = balance.manaRegenInStance();
        configRegenDelay = balance.manaRegenDelay();
        configExhaust = balance.manaExhaust();
    }

    /** The configured pool size, for the HUD readout. */
    public static double max() {
        return configMax;
    }




    /** Per second, once regen kicks in out of stance. */
    /** Per second while the stance is open - slower, but never negative. */
    private static final double STANCE_configRegen = 15.0D;
    /** Delay after releasing the stance before regen starts. */
    /** Lockout after burning out completely. */

    private double current = configMax;
    private long lastSpendAt = 0L;
    private long exhaustedUntil = 0L;

    public double current() {
        return current;
    }

    public double fraction() {
        return Math.clamp(current / configMax, 0.0D, 1.0D);
    }

    public boolean isExhausted() {
        return System.currentTimeMillis() < exhaustedUntil;
    }

    public boolean canAfford(double cost) {
        return !isExhausted() && current >= cost;
    }

    /** Spends mana if affordable. Returns false when the cast should be refused. */
    public boolean trySpend(double cost) {
        if (!canAfford(cost)) {
            return false;
        }
        current -= cost;
        lastSpendAt = System.currentTimeMillis();
        checkBurnout();
        return true;
    }

    /**
     * Called every tick while the stance is open. Holding the stance is free -
     * only casting costs mana - so this just regenerates, more slowly than
     * standing down does.
     */
    public void stanceRegenTick() {
        if (current >= configMax) {
            current = configMax;
            return;
        }
        if (System.currentTimeMillis() - lastSpendAt < configRegenDelay) {
            return;
        }
        current = Math.min(configMax, current + STANCE_configRegen / 20.0D);
    }

    /** Called every tick while the stance is not held. */
    public void regenTick() {
        if (current >= configMax) {
            current = configMax;
            return;
        }
        if (System.currentTimeMillis() - lastSpendAt < configRegenDelay) {
            return;
        }
        current = Math.min(configMax, current + configRegen / 20.0D);
    }

    private void checkBurnout() {
        if (current <= 0.0D) {
            current = 0.0D;
            exhaustedUntil = System.currentTimeMillis() + configExhaust;
        }
    }
}
