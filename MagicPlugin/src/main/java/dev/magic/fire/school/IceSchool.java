package dev.magic.fire.school;

import dev.magic.fire.MagicPlugin;
import dev.magic.fire.combo.InputType;
import dev.magic.fire.element.Element;
import dev.magic.fire.fx.Fx;
import dev.magic.fire.fx.Model;
import dev.magic.fire.spell.SpellBook;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Ice: control rather than damage.
 *
 * <p>Every ice spell hits softer than its fire counterpart but takes the
 * target's tempo - slowness, mining fatigue, and for the heavy spells the
 * vanilla freeze effect, which brings the shiver and blue screen vignette for
 * free.</p>
 *
 * <p>Ice's utility is the opposite of fire's: it freezes water into the same
 * frosted ice a Frost Walker boot leaves, and turns loose water into a surface
 * you can walk on.</p>
 */
public final class IceSchool implements School {

    private static final Color FROST_CORE = Color.fromRGB(0xF2, 0xFE, 0xFF);
    private static final Color FROST_HOT = Color.fromRGB(0xA8, 0xEC, 0xFF);
    private static final Color FROST_MID = Color.fromRGB(0x5F, 0xC8, 0xF0);
    private static final Color FROST_LOW = Color.fromRGB(0x2A, 0x7F, 0xB8);

    private final MagicPlugin plugin;
    private final SpellBook book = new SpellBook(Element.ICE);

    public IceSchool(MagicPlugin plugin) {
        this.plugin = plugin;
        register();
    }

    @Override
    public SpellBook book() {
        return book;
    }

    @Override
    public Element element() {
        return Element.ICE;
    }

    /**
     * Ice's gesture table.
     *
     * <p>Its own book, so ice keeps the gestures that suit it - the aimed spike
     * line on look-down, the glacier on look-up - without having to match what
     * fire put there.</p>
     */
    /** Shorthand: this school's config lookups, keyed by spell id. */
    private int mana(String id, int fallback) {
        return plugin.balance().mana(Element.ICE, id, fallback);
    }

    private long cool(String id, long fallback) {
        return plugin.balance().cooldown(Element.ICE, id, fallback);
    }

    private double dmg(String id, double fallback) {
        return plugin.balance().damage(Element.ICE, id, fallback);
    }

    private double reach(String id, double fallback) {
        return plugin.balance().range(Element.ICE, id, fallback);
    }

    private int lasts(String id, int fallback) {
        return plugin.balance().duration(Element.ICE, id, fallback);
    }

    private void register() {
        // Mobility - flatter than fire's, it slides.
        book.add("slide", "Ковзання", mana("slide", 12), cool("slide", 600), 0, 0, 0,
                p -> slide(p, Fx.look(p).multiply(1.6D)),
                InputType.TAP_FORWARD);
        book.add("backslide", "Відступ", mana("backslide", 10), cool("backslide", 600), 0, 0, 0,
                p -> slide(p, Fx.look(p).multiply(-1.3D)),
                InputType.TAP_BACKWARD);
        book.add("dodge_left", "Ухил уліво", mana("dodge", 10), cool("dodge", 600), 0, 0, 0,
                p -> slide(p, Fx.right(Fx.look(p)).multiply(-1.45D)),
                InputType.TAP_LEFT);
        book.add("dodge_right", "Ухил управо", mana("dodge", 10), cool("dodge", 600), 0, 0, 0,
                p -> slide(p, Fx.right(Fx.look(p)).multiply(1.45D)),
                InputType.TAP_RIGHT);
        book.add("updraft", "Крижана ковзанка", mana("updraft", 16), cool("updraft", 800), 0, 0, 0,
                this::updraft,
                InputType.TAP_JUMP);

        // Ice-only: a lane of ice driven forward. On RMB rather than LMB so it
        // never competes with the basic attack for the same button.
        book.add("rimeline", "Крижаний шлях", mana("rimeline", 22), cool("rimeline", 1500),
                dmg("rimeline", 4.0D), reach("rimeline", 1.8D), lasts("rimeline", 120),
                this::rimeline,
                InputType.TAP_FORWARD, InputType.RIGHT_CLICK);

        // Ice-only: encase the nearest target in a pillar of ice. Moved off
        // Shift+LMB, which the channel now owns exclusively.
        book.add("tomb", "Крижана домовина", mana("tomb", 38), cool("tomb", 3500),
                dmg("tomb", 6.0D), reach("tomb", 6.0D), lasts("tomb", 80),
                this::tomb,
                InputType.SWAP_HAND, InputType.RIGHT_CLICK);

        // Control.
        book.add("wall", "Крижана стіна", mana("wall", 30), cool("wall", 2500),
                dmg("wall", 2.0D), reach("wall", 3.2D), lasts("wall", 200),
                p -> plugin.walls().place(p, this),
                InputType.SNEAK_DOWN, InputType.RIGHT_CLICK);

        // Ice-only: an inverted glacier dropped from the sky.
        book.add("glacier", "Глетчер", mana("glacier", 68), cool("glacier", 6500),
                dmg("glacier", 17.0D), reach("glacier", 6.5D), 0,
                this::glacier,
                InputType.LOOK_UP, InputType.RIGHT_CLICK);

        // Ice-only: a line of spikes erupting away from the caster.
        book.add("spikes", "Крижані шипи", mana("spikes", 30), cool("spikes", 2200),
                dmg("spikes", 8.0D), reach("spikes", 7.0D), lasts("spikes", 120),
                this::spikes,
                InputType.LOOK_DOWN, InputType.RIGHT_CLICK);

        book.add("aegis", "Крижаний обертень", mana("aegis", 32), cool("aegis", 5000),
                dmg("aegis", 2.0D), reach("aegis", 2.2D), lasts("aegis", 160),
                this::aegis,
                InputType.DROP, InputType.DROP);
        book.add("zero", "Абсолютний нуль", mana("zero", 88), cool("zero", 9000),
                dmg("zero", 3.0D), reach("zero", 7.0D), lasts("zero", 120),
                this::absoluteZero,
                InputType.DROP, InputType.RIGHT_CLICK, InputType.RIGHT_CLICK);
    }

    // ------------------------------------------------------------- mobility

    private void slide(Player player, Vector push) {
        Location origin = player.getLocation();
        World world = origin.getWorld();

        // Flat and fast - a slide, not a leap.
        player.setVelocity(push.clone().setY(0.16D));

        world.playSound(origin, Sound.BLOCK_GLASS_BREAK, 0.8F, 1.7F);
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.5F);

        Vector back = push.clone().normalize().multiply(-1);
        for (int i = 0; i < 22; i++) {
            Location p = origin.clone()
                    .add(back.clone().multiply(i / 22.0D * 1.8D))
                    .add(0, 0.15D, 0);
            world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0.15D, 0.05D, 0.15D, 0.01D);
            world.spawnParticle(Particle.ITEM_SNOWBALL, p, 1, 0.1D, 0.05D, 0.1D, 0.02D);
        }
        Fx.trail(plugin, player, 12, Particle.SNOWFLAKE, 3, 0.14D);
    }

    private void updraft(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();

        player.setVelocity(player.getVelocity().setY(0.95D));
        FireSchool.softLanding(player);

        world.playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.0F, 1.5F);
        world.playSound(origin, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7F, 1.4F);

        Fx.ring(world, origin.clone().add(0, 0.15D, 0), 0.9D, Particle.SNOWFLAKE, 30);
        world.spawnParticle(Particle.ITEM_SNOWBALL, origin, 20, 0.4D, 0.1D, 0.4D, 0.06D);
        Fx.trail(plugin, player, 16, Particle.SNOWFLAKE, 5, 0.22D);
    }

    // -------------------------------------------------------------- offence

    /**
     * W W then RMB - a sheet of ice races ahead, freezing what it crosses.
     *
     * <p>Moved off LMB so it never competes with the basic attack, and turned
     * around: it used to lay patches <em>behind</em> the caster, which meant
     * casting it did nothing to whoever you were facing. Now it runs forward
     * like fire's ram - a lane of ice you push into someone rather than a trail
     * you leave when fleeing.</p>
     */
    private void rimeline(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        Vector forward = Fx.look(player);

        world.playSound(origin, Sound.BLOCK_POWDER_SNOW_PLACE, 1.3F, 0.7F);
        world.playSound(origin, Sound.BLOCK_GLASS_PLACE, 1.1F, 0.9F);

        // A short forward slide, so the cast feels driven.
        player.setVelocity(forward.clone().multiply(0.55D).setY(0.1D));

        final int steps = 9;

        for (int i = 0; i < steps; i++) {
            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }
                    Location spot = Fx.ground(origin.clone()
                            .add(forward.clone().multiply(1.4D + index * 1.1D))
                            .add(0, 1, 0));

                    // A slab of ice underfoot, wider than the old patch so the
                    // lane is actually something you have to step around.
                    Model.group(plugin, spot)
                            .with(Model.piece(Material.ICE.createBlockData())
                                    .at(0, 0, 0).size(1.8D, 0.14D, 1.8D)
                                    .glow(FROST_HOT))
                            .grow(2)
                            .fadeAfter(lasts("rimeline", 120), 8);

                    world.spawnParticle(Particle.SNOWFLAKE,
                            spot.clone().add(0, 0.3D, 0), 8, 0.6D, 0.15D, 0.6D, 0.02D);
                    world.spawnParticle(Particle.ITEM_SNOWBALL,
                            spot.clone().add(0, 0.2D, 0), 4, 0.5D, 0.1D, 0.5D, 0.03D);

                    if (index % 3 == 0) {
                        world.playSound(spot, Sound.BLOCK_GLASS_PLACE, 0.8F, 1.2D > 0 ? 1.3F : 1.3F);
                    }

                    Fx.areaDamage(player, spot.clone().add(0, 0.6D, 0),
                            reach("rimeline", 1.8D), dmg("rimeline", 4.0D),
                            (target, falloff) -> {
                                chill(target, 110, 2);
                                freeze(target, 30);
                            });
                }
            }.runTaskLater(plugin, index * 2L);
        }
    }

    /**
     * Shift then LMB - encases the nearest target in a block of ice.
     *
     * <p>Ice's signature: no damage worth speaking of, but the target simply
     * stops for four seconds.</p>
     */
    private void tomb(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();

        LivingEntity victim = null;
        double best = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity
                : world.getNearbyEntities(eye, 6.0D, 4.0D, 6.0D)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            // Prefer whatever is closest to the crosshair, not just closest.
            Vector to = living.getLocation().add(0, 0.9D, 0)
                    .toVector().subtract(eye.toVector());
            double aim = to.clone().normalize().dot(eye.getDirection());
            if (aim < 0.5D) {
                continue;
            }
            double score = to.length() / Math.max(0.6D, aim);
            if (score < best) {
                best = score;
                victim = living;
            }
        }

        if (victim == null) {
            world.playSound(eye, Sound.BLOCK_GLASS_BREAK, 0.6F, 1.9F);
            return;
        }

        Location at = victim.getLocation();
        world.playSound(at, Sound.BLOCK_GLASS_PLACE, 1.5F, 0.5F);
        world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2F, 0.8F);

        // A real block of ice around them, built from displays.
        Model.group(plugin, at)
                .with(Model.piece(Material.ICE.createBlockData())
                        .at(0, 0, 0).size(1.5D, 2.4D, 1.5D).glow(FROST_HOT))
                .with(Model.piece(Material.PACKED_ICE.createBlockData())
                        .at(0, 0, 0).size(1.1D, 2.6D, 1.1D).spin(0.7D))
                .grow(4)
                .fadeAfter(80, 10);

        Fx.hit(player, victim, dmg("tomb", 6.0D));
        freeze(victim, 80);
        root(victim, 80);
        world.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1.0D, 0),
                40, 0.6D, 1.0D, 0.6D, 0.05D);
    }

    /**
     * Look down then RMB - six spikes erupt away from the caster.
     *
     * <p>Built to the same rhythm as fire's pillar line: one burst per step,
     * evenly spaced, marching outward. The old version threw two ragged shards
     * per step with random lean and height, which read as debris rather than a
     * deliberate wall - the regular cadence is what makes a line of spikes look
     * summoned.</p>
     *
     * <p>Fire's version launches; this one roots. Same shape, opposite
     * consequence.</p>
     */
    private void spikes(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        Vector forward = Fx.look(player);

        world.playSound(origin, Sound.BLOCK_GLASS_PLACE, 1.4F, 0.6F);

        final int count = 6;

        for (int i = 0; i < count; i++) {
            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }
                    // Stepped outward on a fixed stride, so the line races away
                    // at a readable pace rather than scattering.
                    Location spot = Fx.ground(origin.clone()
                            .add(forward.clone().multiply(2.0D + index * 1.6D))
                            .add(0, 1, 0));

                    world.playSound(spot, Sound.BLOCK_GLASS_BREAK, 1.0F, 0.7F);

                    // A tapered shard: wide base, narrow tip, standing upright.
                    Model.group(plugin, spot)
                            .with(Model.piece(Material.PACKED_ICE.createBlockData())
                                    .at(0, 0, 0).size(0.7D, 1.6D, 0.7D)
                                    .glow(FROST_HOT))
                            .with(Model.piece(Material.ICE.createBlockData())
                                    .at(0, 1.5D, 0).size(0.38D, 1.5D, 0.38D)
                                    .spin(0.7D)
                                    .glow(FROST_CORE))
                            .grow(3)
                            .fadeAfter(lasts("spikes", 120), 8);

                    for (double y = 0; y < 2.8D; y += 0.35D) {
                        world.spawnParticle(Particle.SNOWFLAKE,
                                spot.clone().add(0, y, 0), 2,
                                0.25D, 0.1D, 0.25D, 0.02D);
                    }
                    world.spawnParticle(Particle.BLOCK_CRUMBLE,
                            spot.clone().add(0, 0.1D, 0), 10, 0.3D, 0.05D, 0.3D, 0,
                            Material.PACKED_ICE.createBlockData());

                    Fx.areaDamage(player, spot.clone().add(0, 1.2D, 0),
                            reach("spikes", 1.8D), dmg("spikes", 8.0D),
                            (target, falloff) -> {
                                chill(target, 100, 2);
                                // Pinned rather than launched - ice takes the
                                // option to move, fire takes the ground.
                                root(target, 40);
                                target.setVelocity(new Vector(0, 0.25D, 0));
                            });
                }
            }.runTaskLater(plugin, index * 3L);
        }
    }

    /**
     * Look up then RMB - an inverted glacier dropped point-first.
     *
     * <p>Ice's heavy hitter. Fire drops a tumbling rock; ice drops a mountain
     * upside down, which is both the better joke and the better silhouette.</p>
     */
    private void glacier(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8F, 0.5F);

        // Widest at the top, tapering to a point - an upside-down mountain.
        Model.Group berg = Model.group(plugin, player.getLocation().add(0, 44, 0))
                .with(Model.piece(Material.BLUE_ICE.createBlockData())
                        .at(0, 2.2D, 0).size(3.4D, 1.6D, 3.4D).glow(FROST_MID))
                .with(Model.piece(Material.PACKED_ICE.createBlockData())
                        .at(0, 1.1D, 0).size(2.4D, 1.4D, 2.4D).spin(0.5D))
                .with(Model.piece(Material.ICE.createBlockData())
                        .at(0, 0.2D, 0).size(1.4D, 1.2D, 1.4D).spin(1.1D).glow(FROST_HOT))
                .with(Model.piece(Material.PACKED_ICE.createBlockData())
                        .at(0, -0.6D, 0).size(0.7D, 1.0D, 0.7D).glow(FROST_CORE))
                .grow(5);

        int[] spin = {0};

        Fx.skyfall(plugin, player, 44.0D,
                falling -> {
                    berg.moveTo(falling);
                    spin[0]++;
                    // A slow majestic rotation, not a tumble.
                    if (spin[0] % 3 == 0) {
                        berg.respin(spin[0] * 0.05D, 0);
                    }
                    world.spawnParticle(Particle.SNOWFLAKE, falling, 10,
                            0.9D, 1.2D, 0.9D, 0.04D);
                    world.spawnParticle(Particle.ITEM_SNOWBALL, falling, 4,
                            0.8D, 1.0D, 0.8D, 0.03D);
                    world.spawnParticle(Particle.CLOUD, falling, 3, 1.0D, 1.0D, 1.0D, 0.02D);
                },
                (mark, locked) -> Fx.ring(world, mark, locked ? 3.2D : 3.8D,
                        locked ? FROST_LOW : FROST_HOT, 1.3F, locked ? 30 : 22,
                        locked ? 0 : System.currentTimeMillis() / 400.0D),
                impact -> {
                    berg.remove();
                    world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, 0.6F);
                    world.playSound(impact, Sound.BLOCK_GLASS_BREAK, 2.0F, 0.4F);
                    world.playSound(impact, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5F, 0.7F);

                    world.spawnParticle(Particle.SNOWFLAKE, impact, 150,
                            3.0D, 1.0D, 3.0D, 0.12D);
                    world.spawnParticle(Particle.BLOCK_CRUMBLE, impact, 60,
                            2.5D, 0.6D, 2.5D, 0, Material.BLUE_ICE.createBlockData());
                    world.spawnParticle(Particle.CLOUD, impact, 40, 2.5D, 0.8D, 2.5D, 0.05D);

                    Fx.shockwave(plugin, world, impact.clone().add(0, 0.3D, 0),
                            7.0D, Particle.SNOWFLAKE, FROST_HOT);

                    // Shards left standing in the crater.
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.PI * 2 * i / 8;
                        Location spot = Fx.ground(impact.clone().add(
                                Math.cos(angle) * 2.3D, 1, Math.sin(angle) * 2.3D));
                        double h = 1.4D + Math.random() * 0.9D;
                        Model.group(plugin, spot)
                                .with(Model.piece(Material.PACKED_ICE.createBlockData())
                                        .at(0, 0, 0).size(0.5D, h, 0.5D)
                                        .tilt((Math.random() - 0.5D) * 0.5D)
                                        .glow(FROST_HOT))
                                .grow(4)
                                .fadeAfter(160, 10);
                    }

                    Fx.areaDamage(player, impact, 6.5D, 17.0D, (target, falloff) -> {
                        chill(target, (int) (140 * falloff), 3);
                        freeze(target, (int) (120 * falloff));
                        Fx.knockback(target, impact, 0.7D * falloff, 0.3D * falloff);
                    });
                });
    }

    /**
     * Q Q - eight orbiting shards of ice, four low clockwise and four high
     * anticlockwise.
     *
     * <p>Same shape as fire's shield, different consequence: these barely hurt,
     * but every contact chills. Walking into fire's ring costs health; walking
     * into this one costs the ability to walk away.</p>
     */
    private void aegis(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.2F, 1.2F);

        final int count = 8;
        final int perRing = count / 2;
        final double radius = reach("aegis", 2.2D);
        final int life = lasts("aegis", 160);

        // Update and interpolate on the same beat, so each tween finishes as
        // the next begins and the orbit reads as continuous.
        final int step = 3;

        List<Model.Body> shards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean low = i < perRing;
            shards.add(Model.body(plugin, player.getLocation(),
                    Model.piece(low
                                    ? Material.PACKED_ICE.createBlockData()
                                    : Material.BLUE_ICE.createBlockData())
                            // Tapered, so they read as shards not dice.
                            .size(0.3D, 0.55D, 0.3D)
                            .glow(low ? FROST_HOT : FROST_CORE),
                    4));
        }

        long[] lastHit = new long[count];

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > life || !player.isOnline()) {
                    shards.forEach(shard -> shard.fade(plugin, 6));
                    cancel();
                    return;
                }

                Location centre = player.getLocation();
                long now = System.currentTimeMillis();
                boolean moveBeat = ticks % step == 0;

                for (int i = 0; i < count; i++) {
                    Model.Body shard = shards.get(i);
                    if (!shard.isAlive()) {
                        continue;
                    }

                    boolean low = i < perRing;
                    int index = low ? i : i - perRing;
                    double rate = 0.14D * (low ? 1.0D : -1.0D);
                    double angle = ticks * rate + Math.PI * 2 * index / perRing;
                    double height = low ? 0.5D : 1.6D;

                    if (moveBeat) {
                        shard.reanchor(centre);
                        double[] drift = shard.offsetTo(centre);
                        double ahead = angle + rate * step;
                        shard.glideTo(drift[0] + Math.cos(ahead) * radius,
                                drift[1] + height,
                                drift[2] + Math.sin(ahead) * radius,
                                ahead, 0.2D, step);
                    }

                    Location at = centre.clone().add(
                            Math.cos(angle) * radius, height, Math.sin(angle) * radius);

                    if (ticks % 2 == 0) {
                        Location tail = centre.clone().add(
                                Math.cos(angle - 0.3D) * radius, height,
                                Math.sin(angle - 0.3D) * radius);
                        world.spawnParticle(Particle.SNOWFLAKE, tail, 1,
                                0.03D, 0.03D, 0.03D, 0.004D);
                    }

                    if (now - lastHit[i] < 500L) {
                        continue;
                    }
                    final int slot = i;
                    Fx.areaDamage(player, at, 1.1D, dmg("aegis", 2.0D),
                            (target, falloff) -> {
                                lastHit[slot] = now;
                                // Control, not impact: a hard chill and a small
                                // shove, where fire's ring would launch them.
                                chill(target, 90, 2);
                                freeze(target, 30);
                                Fx.knockback(target, centre, 0.55D, 0.2D);
                                world.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.7F, 1.6F);
                                world.spawnParticle(Particle.SNOWFLAKE, at, 12,
                                        0.25D, 0.25D, 0.25D, 0.04D);
                            });
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Q RMB RMB - a freezing vortex that rises around the caster.
     *
     * <p>Fire's ultimate is a firestorm that drags people in and throws them
     * clear. This is its mirror: the same climbing column, but instead of
     * flinging victims it locks them where they stand. Getting caught by the
     * vortex is survivable; getting caught by this is not being able to
     * leave.</p>
     *
     * <p>Still escapable - a dodge beats the pull - but only if spent before
     * the freeze lands.</p>
     */
    private void absoluteZero(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2F, 1.8F);
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0F, 0.4F);

        final int life = lasts("zero", 120);
        final int layers = 8;
        final int perLayer = 3;
        final double radius = 3.0D;
        final double maxHeight = 12.0D;
        final int step = 3;

        List<Model.Body> blades = new ArrayList<>(layers * perLayer);
        for (int i = 0; i < layers * perLayer; i++) {
            boolean deep = i % 3 == 2;
            blades.add(Model.body(plugin, player.getLocation(),
                    Model.piece(deep
                                    ? Material.BLUE_ICE.createBlockData()
                                    : Material.PACKED_ICE.createBlockData())
                            .size(0.5D, 0.5D, 0.5D)
                            .glow(deep ? FROST_LOW : FROST_HOT),
                    4));
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > life || !player.isOnline()) {
                    blades.forEach(b -> b.fade(plugin, 8));
                    if (player.isOnline()) {
                        shatter(player, player.getLocation());
                    }
                    cancel();
                    return;
                }

                double growth = Math.min(1.0D, ticks / 50.0D);
                double spin = ticks * (0.09D + growth * 0.10D);
                double top = maxHeight * growth;

                Location centre = player.getLocation();
                boolean moveBeat = ticks % step == 0;

                for (int layer = 0; layer < layers; layer++) {
                    double layerHeight = top * (layer + 1) / layers;

                    for (int slot = 0; slot < perLayer; slot++) {
                        int index = layer * perLayer + slot;
                        Model.Body blade = blades.get(index);
                        if (!blade.isAlive()) {
                            continue;
                        }

                        double angle = spin + Math.PI * 2 * slot / perLayer
                                + layer * 0.7D;
                        double r = radius + 0.2D * Math.sin(ticks * 0.09D + layer);

                        if (moveBeat) {
                            blade.reanchor(centre);
                            double[] drift = blade.offsetTo(centre);
                            double ahead = angle + (0.09D + growth * 0.10D) * step;
                            blade.glideTo(drift[0] + Math.cos(ahead) * r,
                                    drift[1] + layerHeight,
                                    drift[2] + Math.sin(ahead) * r,
                                    ahead, ticks * 0.18D + index, step);
                        }

                        if (ticks % 4 == 0 && layer % 2 == 0) {
                            world.spawnParticle(Particle.SNOWFLAKE,
                                    centre.clone().add(Math.cos(angle) * r,
                                            layerHeight, Math.sin(angle) * r),
                                    1, 0.08D, 0.08D, 0.08D, 0.01D);
                        }
                    }
                }

                // A column of frost marking the eye.
                if (ticks % 3 == 0) {
                    for (double y = 0; y < top; y += 1.2D) {
                        world.spawnParticle(Particle.CLOUD,
                                centre.clone().add(0, y, 0), 1, 0.15D, 0.1D, 0.15D, 0.01D);
                    }
                }

                if (ticks % 10 == 0) {
                    world.playSound(centre, Sound.BLOCK_POWDER_SNOW_STEP,
                            1.2F, (float) (0.4D + growth * 0.3D));
                }

                lockDown(player, centre, radius + 1.5D, growth, spin);

                if (ticks % 8 == 0) {
                    Fx.areaDamage(player, centre, radius + 1.5D, dmg("zero", 3.0D),
                            (target, falloff) -> {
                                chill(target, 60, 3);
                                freeze(target, 40);
                            });
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Drags everything inward and freezes it in place as the storm matures.
     *
     * <p>Fire's vortex adds lift, throwing victims around the eye. This one
     * does the opposite: the pull is gentler but the chill deepens, so being
     * caught means being unable to leave rather than being thrown about.</p>
     */
    private void lockDown(Player caster, Location centre, double radius,
                          double growth, double spin) {
        Fx.areaDamage(caster, centre, radius, 0, (target, falloff) -> {
            Vector toCentre = centre.toVector()
                    .subtract(target.getLocation().toVector());
            double distance = toCentre.length();
            if (distance < 0.6D) {
                return;
            }
            Vector inward = toCentre.normalize();
            Vector tangent = new Vector(-inward.getZ(), 0, inward.getX());

            // No lift - ice keeps people on the ground where the freeze works.
            Vector drag = inward.multiply(0.22D * growth)
                    .add(tangent.multiply(0.2D * growth))
                    .setY(0.0D);

            target.setVelocity(target.getVelocity().multiply(0.55D).add(drag));

            // The deeper the storm, the harder the hold.
            chill(target, 40, growth > 0.6D ? 4 : 2);
            if (growth > 0.8D) {
                root(target, 20);
            }
        });
    }

    /** The storm collapses and everything frozen in it shatters. */
    private void shatter(Player caster, Location centre) {
        World world = centre.getWorld();

        world.playSound(centre, Sound.BLOCK_GLASS_BREAK, 2.0F, 0.4F);
        world.playSound(centre, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.6F, 0.6F);
        world.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 1.4F);

        world.spawnParticle(Particle.SNOWFLAKE, centre, 140, 3.0D, 1.2D, 3.0D, 0.12D);
        world.spawnParticle(Particle.BLOCK_CRUMBLE, centre, 70, 2.5D, 1.0D, 2.5D, 0,
                Material.BLUE_ICE.createBlockData());
        world.spawnParticle(Particle.CLOUD, centre, 50, 2.5D, 0.8D, 2.5D, 0.06D);

        Fx.shockwave(plugin, world, centre.clone().add(0, 0.3D, 0),
                7.0D, Particle.SNOWFLAKE, FROST_HOT);

        // A crown of spikes left where the storm stood.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            Location spot = Fx.ground(centre.clone().add(
                    Math.cos(angle) * 2.4D, 1, Math.sin(angle) * 2.4D));
            double h = 1.5D + Math.random() * 1.0D;
            Model.group(plugin, spot)
                    .with(Model.piece(Material.PACKED_ICE.createBlockData())
                            .at(0, 0, 0).size(0.5D, h, 0.5D)
                            .tilt((Math.random() - 0.5D) * 0.4D)
                            .glow(FROST_HOT))
                    .grow(4)
                    .fadeAfter(120, 10);
        }

        // Anyone still held takes the shatter - the payoff for the wind-up.
        Fx.areaDamage(caster, centre, 6.5D, dmg("zero", 3.0D) * 4.0D,
                (target, falloff) -> {
                    freeze(target, target.getMaxFreezeTicks());
                    chill(target, 120, 3);
                });
    }

    // ------------------------------------------------------- channel + bolt

    /**
     * The basic attack: a spinning lance of ice that grows into a wall.
     *
     * <p>Fire's volley escalates by <em>count</em> - more cubes, wider ring.
     * Ice escalates by <em>reach</em>: one shard becomes three abreast, then
     * five, then seven, so a charged shot is a line you cannot step around
     * rather than a bigger ball. That is the same tier ladder read through
     * ice's identity - it controls space instead of concentrating force.</p>
     */
    @Override
    public void bolt(Player player, double power) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();

        int shards = shardCount(power);
        double damage = boltDamage(power);
        double blast = 0.9D + shards * 0.26D;
        double knock = knockbackFor(shards);
        int slow = plugin.balance().iceBoltSlow(power);

        Color tint = tint(power);

        world.playSound(eye, Sound.BLOCK_GLASS_BREAK, 1.0F,
                (float) (1.8D - power * 0.6D));
        if (shards >= 5) {
            world.playSound(eye, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0F, 0.8F);
        }

        // Shards fly abreast, spread across the aim, so the volley is a line.
        Vector forward = eye.getDirection().normalize();
        Vector across = Fx.right(new Vector(forward.getX(), 0, forward.getZ())
                .normalize());

        List<Model.Body> bodies = new ArrayList<>(shards);
        for (int i = 0; i < shards; i++) {
            bodies.add(Model.body(plugin, eye,
                    Model.piece(Material.PACKED_ICE.createBlockData())
                            // Elongated along flight - a lance, not a pebble.
                            .size(0.3D, 0.3D, 0.8D)
                            .glow(tint),
                    2));
        }

        // Spacing widens with the tier; one shard sits dead centre.
        double spacing = shards == 1 ? 0.0D : 0.45D;
        int[] age = {0};

        Fx.projectile(plugin, player, 1.1D + power * 0.8D, blast * 0.7D, 70,
                at -> {
                    age[0]++;
                    double spin = age[0] * 0.55D;

                    for (int i = 0; i < bodies.size(); i++) {
                        Model.Body shard = bodies.get(i);
                        if (!shard.isAlive()) {
                            continue;
                        }
                        // Centred offsets: -1,0,1 for three, -2..2 for five.
                        double lane = i - (bodies.size() - 1) / 2.0D;
                        Location slot = at.clone()
                                .add(across.clone().multiply(lane * spacing));

                        shard.reanchor(slot);
                        // Rifling along its own axis, which reads as a drill.
                        shard.glideTo(0, 0, 0, 0, spin, 2);
                    }

                    world.spawnParticle(Particle.SNOWFLAKE, at, 1 + shards / 3,
                            0.08D, 0.08D, 0.08D, 0.01D);
                    if (age[0] % 2 == 0) {
                        world.spawnParticle(Particle.DUST, at, 1,
                                0.12D, 0.12D, 0.12D, 0.0D,
                                new Particle.DustOptions(tint, 0.8F));
                    }
                },
                at -> {
                    // Each shard shatters outward along the line it flew in.
                    for (int i = 0; i < bodies.size(); i++) {
                        Model.Body shard = bodies.get(i);
                        if (!shard.isAlive()) {
                            continue;
                        }
                        double lane = i - (bodies.size() - 1) / 2.0D;
                        Vector fling = across.clone()
                                .multiply(lane * (0.5D + shards * 0.12D))
                                .setY(0.3D);
                        shard.reanchor(at);
                        shard.glideTo(fling.getX(), fling.getY(), fling.getZ(),
                                0, lane * 2, 4);
                        shard.fade(plugin, 4);
                    }

                    world.playSound(at, Sound.BLOCK_GLASS_BREAK,
                            (float) (0.7D + shards * 0.09D),
                            (float) (1.7D - shards * 0.08D));
                    if (shards >= 5) {
                        world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE,
                                (float) (0.6D + shards * 0.06D), 0.8F);
                    }

                    world.spawnParticle(Particle.SNOWFLAKE, at, 18 + shards * 14,
                            blast * 0.45D, blast * 0.45D, blast * 0.45D, 0.08D);
                    world.spawnParticle(Particle.BLOCK_CRUMBLE, at, 8 + shards * 6,
                            blast * 0.4D, blast * 0.4D, blast * 0.4D, 0,
                            Material.ICE.createBlockData());
                    world.spawnParticle(Particle.DUST, at, 10 + shards * 7,
                            blast * 0.55D, blast * 0.55D, blast * 0.55D, 0.03D,
                            new Particle.DustOptions(tint, 1.1F + shards * 0.1F));

                    // A frozen ring on the ground for the heavier tiers, so a
                    // big hit leaves the arena changed for a moment.
                    if (shards >= 5) {
                        Fx.shockwave(plugin, world, at.clone(), blast * 1.3D,
                                Particle.SNOWFLAKE, tint);
                    }

                    // The full volley freezes outright, briefly.
                    int freezeTicks = shards >= 7 ? 60 : 0;
                    Fx.areaDamage(player, at, blast, damage, (target, falloff) -> {
                        chill(target, (int) (slow * falloff), shards >= 5 ? 2 : 1);
                        if (freezeTicks > 0) {
                            freeze(target, (int) (freezeTicks * falloff));
                        }
                        if (knock > 0.0D) {
                            Fx.knockback(target, at, knock * falloff, 0.25D * falloff);
                        }
                    });
                });
    }

    /**
     * How many shards a given charge throws.
     *
     * <p>Odd counts only, so there is always a centre lane pointing where the
     * player actually aimed.</p>
     */
    private static int shardCount(double power) {
        return power >= 0.85D ? 7
                : power >= 0.55D ? 5
                : power >= 0.25D ? 3
                : 1;
    }

    /**
     * Knockback by tier - lighter than fire's throughout.
     *
     * <p>Ice pays for its control with impact: where the fire volley punts
     * people across the arena, ice nudges them and takes their speed instead.</p>
     */
    private double knockbackFor(int shards) {
        double fallback = switch (shards) {
            case 7 -> 0.7D;
            case 5 -> 0.45D;
            case 3 -> 0.2D;
            default -> 0.0D;
        };
        return plugin.balance().tierKnockback(Element.ICE, shards, fallback);
    }

    @Override
    public double boltDamage(double power) {
        // Softer per tier than fire, because every tier also slows.
        double fallback = switch (shardCount(power)) {
            case 7 -> 13.0D;
            case 5 -> 9.0D;
            case 3 -> 6.0D;
            default -> 2.5D;
        };
        return plugin.balance().tierDamage(Element.ICE, shardCount(power), fallback);
    }

    @Override
    public int boltMana(double power) {
        return plugin.balance().boltManaBase(Element.ICE, 9)
                + (int) Math.round(power
                        * plugin.balance().boltManaPerCharge(Element.ICE, 32));
    }

    @Override
    public String boltName(double power) {
        return switch (shardCount(power)) {
            case 7 -> "7 ОСКОЛКІВ";
            case 5 -> "5 ОСКОЛКІВ";
            case 3 -> "3 ОСКОЛКИ";
            default -> "КРИЖАНИЙ ОСКОЛОК";
        };
    }

    @Override
    public void channel(Player player, int tick, double power) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection().normalize();

        double reach = 5.0D + power * 4.0D;

        for (double d = 1.0D; d < reach; d += 0.4D) {
            double spread = 0.06D + (d / reach) * (0.4D + power * 0.25D);
            Location p = eye.clone().add(dir.clone().multiply(d));

            if (p.getBlock().getType().isSolid()) {
                world.spawnParticle(Particle.SNOWFLAKE, p, 4,
                        spread, spread, spread, 0.03D);
                break;
            }
            world.spawnParticle(Particle.SNOWFLAKE, p, 2, spread, spread, spread, 0.02D);
            if (tick % 2 == 0) {
                world.spawnParticle(Particle.CLOUD, p, 1, spread, spread, spread, 0.01D);
            }
            if (d > reach * 0.5D) {
                world.spawnParticle(Particle.ITEM_SNOWBALL, p, 1,
                        spread * 0.7D, spread * 0.7D, spread * 0.7D, 0.02D);
            }
        }

        if (tick % 5 == 0) {
            world.playSound(eye, Sound.BLOCK_POWDER_SNOW_STEP, 0.9F, 0.6F);
        }

        if (tick % 5 != 0) {
            return;
        }
        double damage = plugin.balance()
                .channelDamage(Element.ICE, power, 1.2D, 2.8D);
        Location mid = eye.clone().add(dir.clone().multiply(reach * 0.5D));
        Fx.areaDamage(player, mid, reach * 0.6D, 0, (target, falloff) -> {
            Vector toTarget = target.getLocation().add(0, 0.9D, 0)
                    .toVector().subtract(eye.toVector());
            if (toTarget.length() > reach || toTarget.normalize().dot(dir) < 0.75D) {
                return;
            }
            Fx.hit(player, target, damage);
            chill(target, 40, power >= 0.6D ? 2 : 1);
            // The blizzard builds freeze rather than applying it outright.
            freeze(target, 25);
        });
    }

    @Override
    public double channelDps(double power) {
        return plugin.balance().channelDamage(Element.ICE, power, 1.2D, 2.8D) * 4.0D;
    }

    @Override
    public int channelMana() {
        return plugin.balance().channelMana(Element.ICE, 3);
    }

    @Override
    public String channelName() {
        return "ХУРТОВИНА";
    }

    // -------------------------------------------------------------- utility

    /**
     * Ice has no block interaction of its own.
     *
     * <p>Freezing water used to be a right click, which spent a gesture slot on
     * something that reads better as a property of the mage than an action. It
     * happens automatically in the frost form now - see
     * {@link FrostForm} - the way Frost Walker boots work.</p>
     */
    @Override
    public boolean interact(Player player, Block block) {
        World world = block.getWorld();

        // Lava is still worth a deliberate cast: quenching it is a decision,
        // not something you want happening as you walk past.
        if (block.getType() == Material.LAVA) {
            block.setType(Material.OBSIDIAN);
            Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
            world.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, 1.2F, 0.8F);
            world.spawnParticle(Particle.CLOUD, at, 30, 0.5D, 0.5D, 0.5D, 0.05D);
            return true;
        }

        if (block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE) {
            block.setType(Material.AIR);
            Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
            world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.4F);
            world.spawnParticle(Particle.SNOWFLAKE, at, 15, 0.3D, 0.3D, 0.3D, 0.03D);
            return true;
        }

        return false;
    }

    /**
     * Ice's passive: the mage does not freeze, and does not slip.
     *
     * <p>The mirror of fire's. Ice's own spells leave frozen ground everywhere,
     * and its ultimate freezes an area the caster is standing in - so immunity
     * to its own element is the same practical fix. Depth Strider is the second
     * half: a frost mage wading through the water they froze should not be the
     * slowest thing on the field.</p>
     */
    @Override
    public void passive(Player player) {
        if (player.getFreezeTicks() > 0) {
            player.setFreezeTicks(0);
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DOLPHINS_GRACE, 40, 0, false, false, false));
    }

    @Override
    public void clearPassive(Player player) {
        PotionEffect existing = player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        if (existing != null && existing.getDuration() <= 40) {
            player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        }
    }

    @Override
    public String passiveName() {
        return "холод не шкодить";
    }

    @Override
    public void aura(Player player, double manaFraction, int tick) {
        Location base = player.getLocation();
        World world = base.getWorld();

        double radius = 0.85D + 0.08D * Math.sin(tick * 0.12D);
        Color tint = manaFraction > 0.6D ? FROST_HOT
                : manaFraction > 0.3D ? FROST_MID : FROST_LOW;

        for (int i = 0; i < 3; i++) {
            double angle = tick * 0.15D + Math.PI * 2 * i / 3;
            world.spawnParticle(Particle.DUST, base.clone().add(
                            Math.cos(angle) * radius, 0.06D, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, new Particle.DustOptions(tint, 0.65F));
        }
        if (tick % 8 == 0 && manaFraction > 0.15D) {
            world.spawnParticle(Particle.SNOWFLAKE,
                    base.clone().add(0, 0.7D, 0), 1, 0.45D, 0.12D, 0.45D, 0.003D);
        }
    }

    @Override
    public void chargeGlow(Player player, double power, int tick) {
        Location hand = FireSchool.handPoint(player);
        World world = hand.getWorld();

        double size = 0.09D + power * 0.26D;
        world.spawnParticle(Particle.SNOWFLAKE, hand, 1 + (int) (power * 3),
                size, size, size, 0.003D);
        world.spawnParticle(Particle.DUST, hand, 1 + (int) (power * 3),
                size, size, size, 0.0D,
                new Particle.DustOptions(tint(power), (float) (0.6D + power * 0.8D)));

        if (power > 0.3D) {
            double angle = tick * 0.45D;
            double r = 0.5D * (1.0D - power);
            world.spawnParticle(Particle.ITEM_SNOWBALL,
                    hand.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r),
                    0, 0, 0, 0, 1.0D);
        }
    }

    // -------------------------------------------------------------- helpers

    /** Slowness plus mining fatigue - cold hands swing slower too. */
    static void chill(LivingEntity target, int ticks, int amplifier) {
        if (ticks <= 0) {
            return;
        }
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, ticks, amplifier, false, true, true));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.MINING_FATIGUE, ticks, 0, false, false, true));
    }

    /** The vanilla powder-snow freeze, complete with its screen vignette. */
    static void freeze(LivingEntity target, int ticks) {
        if (ticks <= 0) {
            return;
        }
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(),
                target.getFreezeTicks() + ticks));
    }

    /**
     * A hard root.
     *
     * <p>Slowness alone never fully stops a player, so this is a very high
     * amplifier plus jump suppression - as close as vanilla gets to "held in
     * place" without teleport-locking, which would fight the client.</p>
     */
    static void root(LivingEntity target, int ticks) {
        if (ticks <= 0) {
            return;
        }
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, ticks, 6, false, true, true));
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
    }

    /** Deeper blue as the charge builds - a heavy shard looks colder. */
    private static Color tint(double power) {
        return power >= 0.85D ? FROST_LOW
                : power >= 0.6D ? FROST_MID
                : power >= 0.3D ? FROST_HOT
                : FROST_CORE;
    }
}
