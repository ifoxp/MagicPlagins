package dev.magic.fire;

import dev.magic.fire.element.Element;
import dev.magic.fire.item.FocusItem;
import dev.magic.fire.school.School;
import dev.magic.fire.spell.SpellDef;
import dev.magic.fire.ui.ElementMenu;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /magic} opens the school picker; {@code /magic help} prints the
 * gesture table for whatever focus is held.
 *
 * <p>The table is generated from the school's own spell book, so a newly
 * declared spell appears here with no edit to this class.</p>
 */
public final class MagicCommand implements BasicCommand {

    private static final TextColor DEAD = TextColor.color(0x4A3630);
    private static final TextColor ASH = TextColor.color(0x8A817A);
    private static final TextColor DAMAGE = TextColor.color(0xC46A6A);

    private final MagicPlugin plugin;
    private final ElementMenu menu;

    public MagicCommand(MagicPlugin plugin, ElementMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(
                    Component.text("Тільки для гравців.", NamedTextColor.RED));
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("magic.reload")) {
                player.sendMessage(Component.text("Немає доступу.", NamedTextColor.RED));
                return;
            }
            // Re-read config.yml and push the values that are cached outside it.
            plugin.balance().reload();
            plugin.applyBalance();
            player.sendMessage(Component.text()
                    .append(Component.text("✦ ", ASH))
                    .append(Component.text("Баланс перезавантажено", NamedTextColor.WHITE))
                    .append(Component.text(String.format("   пробивання броні %.0f%%",
                            plugin.balance().armourPierce() * 100), DEAD))
                    .build());
            return;
        }
        // Choosing a school is the first decision; the focus carries its own
        // gesture list from there.
        menu.open(player);
    }

    @Override
    public List<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? List.of("help", "reload") : List.of();
    }

    private void sendHelp(Player player) {
        Element held = FocusItem.elementOf(player.getInventory().getItemInMainHand());
        School school = plugin.school(held == null ? Element.FIRE : held);
        Element element = school.element();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text()
                .append(Component.text("  " + element.icon + " ", element.core)
                        .shadowColor(element.glow))
                .append(Component.text("МАГІЯ " + element.genitive.toUpperCase(),
                                element.hot)
                        .decorate(TextDecoration.BOLD).shadowColor(element.glow))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("  ◆ ", element.hot).shadowColor(element.glow))
                .append(Component.text("тримай паличку — магія активна", element.mid))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("  ◆ ", element.hot).shadowColor(element.glow))
                .append(Component.text(school.passiveName(), element.mid))
                .build());
        player.sendMessage(Component.empty());

        // Neither the volley nor the channel is a gesture pattern, so both are
        // listed by hand - and they are separate buttons, not one ladder.
        player.sendMessage(section("ЛКМ — основна атака"));
        player.sendMessage(row(element, "1 клік",
                school.boltName(0.0D), school.boltMana(0.0D),
                school.boltDamage(0.0D) / 2.0D));
        player.sendMessage(row(element, "заклікуй",
                school.boltName(0.6D), school.boltMana(0.6D),
                school.boltDamage(0.6D) / 2.0D));
        player.sendMessage(row(element, "максимум",
                school.boltName(1.0D), school.boltMana(1.0D),
                school.boltDamage(1.0D) / 2.0D));
        player.sendMessage(Component.empty());

        player.sendMessage(section("SHIFT + ЛКМ"));
        player.sendMessage(row(element, "заклікуй",
                school.channelName(), school.channelMana(),
                school.channelDps(1.0D) / 2.0D));
        player.sendMessage(Component.empty());

        player.sendMessage(section("ЖЕСТИ"));
        for (SpellDef spell : school.book().listed()) {
            player.sendMessage(row(element, spell.gesture(), spell.title(),
                    spell.mana(), spell.hearts()));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(section("ФОРМА"));
        player.sendMessage(row(element, "ПКМ 3 сек", element == Element.FIRE
                ? "інферно — швидкість, стрибок"
                : "мороз — броня, аура холоду", 0, 0.0D));
        player.sendMessage(row(element, "Shift + S S", "вийти з форми", 0, 0.0D));

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Shift + ПКМ по блоку — "
                        + (element == Element.FIRE
                                ? "підпалити, розтопити"
                                : "згасити лаву, вогонь"), element.low)
                .decorate(TextDecoration.ITALIC));
        player.sendMessage(Component.empty());
    }

    private Component section(String text) {
        return Component.text()
                .append(Component.text("  ▰ ", DEAD))
                .append(Component.text(text, ASH).decorate(TextDecoration.BOLD))
                .build();
    }

    private static Component row(Element element, String gesture, String name,
                                 int mana, double hearts) {
        Component line = Component.text()
                .append(Component.text("   " + gesture, element.hot)
                        .shadowColor(element.glow))
                .append(Component.text("  →  ", DEAD))
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(mana > 0
                        ? Component.text("  " + mana, DEAD)
                        : Component.empty())
                .build();

        return hearts > 0.0D
                ? line.append(Component.text(String.format("  %.1f♥", hearts), DAMAGE))
                : line;
    }
}
