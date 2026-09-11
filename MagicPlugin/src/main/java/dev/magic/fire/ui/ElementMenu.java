package dev.magic.fire.ui;

import dev.magic.fire.element.Element;
import dev.magic.fire.item.FocusItem;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The {@code /magic} school picker.
 *
 * <p>A chest inventory rather than a chat list: schools are things you pick up,
 * and a row of icons with hover detail reads better than twenty lines of chat.
 * Clicking one hands over that school's focus.</p>
 */
public final class ElementMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int SIZE = 27;

    /** Where the icons sit - spread across the middle row. */
    private static final int[] SLOTS = {11, 13, 15, 12, 14};

    private static final TextColor ASH = TextColor.color(0x8A817A);
    private static final TextColor DEAD = TextColor.color(0x4A3630);

    private final dev.magic.fire.MagicPlugin plugin;

    public ElementMenu(dev.magic.fire.MagicPlugin plugin) {
        this.plugin = plugin;
    }

    /** Also the marker that a click came from our menu. */
    private final Component title = MM.deserialize(
            "<gradient:#FFC24B:#A8ECFF><b>◈ ВИБІР СТИХІЇ ◈</b></gradient>");

    public void open(Player player) {
        Inventory menu = Bukkit.createInventory(null, SIZE, title);

        // Dark glass surround, so the icons read as the only live part.
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < SIZE; i++) {
            menu.setItem(i, filler);
        }

        Element[] elements = Element.values();
        for (int i = 0; i < elements.length && i < SLOTS.length; i++) {
            menu.setItem(SLOTS[i], icon(elements[i]));
        }

        player.openInventory(menu);
        player.playSound(sound("minecraft:block.enchantment_table.use", 0.7F, 1.3F));
    }

    /** One school icon, with a summary on hover. */
    private ItemStack icon(Element element) {
        ItemStack stack = new ItemStack(element.focusMaterial);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text()
                .append(Component.text(element.icon + " ", element.core)
                        .shadowColor(element.glow))
                .append(Component.text(element.title.toUpperCase(), element.hot)
                        .decorate(TextDecoration.BOLD)
                        .shadowColor(element.glow))
                .decoration(TextDecoration.ITALIC, false)
                .build());

        meta.lore(loreFor(element));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Summary built from the school itself, so it cannot go stale. */
    private List<Component> loreFor(Element element) {
        dev.magic.fire.school.School school = plugin.school(element);
        List<Component> lore = new java.util.ArrayList<>();

        lore.add(plain(element == Element.FIRE
                ? "Урон, горіння, підпал"
                : "Контроль, сповільнення, лід"));
        lore.add(Component.empty());
        lore.add(bullet(element, school.book().listed().size() + " жестів"));
        lore.add(bullet(element, "Shift+ЛКМ → " + school.channelName()));
        lore.add(bullet(element, element == Element.FIRE
                ? "Підпалює запал кріперів"
                : "Морозить воду і лаву"));
        lore.add(Component.empty());
        lore.add(italic(element == Element.FIRE
                ? "агресивна, пряма"
                : "сповільнює, тримає на місці"));
        lore.add(Component.empty());
        lore.add(pick(element));
        return lore;
    }

    private static Component plain(String text) {
        return Component.text(text, ASH).decoration(TextDecoration.ITALIC, false);
    }

    private static Component italic(String text) {
        return Component.text(text, DEAD).decorate(TextDecoration.ITALIC);
    }

    private static Component bullet(Element element, String text) {
        return Component.text()
                .append(Component.text("• ", element.mid))
                .append(Component.text(text, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static Component pick(Element element) {
        return Component.text("▸ клікни щоб узяти", element.hot)
                .decoration(TextDecoration.ITALIC, false)
                .shadowColor(element.glow);
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!title.equals(event.getView().title())) {
            return;
        }
        // Nothing in this menu may be taken or moved.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        for (Element element : Element.values()) {
            if (clicked.getType() == element.focusMaterial) {
                player.closeInventory();
                give(player, plugin.school(element));
                return;
            }
        }
    }

    private static void give(Player player, dev.magic.fire.school.School school) {
        Element element = school.element();
        player.getInventory().addItem(FocusItem.create(school));

        player.sendMessage(Component.text()
                .append(Component.text("✦ ", element.core).shadowColor(element.glow))
                .append(Component.text("Фокус ", ASH))
                .append(Component.text(element.genitive, element.hot)
                        .decorate(TextDecoration.BOLD)
                        .shadowColor(element.glow))
                .append(Component.text(" видано", ASH))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("  ПКМ", element.hot).shadowColor(element.glow))
                .append(Component.text(" — стійка,  ", DEAD))
                .append(Component.text("F", element.hot).shadowColor(element.glow))
                .append(Component.text(" — вихід.  Жести — у підказці предмета.", DEAD))
                .build());

        player.playSound(sound(element == Element.FIRE
                ? "minecraft:item.flintandsteel.use"
                : "minecraft:block.glass.place",
                0.8F, element == Element.FIRE ? 1.1F : 1.4F));
    }

    private static Sound sound(String key, float volume, float pitch) {
        return Sound.sound()
                .type(Key.key(key))
                .source(Sound.Source.PLAYER)
                .volume(volume)
                .pitch(pitch)
                .build();
    }
}
