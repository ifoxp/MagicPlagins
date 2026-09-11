package dev.magic.fire.spell;

import org.bukkit.entity.Player;

/**
 * What a spell actually does.
 *
 * <p>A functional interface so effects can be written as lambdas or method
 * references next to the spell's numbers, instead of being reached through a
 * switch over string ids.</p>
 */
@FunctionalInterface
public interface SpellEffect {

    void cast(Player caster);
}
