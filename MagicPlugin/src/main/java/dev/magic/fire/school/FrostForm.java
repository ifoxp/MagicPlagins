package dev.magic.fire.school;

import dev.magic.fire.MagicPlugin;
import dev.magic.fire.element.Element;
import dev.magic.fire.fx.Fx;
import dev.magic.fire.fx.Model;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The frost form: ice's answer to the inferno.
 *
 * <p>Entered the same way - hold right click for three seconds - and left the
 * same way, with {@code Shift + S}. What it grants is the mirror image: where
 * the inferno makes you fast and aggressive, this makes you hard to kill and
 * hard to get away from. A crown of floating shards, an aura that chills
 * anything that comes near, resistance instead of speed.</p>
 *
 * <p>That contrast is the point. Fire's form is for closing distance; ice's is
 * for owning the ground you already stand on.</p>
 */
public final class FrostForm {

    /** Held right click for this long enters the form. */
    public static final long CHARGE_MS = 3000L;

    private static final Color FROST_CORE = Color.fromRGB(0xF2, 0xFE, 0xFF);
    private static final Color FROST_HOT = Color.fromRGB(0xA8, 0xEC, 0xFF);
    private static final Color FROST_LOW = Color.fromRGB(0x2A, 0x7F, 0xB8);

    /** How far the chilling aura reaches. */
    private static final double AURA_RADIUS = 4.0D;

    private final MagicPlugin plugin;
    private final Map<UUID, Active> forms = new HashMap<>();
    private final org.bukkit.NamespacedKey armourKey;

    public FrostForm(MagicPlugin plugin) {
        this.plugin = plugin;
        this.armourKey = new org.bukkit.NamespacedKey(plugin, "frost_armour");
    }

    public boolean isActive(Player player) {
        return forms.containsKey(player.getUniqueId());
    }

    public boolean enter(Player player) {
        if (isActive(player)) {
            return false;
        }
        if (plugin.state(player).mana.fraction()
                < plugin.balance().formMinimumMana(Element.ICE, 0.25D)) {
            plugin.state(player).hud.denied(player, "замало сили для форми");
            return false;
        }

        World world = player.getWorld();
        Location at = player.getLocation();

        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.1F, 1.9F);
        world.playSound(at, Sound.BLOCK_GLASS_PLACE, 1.6F, 0.5F);
        world.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 60,
                0.6D, 1.0D, 0.6D, 0.08D);
        Fx.shockwave(plugin, world, at, 4.0D, Particle.SNOWFLAKE, FROST_HOT);

        applyBuffs(player);

        Active active = new Active();
        buildCrown(player, active);
        forms.put(player.getUniqueId(), active);
        active.task = startTicker(player, active);
        return true;
    }

    public void exit(Player player, String reason) {
        Active active = forms.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        active.removeCrown();
        active.thaw();
        removeBuffs(player);

        if (player.isOnline()) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2F, 0.7F);
            world.spawnParticle(Particle.SNOWFLAKE,
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
                Active active = forms.remove(id);
                if (active != null) {
                    active.removeCrown();
                    active.thaw();
                }
            }
        }
    }

    // ------------------------------------------------------------------ buffs

    /**
     * Toughness rather than speed.
     *
     * <p>Resistance and extra armour where the inferno gives movement - a frost
     * mage in form is meant to be walked up to and regretted, not chased.</p>
     */
    private void applyBuffs(Player player) {
        AttributeInstance armour = player.getAttribute(Attribute.ARMOR);
        if (armour != null && armour.getModifier(armourKey) == null) {
            armour.addModifier(new AttributeModifier(armourKey, 8.0D,
                    AttributeModifier.Operation.ADD_NUMBER));
        }

        int forever = Integer.MAX_VALUE;
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, forever, 1, false, false, false));
        // Enough to keep up, not enough to chase - the opposite trade to fire.
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, forever, 0, false, false, false));
    }

    private void removeBuffs(Player player) {
        AttributeInstance armour = player.getAttribute(Attribute.ARMOR);
        if (armour != null) {
            AttributeModifier existing = armour.getModifier(armourKey);
            if (existing != null) {
                armour.removeModifier(existing);
            }
        }
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    // ---------------------------------------------------------------- visuals

    /** A crown of shards orbiting the head, built once and moved thereafter. */
    private void buildCrown(Player player, Active active) {
        for (int i = 0; i < 6; i++) {
            active.crown.add(Model.body(plugin, player.getEyeLocation(),
                    Model.piece(i % 2 == 0
                                    ? Material.PACKED_ICE.createBlockData()
                                    : Material.BLUE_ICE.createBlockData())
                            .size(0.22D, 0.42D, 0.22D)
                            .glow(i % 2 == 0 ? FROST_HOT : FROST_CORE),
                    4));
        }
    }

    private BukkitRunnable startTicker(Player player, Active active) {
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

                if (ticks % 5 == 0) {
                    if (!plugin.state(player).mana.trySpend(
                            (int) Math.ceil(plugin.balance()
                                    .formDrain(Element.ICE, 20.0D) / 4.0D))) {
                        exit(player, "форма розтанула");
                        cancel();
                        return;
                    }
                }

                drawCrown(player, active, ticks);
                drawAura(player, ticks);
                chillNearby(player, ticks);
                freezeUnderfoot(player, ticks);
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        return task;
    }

    /** Shards orbiting above the head, on the same beat trick as the shield. */
    private void drawCrown(Player player, Active active, int ticks) {
        final int step = 3;
        if (ticks % step != 0) {
            return;
        }
        Location head = player.getEyeLocation().add(0, 0.55D, 0);
        double rate = 0.1D;

        for (int i = 0; i < active.crown.size(); i++) {
            Model.Body shard = active.crown.get(i);
            if (!shard.isAlive()) {
                continue;
            }
            double angle = ticks * rate + Math.PI * 2 * i / active.crown.size();
            double ahead = angle + rate * step;

            shard.reanchor(head);
            double[] drift = shard.offsetTo(head);
            // Bobbing slightly, so the crown is not a rigid ring.
            double bob = 0.06D * Math.sin(ticks * 0.15D + i);
            shard.glideTo(drift[0] + Math.cos(ahead) * 0.55D,
                    drift[1] + bob,
                    drift[2] + Math.sin(ahead) * 0.55D,
                    ahead, 0.25D, step);
        }
    }

    /** A ground mist marking the aura's reach, so its edge is readable. */
    private void drawAura(Player player, int ticks) {
        World world = player.getWorld();
        Location feet = player.getLocation();

        if (ticks % 2 == 0) {
            Fx.ring(world, feet.clone().add(0, 0.1D, 0), AURA_RADIUS,
                    FROST_LOW, 0.9F, 20, ticks * 0.04D);
        }
        if (ticks % 4 == 0) {
            world.spawnParticle(Particle.SNOWFLAKE, feet.clone().add(0, 0.3D, 0),
                    3, AURA_RADIUS * 0.5D, 0.2D, AURA_RADIUS * 0.5D, 0.01D);
        }
        // Frost trailing from the feet, so movement leaves a mark.
        if (ticks % 3 == 0) {
            world.spawnParticle(Particle.ITEM_SNOWBALL, feet, 2,
                    0.25D, 0.05D, 0.25D, 0.02D);
        }
    }

    /**
     * Freezes water underfoot, the way Frost Walker boots do.
     *
     * <p>This used to be a right click, which spent a gesture on something that
     * reads better as a property of the mage than an action. A frost mage
     * should simply not sink - so it happens while the form is up, and only
     * under the feet.</p>
     *
     * <p>Uses {@link Material#FROSTED_ICE}, the same block the enchantment
     * leaves: it melts on its own, so walking across an ocean cannot
     * permanently reshape it.</p>
     */
    private void freezeUnderfoot(Player player, int ticks) {
        // Every few ticks is plenty - the crust only has to keep up with a
        // walking player, and scanning a disc every tick would be wasteful.
        if (ticks % 4 != 0) {
            return;
        }
        Location feet = player.getLocation();
        // Only while actually on or in the surface; no freezing the sea from
        // twenty blocks up.
        if (feet.getBlock().getType() != Material.WATER
                && feet.clone().subtract(0, 1, 0).getBlock().getType() != Material.WATER) {
            return;
        }

        World world = feet.getWorld();
        int frozen = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // A rough disc, so the crust is round rather than square.
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                Block candidate = feet.clone().add(dx, -1, dz).getBlock();
                if (candidate.getType() != Material.WATER) {
                    continue;
                }
                // Only freeze the surface - a solid block above means this is
                // water under something, not water being walked on.
                if (candidate.getRelative(0, 1, 0).getType().isSolid()) {
                    continue;
                }
                candidate.setType(Material.FROSTED_ICE);
                // Remembered so the form can thaw its own crust on exit.
                // Vanilla only melts frosted ice through the Frost Walker tick,
                // which never runs for a player without the boots.
                Active active = forms.get(player.getUniqueId());
                if (active != null) {
                    active.frozen.add(candidate.getLocation());
                }
                frozen++;
            }
        }

        if (frozen > 0) {
            world.spawnParticle(Particle.SNOWFLAKE, feet, 6,
                    1.2D, 0.1D, 1.2D, 0.02D);
            if (ticks % 16 == 0) {
                world.playSound(feet, Sound.BLOCK_GLASS_PLACE, 0.5F, 1.5F);
            }
        }
    }

    /**
     * The aura itself: everything in range is slowed, continuously.
     *
     * <p>No damage - the form is not a damage aura. It removes the option of
     * walking away, which is what ice trades its impact for.</p>
     */
    private void chillNearby(Player player, int ticks) {
        if (ticks % 10 != 0) {
            return;
        }
        Fx.areaDamage(player, player.getLocation(), AURA_RADIUS, 0,
                (target, falloff) -> {
                    IceSchool.chill(target, 40, falloff > 0.6D ? 2 : 1);
                    IceSchool.freeze(target, 15);
                });
    }

    /** Per-player bookkeeping: the crown it owns, and the ice it laid. */
    private static final class Active {
        private BukkitRunnable task;
        private final java.util.List<Model.Body> crown = new java.util.ArrayList<>();

        /**
         * Blocks this form turned to ice.
         *
         * <p>A set, so crossing the same tile twice does not queue it twice,
         * and bounded in practice by how far a player walks in one form.</p>
         */
        private final java.util.Set<Location> frozen = new java.util.LinkedHashSet<>();

        void removeCrown() {
            crown.forEach(Model.Body::remove);
            crown.clear();
        }

        /** Puts the water back, so the form never permanently reshapes a lake. */
        void thaw() {
            for (Location at : frozen) {
                if (at.getBlock().getType() == Material.FROSTED_ICE) {
                    at.getBlock().setType(Material.WATER);
                }
            }
            frozen.clear();
        }
    }
}
