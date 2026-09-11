package dev.magic.fire.spell;

import dev.magic.fire.combo.InputType;

import java.util.List;

/**
 * One spell: what triggers it, what it costs, and what it does.
 *
 * <p>This replaces the old arrangement where the gesture table, the numbers and
 * the dispatch switch lived in three different files and had to be kept in step
 * by hand. A spell is now declared once - pattern, cost, damage and effect
 * together - and the registry derives the gesture list, the tooltip and the
 * dispatch from that single declaration.</p>
 *
 * <p>Adding a spell is one call to {@link SpellBook#add}; nothing else needs
 * touching.</p>
 *
 * @param id         unique key, also the cooldown key
 * @param title      display name
 * @param pattern    gesture that casts it
 * @param mana       cost; for channelled spells this is the per-beat cost
 * @param cooldownMs gate between casts
 * @param damage     half-hearts at full effect, 0 for utility
 * @param range      blast radius or reach in blocks
 * @param duration   how long the effect lasts in ticks, 0 for instant
 * @param effect     what actually happens
 */
public record SpellDef(
        String id,
        String title,
        List<InputType> pattern,
        int mana,
        long cooldownMs,
        double damage,
        double range,
        int duration,
        SpellEffect effect) {

    /** Longest allowed pause between two inputs of the pattern. */
    public static final long GAP_MS = 700L;

    public int length() {
        return pattern.size();
    }

    /** Human-readable gesture, e.g. {@code "W W ЛКМ"}. */
    public String gesture() {
        return String.join(" ", pattern.stream().map(InputType::display).toList());
    }

    public boolean isOffensive() {
        return damage > 0.0D;
    }

    /** Hearts, for display - players think in hearts, not half-hearts. */
    public double hearts() {
        return damage / 2.0D;
    }

    /** True when this spell is expensive enough to deserve emphasis in the UI. */
    public boolean isHeavy() {
        return mana >= 50;
    }
}
