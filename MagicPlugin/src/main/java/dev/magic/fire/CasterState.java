package dev.magic.fire;

import dev.magic.fire.ability.ChargeState;
import dev.magic.fire.combo.InputBuffer;
import dev.magic.fire.combo.TapDetector;
import dev.magic.fire.mana.ManaPool;
import dev.magic.fire.school.School;
import dev.magic.fire.ui.MagicHud;

import java.util.HashMap;
import java.util.Map;

/** Everything the plugin tracks per player. */
public final class CasterState {

    public final InputBuffer inputs = new InputBuffer();
    public final ManaPool mana = new ManaPool();
    public final MagicHud hud = new MagicHud();
    public final ChargeState charge = new ChargeState();
    public final TapDetector taps = new TapDetector();

    /** True while the cast stance is open. */
    public boolean stanceActive = false;

    /** Last observed pitch, for detecting camera flicks. */
    public float lastPitch = 0.0F;

    /** Ticks spent in the current stance, drives the aura animation. */
    public int stanceTicks = 0;

    /** Debounces right click, which can arrive twice in a tick. */
    public long lastRightClickAt = 0L;

    /**
     * True when the current left-click run is the sustained channel rather than
     * the volley - decided by whether Shift was held when it began.
     */
    public boolean channelMode = false;

    /** When Q last fired, so its accompanying swing can be ignored. */
    public long lastDropAt = 0L;

    /** When the current right-click hold began, or 0 if not holding. */
    public long rightHeldSince = 0L;

    /**
     * The school whose passive is currently applied.
     *
     * <p>Tracked so swapping focuses mid-stance drops the old passive before
     * granting the new one - otherwise a player could hold both by alternating
     * between two wands.</p>
     */
    public School lastPassive = null;

    /** Last gesture input, for the idle timeout. */
    public long lastInputAt = 0L;

    /**
     * Per-spell cooldowns. A shared cooldown meant a cheap dash locked out an
     * expensive meteor, which made combos feel arbitrary.
     */
    private final Map<String, Long> cooldowns = new HashMap<>();

    /** Fall damage is waived until this timestamp. */
    private long fallImmuneUntil = 0L;

    public boolean onCooldown(String spellId) {
        Long until = cooldowns.get(spellId);
        return until != null && System.currentTimeMillis() < until;
    }

    public void setCooldown(String spellId, long ms) {
        if (ms > 0) {
            cooldowns.put(spellId, System.currentTimeMillis() + ms);
        }
    }

    /**
     * Casting must not be punished by fall damage - a leap puts the player in
     * the air on purpose. A later cast extends the grace rather than replacing
     * a longer window with a shorter one.
     */
    public void grantFallImmunity(int ticks) {
        fallImmuneUntil = Math.max(fallImmuneUntil,
                System.currentTimeMillis() + ticks * 50L);
    }

    public boolean isFallImmune() {
        return System.currentTimeMillis() < fallImmuneUntil;
    }

    /** Extends the grace while the player is still airborne after a cast. */
    public void refreshFallImmunity() {
        grantFallImmunity(20);
    }
}
