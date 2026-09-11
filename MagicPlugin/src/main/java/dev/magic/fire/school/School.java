package dev.magic.fire.school;

import dev.magic.fire.element.Element;
import dev.magic.fire.spell.SpellBook;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * A school of magic.
 *
 * <p>Each school owns its spell book, its charged bolt, its sustained channel
 * and its out-of-combat utility. The stance machinery, mana pool, gesture
 * detection and HUD are shared and know nothing about individual spells, so a
 * new school is one implementation of this interface plus an {@link Element}
 * entry - no changes to the listener or the plugin class.</p>
 */
public interface School {

    Element element();

    /** Gesture-triggered spells. */
    SpellBook book();

    // ------------------------------------------------------------------- bolt

    /** The charged projectile, fired when sustained clicking stops. */
    void bolt(Player caster, double power);

    double boltDamage(double power);

    int boltMana(double power);

    /** Name shown when the bolt lands, which may change with charge. */
    String boltName(double power);

    // ---------------------------------------------------------------- channel

    /**
     * The sustained stream, once the charge converts.
     *
     * @param power how hard the player is clicking, 0..1 - stronger clicking
     *              makes the stream reach further and hit harder
     */
    void channel(Player caster, int tick, double power);

    double channelDps(double power);

    /** Per-beat mana cost while channelling. */
    int channelMana();

    String channelName();

    // ---------------------------------------------------------------- utility

    /**
     * Out-of-combat interaction with a block, outside the stance.
     *
     * @return true if the school did something, so the event is consumed
     */
    boolean interact(Player player, Block block);

    // ---------------------------------------------------------------- passive

    /**
     * Kept applied for as long as the focus is held.
     *
     * <p>Holding the focus is the stance, so it should be worth something on its
     * own - a mage with their wand out is already different from one without it.
     * Called every tick, so implementations must be cheap and idempotent.</p>
     */
    void passive(Player player);

    /** Undone the moment the focus is put away. */
    void clearPassive(Player player);

    /** One line describing the passive, for the focus tooltip. */
    String passiveName();

    // --------------------------------------------------------------- ambience

    /** Ambient effect while the stance is open. */
    void aura(Player player, double manaFraction, int tick);

    /** The charging orb in the caster's hand. */
    void chargeGlow(Player player, double power, int tick);
}
