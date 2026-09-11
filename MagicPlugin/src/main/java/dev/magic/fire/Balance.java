package dev.magic.fire;

import dev.magic.fire.element.Element;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every tunable number, read from {@code config.yml}.
 *
 * <p>Numbers used to be literals inside each spell declaration, which meant a
 * balance change was a code change and a rebuild. They live in config now, and
 * every lookup takes a default - so a partial or missing file still runs, and
 * the file only needs to list what is actually being overridden.</p>
 */
public final class Balance {

    private final JavaPlugin plugin;

    public Balance(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    /** Re-reads the file from disk. */
    public void reload() {
        plugin.reloadConfig();
    }

    // ---------------------------------------------------------------- global

    /**
     * Fraction of spell damage that ignores armour, 0..1.
     *
     * <p>Not a switch: full bypass makes armour pointless, while none makes the
     * school pointless against anyone geared. A fraction keeps both meaningful,
     * and leaves room for a progression that raises it as a mage advances.</p>
     */
    public double armourPierce() {
        return Math.clamp(plugin.getConfig()
                .getDouble("armour-pierce", 0.5D), 0.0D, 1.0D);
    }

    /** Global damage scale, applied on top of every spell's own number. */
    public double damageMultiplier() {
        return plugin.getConfig().getDouble("damage-multiplier", 1.0D);
    }

    // ------------------------------------------------------------------ mana

    public int manaMax() {
        return plugin.getConfig().getInt("mana.maximum", 1000);
    }

    public double manaRegen() {
        return plugin.getConfig().getDouble("mana.regen", 50.0D);
    }

    public double manaRegenInStance() {
        return plugin.getConfig().getDouble("mana.regen-in-stance", 15.0D);
    }

    public long manaRegenDelay() {
        return plugin.getConfig().getLong("mana.regen-delay", 800L);
    }

    public long manaExhaust() {
        return plugin.getConfig().getLong("mana.exhaust", 3000L);
    }

    // ----------------------------------------------------------------- spells

    /** Mana cost for a spell, or {@code fallback} if unset. */
    public int mana(Element element, String spell, int fallback) {
        return spellSection(element, spell).getInt("mana", fallback);
    }

    public long cooldown(Element element, String spell, long fallback) {
        return spellSection(element, spell).getLong("cooldown", fallback);
    }

    /** Damage for a spell, with the global multiplier already applied. */
    public double damage(Element element, String spell, double fallback) {
        double raw = spellSection(element, spell).getDouble("damage", fallback);
        return raw * damageMultiplier();
    }

    public double range(Element element, String spell, double fallback) {
        return spellSection(element, spell).getDouble("range", fallback);
    }

    public int duration(Element element, String spell, int fallback) {
        return spellSection(element, spell).getInt("duration", fallback);
    }

    // ------------------------------------------------------------------ bolt

    public int boltManaBase(Element element, int fallback) {
        return path(element, "bolt.mana-base").getInt("mana-base", fallback);
    }

    public int boltManaPerCharge(Element element, int fallback) {
        return path(element, "bolt.mana-per-charge")
                .getInt("mana-per-charge", fallback);
    }

    /**
     * Damage for one volley tier.
     *
     * <p>Both schools ladder their basic attack, so the lookup is keyed by
     * school and tier size - fire counts cubes, ice counts shards.</p>
     */
    public double tierDamage(Element element, int tier, double fallback) {
        double raw = plugin.getConfig().getDouble(
                element.name().toLowerCase() + ".bolt.tier-" + tier + ".damage",
                fallback);
        return raw * damageMultiplier();
    }

    public double tierKnockback(Element element, int tier, double fallback) {
        return plugin.getConfig().getDouble(
                element.name().toLowerCase() + ".bolt.tier-" + tier + ".knockback",
                fallback);
    }

    public int iceBoltSlow(double power) {
        int min = plugin.getConfig().getInt("ice.bolt.slow-min", 20);
        int max = plugin.getConfig().getInt("ice.bolt.slow-max", 80);
        return min + (int) (power * (max - min));
    }

    // --------------------------------------------------------------- channel

    public int channelMana(Element element, int fallback) {
        return path(element, "channel.mana").getInt("mana", fallback);
    }

    /** Per-hit channel damage at a given intensity, multiplier applied. */
    public double channelDamage(Element element, double intensity,
                                double fallbackMin, double fallbackMax) {
        ConfigurationSection section = path(element, "channel.damage-min");
        double min = section.getDouble("damage-min", fallbackMin);
        double max = section.getDouble("damage-max", fallbackMax);
        return (min + intensity * (max - min)) * damageMultiplier();
    }

    public double channelReach(Element element, double focus,
                               double fallbackMin, double fallbackMax) {
        ConfigurationSection section = path(element, "channel.reach-min");
        double min = section.getDouble("reach-min", fallbackMin);
        double max = section.getDouble("reach-max", fallbackMax);
        return min + focus * (max - min);
    }

    // ------------------------------------------------------------------ form

    /**
     * Mana per second while a form is held.
     *
     * <p>Keyed by school: the two forms cost different amounts because they buy
     * different things - inferno buys speed, frost buys staying power.</p>
     */
    public double formDrain(Element element, double fallback) {
        return plugin.getConfig().getDouble(
                element.name().toLowerCase() + ".form.drain", fallback);
    }

    /** Fraction of the pool needed to enter, so a form cannot be flickered. */
    public double formMinimumMana(Element element, double fallback) {
        return plugin.getConfig().getDouble(
                element.name().toLowerCase() + ".form.minimum-mana", fallback);
    }

    public double formSpeedBonus(Element element, double fallback) {
        return plugin.getConfig().getDouble(
                element.name().toLowerCase() + ".form.speed-bonus", fallback);
    }

    public int formJumpLevel(Element element, int fallback) {
        return plugin.getConfig().getInt(
                element.name().toLowerCase() + ".form.jump-level", fallback);
    }

    // ------------------------------------------------------------- internals

    private ConfigurationSection spellSection(Element element, String spell) {
        return section(element.name().toLowerCase() + ".spells." + spell);
    }

    /** Section holding a dotted key, so callers can read sibling values. */
    private ConfigurationSection path(Element element, String key) {
        String full = element.name().toLowerCase() + "." + key;
        int cut = full.lastIndexOf('.');
        return section(full.substring(0, cut));
    }

    /** Never null - an absent section reads as empty, so defaults apply. */
    private ConfigurationSection section(String path) {
        ConfigurationSection found = plugin.getConfig().getConfigurationSection(path);
        return found != null ? found : plugin.getConfig().createSection(path);
    }
}
