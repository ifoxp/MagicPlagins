package dev.magic.fire.element;

import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

/**
 * A school of magic.
 *
 * <p>Everything that differs between schools lives here: the focus item, the
 * palette the HUD and particles draw from, and the display name. The gesture
 * system, mana pool and stance machinery are shared, so adding a school means
 * adding an enum entry plus its spell implementations - not a second copy of
 * the plugin.</p>
 */
public enum Element {

    FIRE("Вогонь", "вогню",
            Material.BLAZE_ROD,
            "🔥",
            TextColor.color(0xFFF6D8),   // core
            TextColor.color(0xFFC24B),   // hot
            TextColor.color(0xFF7A2F),   // mid
            TextColor.color(0xC42E17),   // low
            ShadowColor.shadowColor(0xCCFF6A18),
            Color.fromRGB(0xFF, 0xC2, 0x4B),
            Color.fromRGB(0xFF, 0x7A, 0x2F)),

    ICE("Лід", "льоду",
            Material.PRISMARINE_SHARD,
            "❄",
            TextColor.color(0xF2FEFF),
            TextColor.color(0xA8ECFF),
            TextColor.color(0x5FC8F0),
            TextColor.color(0x2A7FB8),
            ShadowColor.shadowColor(0xCC2E9FE0),
            Color.fromRGB(0xA8, 0xEC, 0xFF),
            Color.fromRGB(0x5F, 0xC8, 0xF0));

    /** Nominative name, for headings. */
    public final String title;

    /** Genitive form, for "стихія X". */
    public final String genitive;

    /** The item the focus is built from. */
    public final Material focusMaterial;

    /** Icon used in chat and menu headings. */
    public final String icon;

    // Text ramp, brightest to coldest.
    public final TextColor core;
    public final TextColor hot;
    public final TextColor mid;
    public final TextColor low;

    /** Glow behind lit text - the same hue as the ramp. */
    public final ShadowColor glow;

    // Particle tints.
    public final Color dustBright;
    public final Color dustDeep;

    Element(String title, String genitive, Material focusMaterial, String icon,
            TextColor core, TextColor hot, TextColor mid, TextColor low,
            ShadowColor glow, Color dustBright, Color dustDeep) {
        this.title = title;
        this.genitive = genitive;
        this.focusMaterial = focusMaterial;
        this.icon = icon;
        this.core = core;
        this.hot = hot;
        this.mid = mid;
        this.low = low;
        this.glow = glow;
        this.dustBright = dustBright;
        this.dustDeep = dustDeep;
    }

    /** Hex string for MiniMessage tags. */
    public String hex(TextColor colour) {
        return String.format("#%06X", colour.value());
    }
}
