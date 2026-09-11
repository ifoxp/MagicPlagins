package dev.magic.fire.ui;

import dev.magic.fire.ability.ChargeState;
import dev.magic.fire.element.Element;
import dev.magic.fire.school.School;
import dev.magic.fire.spell.SpellDef;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * All player-facing chrome: a mana bar and one action-bar line.
 *
 * <p>Nothing is ever drawn across the middle of the screen - the player is
 * aiming there. Colours come from the active {@link Element}, so a school swap
 * repaints everything with no extra bookkeeping.</p>
 *
 * <p>{@link ShadowColor} carries the look: per-component ARGB shadows mean a
 * bright glyph over a saturated translucent shadow of its own hue reads as
 * emitting light, rather than sitting on the flat black drop shadow.</p>
 */
public final class MagicHud {

    private static final TextColor DEAD = TextColor.color(0x4A3630);
    private static final TextColor ASH = TextColor.color(0x8A817A);
    private static final ShadowColor NO_GLOW = ShadowColor.shadowColor(0x00000000);
    private static final ShadowColor FAINT = ShadowColor.shadowColor(0x33200D08);

    private static final int METER_CELLS = 20;

    private final BossBar bar = BossBar.bossBar(
            Component.empty(), BossBar.MAX_PROGRESS,
            BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    private Element element = Element.FIRE;
    private String lastGesture = "";
    private long gestureAt = 0L;

    public void setElement(Element element) {
        this.element = element;
    }

    public void show(Player player) {
        player.showBossBar(bar);
    }

    public void hide(Player player) {
        player.hideBossBar(bar);
    }

    // ------------------------------------------------------------- mana bar

    public void updateMana(double fraction, boolean stanceActive, boolean exhausted) {
        float progress = (float) Math.clamp(fraction, 0.0D, 1.0D);
        bar.progress(progress);

        if (exhausted) {
            bar.color(BossBar.Color.WHITE);
            bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
            bar.name(Component.text()
                    .append(Component.text("✖ ", element.low).shadowColor(element.glow))
                    .append(Component.text("СИЛА ВИЧЕРПАНА", DEAD)
                            .decorate(TextDecoration.BOLD))
                    .build());
            return;
        }

        bar.removeFlag(BossBar.Flag.DARKEN_SCREEN);
        bar.color(progress > 0.5F ? BossBar.Color.YELLOW
                : progress > 0.25F ? BossBar.Color.RED : BossBar.Color.PURPLE);

        bar.name(Component.text()
                .append(stanceActive
                        ? Component.text("◆ ", element.core).shadowColor(element.glow)
                        : Component.text("◇ ", DEAD).shadowColor(NO_GLOW))
                .append(gradient(element.icon + " " + element.title.toUpperCase()))
                .append(Component.text("  ", ASH))
                .append(meter(progress))
                .append(Component.text("  " + Math.round(fraction * 1000.0D), element.hot)
                        .shadowColor(element.glow))
                .build());
    }

    private Component meter(float fraction) {
        int filled = Math.round(fraction * METER_CELLS);
        TextComponent.Builder out = Component.text();
        for (int i = 0; i < METER_CELLS; i++) {
            float t = (float) i / (METER_CELLS - 1);
            out.append(i < filled
                    ? Component.text('▊', TextColor.lerp(t, element.core, element.low))
                            .shadowColor(ShadowColor.lerp(t, element.glow, FAINT))
                    : Component.text('▊', DEAD).shadowColor(FAINT));
        }
        return out.build();
    }

    // ------------------------------------------------------------ status line

    /** One line: the channel while it runs, otherwise the pending gesture. */
    public void status(Player player, String gesture, ChargeState charge, School school) {
        if (charge.isChannelling()) {
            player.sendActionBar(channelLine(charge, school));
            return;
        }
        if (charge.isActive()) {
            player.sendActionBar(chargeLine(charge.power(), school));
            return;
        }

        if (!gesture.equals(lastGesture)) {
            lastGesture = gesture;
            gestureAt = System.currentTimeMillis();
        }
        float cool = Math.clamp((System.currentTimeMillis() - gestureAt) / 600.0F, 0F, 1F);

        player.sendActionBar(Component.text()
                .append(gradient("◈ " + element.title.toUpperCase() + " ◈"))
                .append(Component.text("  ·  ", DEAD))
                .append(gesture.isBlank()
                        ? Component.text("готовий", ASH).decorate(TextDecoration.ITALIC)
                        : Component.text(gesture,
                                        TextColor.lerp(cool, element.core, element.hot))
                                .decorate(TextDecoration.BOLD)
                                .shadowColor(ShadowColor.lerp(cool, element.glow, FAINT)))
                .build());
    }

    private Component chargeLine(double power, School school) {
        return Component.text()
                .append(Component.text("✹ ", element.core).shadowColor(element.glow))
                .append(cells('█', '░', 16, power))
                .append(Component.text("  " + school.boltName(power),
                                TextColor.lerp((float) power, element.hot, element.core))
                        .decorate(TextDecoration.BOLD).shadowColor(element.glow))
                .append(Component.text(String.format("  %.1f♥",
                        school.boltDamage(power) / 2.0D), ASH))
                .append(power >= 0.99D
                        ? Component.text("   ще → " + school.channelName(), element.mid)
                                .decorate(TextDecoration.ITALIC)
                        : Component.empty())
                .build();
    }

    private Component channelLine(ChargeState charge, School school) {
        double intensity = charge.intensity();
        return Component.text()
                .append(Component.text("✷ ", element.core).shadowColor(element.glow))
                .append(gradient(school.channelName()).decorate(TextDecoration.BOLD))
                .append(Component.text("  ", ASH))
                .append(cells('▰', '▱', 12, intensity))
                .append(Component.text(String.format("  %.1f♥/с",
                        school.channelDps(intensity) / 2.0D), ASH))
                .append(intensity < 0.9D
                        ? Component.text("   швидше", DEAD).decorate(TextDecoration.ITALIC)
                        : Component.empty())
                .build();
    }

    /** Filled/empty glyph bar, used by both the charge and channel readouts. */
    private Component cells(char on, char off, int count, double fraction) {
        int filled = (int) Math.round(Math.clamp(fraction, 0.0D, 1.0D) * count);
        TextComponent.Builder out = Component.text();
        for (int i = 0; i < count; i++) {
            float t = (float) i / Math.max(1, count - 1);
            out.append(i < filled
                    ? Component.text(on, TextColor.lerp(t, element.hot, element.core))
                            .shadowColor(element.glow)
                    : Component.text(off, DEAD).shadowColor(FAINT));
        }
        return out.build();
    }

    // ----------------------------------------------------------------- cues

    public void spellCast(Player player, SpellDef spell) {
        TextComponent.Builder line = Component.text()
                .append(Component.text("✦ ", element.core).shadowColor(element.glow))
                .append(gradient(spell.title().toUpperCase())
                        .decorate(TextDecoration.BOLD));

        if (spell.isOffensive()) {
            line.append(Component.text(
                    String.format("  %.1f♥", spell.hearts()), element.hot));
        }
        line.append(Component.text("  " + spell.mana() + " мани", DEAD));

        player.sendActionBar(line.build());
        player.playSound(cue(spell.isHeavy() ? "heavy" : "light"));
    }

    public void boltCast(Player player, School school, double power) {
        player.sendActionBar(Component.text()
                .append(Component.text("✹ ", element.core).shadowColor(element.glow))
                .append(Component.text(school.boltName(power), element.hot)
                        .decorate(TextDecoration.BOLD).shadowColor(element.glow))
                .append(Component.text(String.format("  %.1f♥",
                        school.boltDamage(power) / 2.0D), ASH))
                .build());
    }

    public void wallLaunched(Player player) {
        player.sendActionBar(Component.text()
                .append(Component.text("⇨ ", element.core).shadowColor(element.glow))
                .append(gradient("СТІНА ПІШЛА").decorate(TextDecoration.BOLD))
                .build());
        player.playSound(cue("heavy"));
    }

    public void denied(Player player, String reason) {
        player.sendActionBar(Component.text()
                .append(Component.text("✖ ", element.low).shadowColor(element.glow))
                .append(Component.text(reason, ASH))
                .build());
        player.playSound(cue("fail"));
    }

    /** Drawing the focus - the stance is the item, so this is just a cue. */
    public void stanceOpened(Player player) {
        player.sendActionBar(gradient("◈ " + element.title.toUpperCase() + " ◈")
                .decorate(TextDecoration.BOLD));
        player.playSound(cue("open"));
    }

    public void stanceClosed(Player player) {
        player.sendActionBar(Component.empty());
    }

    // ------------------------------------------------------------- internals

    /**
     * Per-character interpolation with a matching shadow ramp, so text looks lit
     * from within rather than merely coloured.
     */
    private Component gradient(String text) {
        TextComponent.Builder out = Component.text();
        int span = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            float t = (float) i / span;
            out.append(Component.text(text.charAt(i),
                            TextColor.lerp(t, element.core, element.mid))
                    .shadowColor(ShadowColor.lerp(t, element.glow, FAINT)));
        }
        return out.build();
    }

    /** School-appropriate sound for a named cue. */
    private Sound cue(String kind) {
        boolean fire = element == Element.FIRE;
        String key = switch (kind) {
            case "heavy" -> fire ? "minecraft:entity.blaze.shoot"
                    : "minecraft:entity.player.hurt_freeze";
            case "open" -> fire ? "minecraft:item.flintandsteel.use"
                    : "minecraft:block.glass.place";
            case "fail" -> fire ? "minecraft:block.fire.extinguish"
                    : "minecraft:block.glass.break";
            default -> fire ? "minecraft:block.fire.ambient"
                    : "minecraft:block.glass.place";
        };
        return Sound.sound().type(Key.key(key)).source(Sound.Source.PLAYER)
                .volume(0.8F).pitch(kind.equals("heavy") ? 0.7F : 1.3F).build();
    }
}
