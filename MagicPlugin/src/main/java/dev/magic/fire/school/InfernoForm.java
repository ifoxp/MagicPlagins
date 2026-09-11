package dev.magic.fire.school;

import dev.magic.fire.MagicPlugin;
import dev.magic.fire.element.Element;
import dev.magic.fire.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The inferno form: a sustained transformation, not a spell.
 *
 * <p>Held right click for three seconds turns the caster into something else -
 * wings of flame, horns of fire, faster and lighter on their feet. Every other
 * spell still works, so the form is a stance within a stance: it is what you
 * enter when you have decided to either run someone down or get out.</p>
 *
 * <p>It drains mana continuously and will not release on its own; leaving early
 * takes a deliberate {@code Shift + S}. That asymmetry is the point - entering
 * is a commitment, and the way out costs you a gesture.</p>
 */
public final class InfernoForm {

    /** Held right click for this long enters the form. */
    public static final long CHARGE_MS = 3000L;

    private static final Color EMBER_CORE = Color.fromRGB(0xFF, 0xF3, 0xC4);
    private static final Color EMBER_HOT = Color.fromRGB(0xFF, 0xC2, 0x4B);
    private static final Color EMBER_MID = Color.fromRGB(0xFF, 0x7A, 0x2F);
    private static final Color VERDANT = Color.fromRGB(0x8A, 0xFF, 0x6A);

    private final MagicPlugin plugin;
    private final Map<UUID, Active> forms = new HashMap<>();

    /** Speed bonus, removed on exit so it can never persist across a relog. */
    private final org.bukkit.NamespacedKey speedKey;

    public InfernoForm(MagicPlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new org.bukkit.NamespacedKey(plugin, "inferno_speed");
    }

    public boolean isActive(Player player) {
        return forms.containsKey(player.getUniqueId());
    }

    /** Enters the form. Returns false if already in it, or out of mana. */
    public boolean enter(Player player) {
        if (isActive(player)) {
            return false;
        }
        if (plugin.state(player).mana.fraction()
                < plugin.balance().formMinimumMana(Element.FIRE, 0.25D)) {
            plugin.state(player).hud.denied(player, "замало сили для форми");
            return false;
        }

        World world = player.getWorld();
        Location at = player.getLocation();

        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2F, 1.1F);
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.6F, 0.6F);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 1,
                0.3D, 0.3D, 0.3D, 0);
        Fx.shockwave(plugin, world, at, 4.0D, Particle.COPPER_FIRE_FLAME, VERDANT);

        applyBuffs(player);

        Active active = new Active();
        forms.put(player.getUniqueId(), active);
        active.task = startTicker(player);
        return true;
    }

    /** Leaves the form - by the exit gesture, by running dry, or on quit. */
    public void exit(Player player, String reason) {
        Active active = forms.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        removeBuffs(player);

        if (player.isOnline()) {
            World world = player.getWorld();
            world.playSound(player.getLocation(),
                    Sound.BLOCK_FIRE_EXTINGUISH, 1.2F, 0.7F);
            world.spawnParticle(Particle.LARGE_SMOKE,
                    player.getLocation().add(0, 1, 0), 30, 0.5D, 0.8D, 0.5D, 0.03D);
            if (reason != null) {
                plugin.state(player).hud.denied(player, reason);
            }
        }
    }

    public void clear() {
        for (UUID id : Map.copyOf(forms).keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                exit(player, null);
            } else {
                forms.remove(id);
            }
        }
    }

    // ------------------------------------------------------------------ buffs

    /**
     * What the form actually grants: speed, height and fire immunity.
     *
     * <p>Speed and jump boost together are what make it read as flight without
     * granting flight - the player covers ground in long bounds. Slow falling
     * is left out on purpose: floating permanently makes it impossible to come
     * down on a target, and the double jump already grants it per leap.</p>
     */
    private void applyBuffs(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(speedKey) == null) {
            speed.addModifier(new AttributeModifier(speedKey,
                    plugin.balance().formSpeedBonus(Element.FIRE, 0.35D),
                    AttributeModifier.Operation.ADD_SCALAR));
        }

        int forever = Integer.MAX_VALUE;
        // Speed II on top of the attribute bonus - the form should feel like a
        // different movement mode, not a small buff.
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, forever, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST, forever,
                plugin.balance().formJumpLevel(Element.FIRE, 2),
                false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, forever, 0, false, false, false));
        // Deliberately no slow falling: a permanent float fights every attempt
        // to drop onto someone. The double jump grants it per leap instead,
        // which is where a soft landing is actually wanted.
    }

    private void removeBuffs(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            AttributeModifier existing = speed.getModifier(speedKey);
            if (existing != null) {
                speed.removeModifier(existing);
            }
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    // ---------------------------------------------------------------- visuals

    private BukkitRunnable startTicker(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    exit(player, null);
                    cancel();
                    return;
                }
                ticks++;

                // Billed on a beat rather than every tick.
                if (ticks % 5 == 0) {
                    if (!plugin.state(player).mana.trySpend(
                            (int) Math.ceil(plugin.balance()
                                    .formDrain(Element.FIRE, 22.0D) / 4.0D))) {
                        exit(player, "форма згасла");
                        cancel();
                        return;
                    }
                }

                drawWings(player, ticks);
                drawHorns(player, ticks);

                if (ticks % 12 == 0) {
                    player.getWorld().playSound(player.getLocation(),
                            Sound.ENTITY_BLAZE_AMBIENT, 0.4F, 1.4F);
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        return task;
    }

    /**
     * Wings of flame behind the caster.
     *
     * <p>Drawn from the caster's own facing so they always trail correctly, and
     * they beat - the span pulses, which sells them as wings rather than two
     * static fans of particles.</p>
     */
    private void drawWings(Player player, int ticks) {
        World world = player.getWorld();
        Location back = player.getLocation().add(0, 1.1D, 0);
        Vector forward = Fx.look(player);
        Vector side = Fx.right(forward);
        // Sit them behind the shoulders, not inside the model.
        Location root = back.add(forward.clone().multiply(-0.25D));

        // One beat per second or so.
        double beat = 0.75D + 0.25D * Math.sin(ticks * 0.22D);

        for (int wing = -1; wing <= 1; wing += 2) {
            for (int seg = 1; seg <= 5; seg++) {
                double reach = seg * 0.34D * beat;
                // Feathers sweep back and rise toward the tip.
                double lift = 0.12D * seg - 0.04D * seg * seg * 0.3D;
                Location p = root.clone()
                        .add(side.clone().multiply(reach * wing))
                        .add(forward.clone().multiply(-reach * 0.45D))
                        .add(0, lift, 0);

                // Green at the roots, orange at the tips - the same ramp the
                // ultimate uses, so the form reads as belonging to it.
                Particle flame = seg <= 2
                        ? Particle.COPPER_FIRE_FLAME
                        : Particle.FLAME;
                world.spawnParticle(flame, p, 1, 0.04D, 0.04D, 0.04D, 0.004D);

                if (ticks % 2 == 0 && seg >= 3) {
                    world.spawnParticle(Particle.DUST, p, 1, 0.05D, 0.05D, 0.05D, 0,
                            new Particle.DustOptions(
                                    seg == 5 ? EMBER_MID : EMBER_HOT, 0.6F));
                }
            }
        }
    }

    /** Two horns curving up from the head. */
    private void drawHorns(Player player, int ticks) {
        World world = player.getWorld();
        Location head = player.getEyeLocation().add(0, 0.35D, 0);
        Vector forward = Fx.look(player);
        Vector side = Fx.right(forward);

        for (int horn = -1; horn <= 1; horn += 2) {
            for (int seg = 0; seg < 4; seg++) {
                double t = seg / 3.0D;
                // Curving outward and back as they rise.
                Location p = head.clone()
                        .add(side.clone().multiply((0.16D + t * 0.14D) * horn))
                        .add(forward.clone().multiply(-t * 0.16D))
                        .add(0, t * 0.42D, 0);

                world.spawnParticle(seg == 3
                                ? Particle.COPPER_FIRE_FLAME
                                : Particle.SMALL_FLAME,
                        p, 1, 0.02D, 0.02D, 0.02D, 0.002D);
            }
        }

        // A faint crown, so the head reads as lit from above.
        if (ticks % 3 == 0) {
            Fx.ring(world, head, 0.3D, EMBER_CORE, 0.5F, 8, ticks * 0.2D);
        }
    }

    /** Per-player bookkeeping. */
    private static final class Active {
        private BukkitRunnable task;
    }
}
