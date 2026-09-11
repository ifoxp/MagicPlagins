package dev.magic.fire;

import dev.magic.fire.element.Element;
import dev.magic.fire.item.FocusItem;
import dev.magic.fire.mana.ManaPool;
import dev.magic.fire.school.FireSchool;
import dev.magic.fire.school.IceSchool;
import dev.magic.fire.school.FrostForm;
import dev.magic.fire.school.InfernoForm;
import dev.magic.fire.school.School;
import dev.magic.fire.school.WallManager;
import dev.magic.fire.spell.SpellDef;
import dev.magic.fire.ui.ElementMenu;
import dev.magic.fire.ui.MagicHud;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gesture-driven elemental magic for a vanilla client.
 *
 * <p>The plugin core knows nothing about individual spells. A {@link School}
 * owns its own {@link dev.magic.fire.spell.SpellBook}, and the listener routes
 * gestures to whichever school the player is holding - so adding a school means
 * writing one class, not editing this one.</p>
 */
public final class MagicPlugin extends JavaPlugin {

    private final Map<UUID, CasterState> states = new HashMap<>();
    private final Map<Element, School> schools = new EnumMap<>(Element.class);

    private Balance balance;
    private WallManager walls;
    private InfernoForm inferno;
    private FrostForm frost;

    @Override
    public void onEnable() {
        FocusItem.init(this);

        // Config first: the schools read their numbers from it as they build
        // their books, so it has to exist before they are constructed.
        balance = new Balance(this);
        applyBalance();

        walls = new WallManager(this);
        inferno = new InfernoForm(this);
        frost = new FrostForm(this);

        schools.put(Element.FIRE, new FireSchool(this));
        schools.put(Element.ICE, new IceSchool(this));

        ElementMenu menu = new ElementMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new MagicListener(this), this);

        // Paper plugins register commands in code, not in paper-plugin.yml.
        registerCommand("magic", "Вибрати стихію", List.of("mg"),
                new MagicCommand(this, menu));

        getLogger().info("Магія активна | " + schools.size()
                + " стихії | Paper 26.2 | Adventure 5");
    }

    @Override
    public void onDisable() {
        walls.clear();
        inferno.clear();
        frost.clear();
        for (Player player : getServer().getOnlinePlayers()) {
            CasterState state = states.get(player.getUniqueId());
            if (state != null) {
                state.hud.hide(player);
            }
        }
        states.clear();
    }

    // ---------------------------------------------------------------- lookups

    public CasterState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new CasterState());
    }

    public void forget(Player player) {
        walls.forget(player);
        inferno.exit(player, null);
        frost.exit(player, null);
        CasterState state = states.remove(player.getUniqueId());
        if (state != null) {
            state.hud.hide(player);
        }
    }

    public Balance balance() {
        return balance;
    }

    /**
     * Pushes config values into the places that cache them.
     *
     * <p>Most numbers are read on demand, so a reload reaches them for free.
     * These two are held statically for the hot path, so they need nudging.</p>
     */
    public void applyBalance() {
        dev.magic.fire.fx.Fx.setPierce(balance.armourPierce());
        ManaPool.configure(balance);
    }

    public WallManager walls() {
        return walls;
    }

    public InfernoForm inferno() {
        return inferno;
    }

    public FrostForm frost() {
        return frost;
    }

    /**
     * Whether the held school's form is currently active.
     *
     * <p>Each school has its own form class, but the listener only cares
     * whether one is up - so the lookup routes by held focus and the callers
     * stay school-agnostic.</p>
     */
    public boolean formActive(Player player) {
        return heldSchool(player).element() == Element.ICE
                ? frost.isActive(player)
                : inferno.isActive(player);
    }

    /** Enters the held school's form. */
    public boolean formEnter(Player player) {
        return heldSchool(player).element() == Element.ICE
                ? frost.enter(player)
                : inferno.enter(player);
    }

    /** Leaves whichever form the player is in. */
    public void formExit(Player player, String reason) {
        inferno.exit(player, reason);
        frost.exit(player, reason);
    }

    public School school(Element element) {
        return schools.get(element);
    }

    /** The school of the focus in the player's main hand. */
    public School heldSchool(Player player) {
        Element element = FocusItem.elementOf(player.getInventory().getItemInMainHand());
        return schools.get(element == null ? Element.FIRE : element);
    }

    // ---------------------------------------------------------------- casting

    /** Casts a gesture spell, gating on cooldown and mana. */
    public boolean cast(Player player, SpellDef spell) {
        CasterState state = state(player);

        // Cooldowns are per spell, so a dash never gates a meteor.
        if (state.onCooldown(spell.id())) {
            return false;
        }
        if (!state.mana.trySpend(spell.mana())) {
            state.hud.denied(player, state.mana.isExhausted()
                    ? "сила ще не відродилася"
                    : "недостатньо мани");
            return false;
        }

        spell.effect().cast(player);
        state.hud.spellCast(player, spell);
        state.setCooldown(spell.id(), spell.cooldownMs());
        // Casting throws the player into the air on purpose - never punish that.
        state.grantFallImmunity(spell.duration() > 0 ? spell.duration() + 60 : 60);
        return true;
    }

    /** Fires the charged bolt when sustained clicking stops. */
    public boolean bolt(Player player, double power) {
        CasterState state = state(player);
        School school = heldSchool(player);

        if (!state.mana.trySpend(school.boltMana(power))) {
            state.hud.denied(player, "недостатньо мани");
            return false;
        }
        school.bolt(player, power);
        state.hud.boltCast(player, school, power);
        state.grantFallImmunity(power > 0.5D ? 80 : 40);
        return true;
    }

    /**
     * Advances the sustained channel.
     *
     * @return false when the caster runs dry, so the channel can stop
     */
    public boolean channel(Player player, int tick, double intensity) {
        CasterState state = state(player);
        School school = heldSchool(player);

        // Billed on a beat rather than every tick.
        if (tick % 5 == 0 && !state.mana.trySpend(school.channelMana())) {
            state.hud.denied(player, "сила вичерпана");
            return false;
        }
        school.channel(player, tick, intensity);
        return true;
    }
}
