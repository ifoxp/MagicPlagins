package dev.magic.fire.item;

import dev.magic.fire.element.Element;
import dev.magic.fire.school.School;
import dev.magic.fire.spell.SpellDef;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The focus item, one per school.
 *
 * <p>Right-clicking it toggles the cast stance. The school is stored in the
 * item's {@link org.bukkit.persistence.PersistentDataContainer}, so the plugin
 * reads it from whatever the player is holding rather than tracking a separate
 * selection - swap items and the magic swaps with it.</p>
 *
 * <p>The tooltip is generated from the school's spell book, so it can never
 * disagree with what the spells actually do.</p>
 */
public final class FocusItem {

    private static final TextColor ASH = TextColor.color(0x7A736D);
    private static final TextColor DAMAGE = TextColor.color(0xC46A6A);

    private static NamespacedKey markerKey;
    private static NamespacedKey elementKey;

    private FocusItem() {
    }

    public static void init(Plugin plugin) {
        markerKey = new NamespacedKey(plugin, "magic_focus");
        elementKey = new NamespacedKey(plugin, "magic_element");
    }

    public static ItemStack create(School school) {
        Element element = school.element();
        ItemStack stack = new ItemStack(element.focusMaterial);

        stack.setData(DataComponentTypes.ITEM_NAME, Component.text()
                .append(Component.text(element.icon + " ", element.core)
                        .shadowColor(element.glow))
                .append(Component.text("Фокус " + element.genitive, element.hot)
                        .decorate(TextDecoration.BOLD).shadowColor(element.glow))
                .decoration(TextDecoration.ITALIC, false)
                .build());

        stack.setData(DataComponentTypes.LORE, ItemLore.lore()
                .addLines(tooltip(school))
                .build());

        // Hide the vanilla attribute block - the lore above is the whole story.
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay()
                        .hideTooltip(false)
                        .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        .build());

        // Neither focus material has a use action, so the vanilla client does
        // not reliably send a use packet when right-clicking empty air - which
        // is why RMB gestures once worked only while facing a block. A
        // consumable with an effectively zero duration makes the client always
        // send it, while being far too short to start a hold animation.
        stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(0.01F)
                .animation(ItemUseAnimation.NONE)
                .hasConsumeParticles(false)
                .sound(Key.key("minecraft:intentionally_empty"))
                .build());

        stack.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        stack.editPersistentDataContainer(pdc -> {
            pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(elementKey, PersistentDataType.STRING, element.name());
        });

        return stack;
    }

    // -------------------------------------------------------------- tooltip

    /**
     * Built from the spell book, so the item can never list a stale spell.
     *
     * <p>Every line is tinted from the school's own palette, the same ramp the
     * HUD uses - so a glance at the tooltip tells you which school you are
     * holding before you have read a word of it.</p>
     */
    private static List<Component> tooltip(School school) {
        Element element = school.element();
        List<Component> lore = new ArrayList<>();

        lore.add(head(element, "тримай у руці — магія активна"));
        lore.add(passive(element, school.passiveName()));
        lore.add(Component.empty());

        lore.add(section(element, "ЛКМ — основна атака"));
        lore.add(row(element, "1 клік", school.boltName(0.0D),
                school.boltDamage(0.0D) / 2.0D));
        lore.add(row(element, "заклікуй", school.boltName(0.3D),
                school.boltDamage(0.3D) / 2.0D));
        lore.add(row(element, "довше", school.boltName(0.6D),
                school.boltDamage(0.6D) / 2.0D));
        lore.add(row(element, "максимум", school.boltName(1.0D),
                school.boltDamage(1.0D) / 2.0D));
        lore.add(Component.empty());

        lore.add(section(element, "SHIFT + ЛКМ"));
        lore.add(row(element, "заклікуй", school.channelName(),
                school.channelDps(1.0D) / 2.0D));
        lore.add(note(element, "чим довше без пауз, тим концентрованіше"));
        lore.add(Component.empty());

        lore.add(section(element, "ЖЕСТИ"));
        for (SpellDef spell : school.book().listed()) {
            lore.add(row(element, spell.gesture(), spell.title(), spell.hearts()));
        }
        lore.add(Component.empty());

        lore.add(section(element, "ФОРМА"));
        lore.add(row(element, "ПКМ 3 сек", element == Element.FIRE
                ? "інферно — швидкість, стрибок"
                : "мороз — броня, аура холоду", 0.0D));
        if (element == Element.ICE) {
            lore.add(note(element, "вода мерзне під ногами"));
        }
        // Spelled out as the double tap it actually is: a single S would be
        // read as a backstep, and the exit has to be unambiguous.
        lore.add(row(element, "Shift + S S", "вийти з форми", 0.0D));
        lore.add(Component.empty());

        lore.add(section(element, "ПОЗА БОЄМ"));
        lore.add(row(element, "Shift + ПКМ", element == Element.FIRE
                ? "підпалити, розтопити"
                : "згасити лаву, вогонь", 0.0D));
        lore.add(Component.empty());

        lore.add(note(element, "стихія " + element.genitive + " · стиль рук"));
        return lore;
    }

    /** Opening line, in the school's brightest tone. */
    private static Component head(Element element, String text) {
        return Component.text(text, element.core)
                .decoration(TextDecoration.ITALIC, false)
                .shadowColor(element.glow);
    }

    /** The always-on perk, marked so it reads as different from a spell. */
    private static Component passive(Element element, String text) {
        return Component.text()
                .append(Component.text("◆ ", element.hot).shadowColor(element.glow))
                .append(Component.text(text, element.mid))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static Component section(Element element, String text) {
        return Component.text("▰ " + text, element.low)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component note(Element element, String text) {
        return Component.text(text, element.low).decorate(TextDecoration.ITALIC);
    }

    private static Component row(Element element, String gesture,
                                 String name, double hearts) {
        Component line = Component.text()
                .append(Component.text(gesture, element.hot)
                        .shadowColor(element.glow))
                .append(Component.text(" — " + name, ASH))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        return hearts > 0.0D
                ? line.append(Component.text(String.format("  %.1f♥", hearts), DAMAGE)
                        .decoration(TextDecoration.ITALIC, false))
                : line;
    }

    // ------------------------------------------------------------- identity

    /** Identified by PDC, not by model data - a player cannot fake this. */
    public static boolean isFocus(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getPersistentDataContainer()
                        .has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Which school this focus belongs to, or null if it is not a focus.
     *
     * <p>Read from the item rather than from player state, so putting one focus
     * away and drawing another switches schools with no extra bookkeeping.</p>
     */
    public static Element elementOf(ItemStack stack) {
        if (!isFocus(stack)) {
            return null;
        }
        String name = stack.getPersistentDataContainer()
                .get(elementKey, PersistentDataType.STRING);
        if (name == null) {
            return Element.FIRE;
        }
        try {
            return Element.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Element.FIRE;
        }
    }

    /** Unused, kept so NamedTextColor stays imported for future rows. */
    static TextColor white() {
        return NamedTextColor.WHITE;
    }
}
