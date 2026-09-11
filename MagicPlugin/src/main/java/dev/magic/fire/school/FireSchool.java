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
 * Fire: damage, burn, and terrain you set alight.
 *
 * <p>Three flame tiers carry the identity - orange for ordinary casts, soul-blue
 * for the heavy ones, and copper-green for the ultimate. A player can read how
 * much trouble they are in from the colour alone.</p>
 *
 * <p>Fire's utility is ignition: it lights campfires and nether portals, and
 * sets a creeper's fuse burning on the spot. It deliberately does not spread
 * fire to terrain.</p>
 */
public final class FireSchool implements School {

    // Ember ramp.
    private static final Color EMBER_CORE = Color.fromRGB(0xFF, 0xF3, 0xC4);
    private static final Color EMBER_HOT = Color.fromRGB(0xFF, 0xC2, 0x4B);
    private static final Color EMBER_MID = Color.fromRGB(0xFF, 0x7A, 0x2F);
    private static final Color EMBER_LOW = Color.fromRGB(0xC4, 0x2E, 0x17);
    private static final Color SOUL = Color.fromRGB(0x7A, 0xE8, 0xFF);
    private static final Color VERDANT = Color.fromRGB(0x8A, 0xFF, 0x6A);

    /** Fuse length for a magically lit creeper - shorter than vanilla's 30. */
    private static final int CREEPER_FUSE = 22;

    private final MagicPlugin plugin;
    private final SpellBook book = new SpellBook(Element.FIRE);

    public FireSchool(MagicPlugin plugin) {
        this.plugin = plugin;
        register();
    }

    @Override
    public SpellBook book() {
        return book;
    }

    @Override
    public Element element() {
        return Element.FIRE;
    }

    /**
     * Fire's gesture table.
     *
     * <p>Patterns are chosen for this school alone - ice has its own book, so
     * neither has to give up a good gesture to keep the two symmetrical.</p>
     */
    /** Shorthand: this school's config lookups, keyed by spell id. */
    private int mana(String id, int fallback) {
        return plugin.balance().mana(Element.FIRE, id, fallback);
    }

    private long cool(String id, long fallback) {
        return plugin.balance().cooldown(Element.FIRE, id, fallback);
    }

    private double dmg(String id, double fallback) {
        return plugin.balance().damage(Element.FIRE, id, fallback);
    }

    private double reach(String id, double fallback) {
        return plugin.balance().range(Element.FIRE, id, fallback);
    }

    private int lasts(String id, int fallback) {
        return plugin.balance().duration(Element.FIRE, id, fallback);
    }

    private void register() {
        // Mobility.
        book.add("dash", "Ривок", mana("dash", 14), cool("dash", 700), 0, 0, 0,
                p -> dash(p, Fx.look(p).multiply(1.5D), 0.28D),
                InputType.TAP_FORWARD);
        book.add("backstep", "Відскок", mana("backstep", 12), cool("backstep", 600), 0, 0, 0,
                p -> dash(p, Fx.look(p).multiply(-1.2D), 0.32D),
                InputType.TAP_BACKWARD);
        book.add("dodge_left", "Ухил уліво", mana("dodge", 12), cool("dodge", 600), 0, 0, 0,
                p -> dash(p, Fx.right(Fx.look(p)).multiply(-1.35D), 0.32D),
                InputType.TAP_LEFT);
        book.add("dodge_right", "Ухил управо", mana("dodge", 12), cool("dodge", 600), 0, 0, 0,
                p -> dash(p, Fx.right(Fx.look(p)).multiply(1.35D), 0.32D),
                InputType.TAP_RIGHT);
        book.add("leap", "Вогняний стрибок", mana("leap", 18), cool("leap", 900), 0, 0, 0,
                this::leap,
                InputType.TAP_JUMP);

        // Fire-only: a forward shockwave. On RMB rather than LMB so it never
        // competes with the basic attack for the same button.
        book.add("charge", "Вогняний таран", mana("charge", 28), cool("charge", 1600),
                dmg("charge", 8.0D), reach("charge", 10.0D), 0,
                this::charge,
                InputType.TAP_FORWARD, InputType.RIGHT_CLICK);

        // Fire-only: a ring of flame that erupts where you stand. Moved off
        // Shift+LMB, which the channel now owns exclusively.
        book.add("nova", "Вогняна нова", mana("nova", 50), cool("nova", 4000),
                dmg("nova", 9.0D), reach("nova", 9.0D), 0,
                this::nova,
                InputType.SWAP_HAND, InputType.RIGHT_CLICK);

        // Control.
        book.add("wall", "Стіна полум'я", mana("wall", 30), cool("wall", 2500),
                dmg("wall", 3.0D), reach("wall", 3.2D), lasts("wall", 160),
                p -> plugin.walls().place(p, this),
                InputType.SNEAK_DOWN, InputType.RIGHT_CLICK);
        book.add("meteor", "Метеор", mana("meteor", 65), cool("meteor", 6000),
                dmg("meteor", 20.0D), reach("meteor", 6.0D), 0,
                this::meteor,
                InputType.LOOK_UP, InputType.RIGHT_CLICK);

        // Fire-only: a pillar of flame under the target.
        book.add("pillar", "Стовпи полум'я", mana("pillar", 34), cool("pillar", 2600),
                dmg("pillar", 8.0D), reach("pillar", 1.8D), 40,
                this::pillar,
                InputType.LOOK_DOWN, InputType.RIGHT_CLICK);

        book.add("aegis", "Вогняний щит", mana("aegis", 35), cool("aegis", 5000),
                dmg("aegis", 3.0D), reach("aegis", 2.0D), lasts("aegis", 160),
                this::aegis,
                InputType.DROP, InputType.DROP);
        book.add("inferno", "Вогняний вихор", mana("inferno", 90), cool("inferno", 9000),
                dmg("inferno", 12.0D), reach("inferno", 6.5D), lasts("inferno", 100),
                this::inferno,
                InputType.DROP, InputType.RIGHT_CLICK, InputType.RIGHT_CLICK);
    }

    // ------------------------------------------------------------- mobility

    private void dash(Player player, Vector push, double lift) {
        Location origin = player.getLocation();
        World world = origin.getWorld();

        player.setVelocity(push.clone().setY(lift));

        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 0.85F, 1.45F);
        world.playSound(origin, Sound.ENTITY_BAT_TAKEOFF, 0.8F, 1.5F);

        Vector back = push.clone().normalize().multiply(-1);
        for (int i = 0; i < 20; i++) {
            Location p = origin.clone()
                    .add(back.clone().multiply(i / 20.0D * 1.6D))
                    .add(0, 0.9D, 0);
            world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        Fx.trail(plugin, player, 10, Particle.SMALL_FLAME, 4, 0.15D);
    }

    private void leap(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();

        player.setVelocity(player.getVelocity().setY(1.05D));
        softLanding(player);

        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.7F);
        world.playSound(origin, Sound.ITEM_FIRECHARGE_USE, 1.2F, 0.8F);

        Fx.ring(world, origin.clone().add(0, 0.15D, 0), 0.9D, Particle.FLAME, 32);
        world.spawnParticle(Particle.LARGE_SMOKE, origin, 16, 0.4D, 0.1D, 0.4D, 0.04D);
        Fx.trail(plugin, player, 14, Particle.FLAME, 5, 0.2D);
    }

    // -------------------------------------------------------------- offence

    /**
     * W W then RMB - a battering ram of flame driven straight ahead.
     *
     * <p>Not a blast centred on the caster: a cylinder of fire that travels
     * forward ten blocks, shoving everything it passes. The old version
     * detonated a flat disc where the dash ended, which read as an explosion at
     * the player's feet rather than a charge <em>through</em> someone.</p>
     */
    private void charge(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        Vector forward = Fx.look(player);
        Vector side = Fx.right(forward);

        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.2F, 0.6F);
        world.playSound(eye, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.1F, 0.8F);

        // A short forward lunge, so the ram feels driven rather than thrown.
        player.setVelocity(forward.clone().multiply(0.75D).setY(0.12D));

        final double reach = 10.0D;
        final double girth = 1.4D;
        final Location origin = player.getLocation().add(0, 0.9D, 0);

        new BukkitRunnable() {
            double travelled = 0.6D;

            @Override
            public void run() {
                if (travelled > reach || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location front = origin.clone()
                        .add(forward.clone().multiply(travelled));

                // Stop at a wall - a ram does not pass through stone.
                if (front.getBlock().getType().isSolid()) {
                    world.spawnParticle(Particle.LARGE_SMOKE, front, 20,
                            0.5D, 0.5D, 0.5D, 0.04D);
                    world.playSound(front, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.3F);
                    cancel();
                    return;
                }

                // Cylinder cross-section: a disc of flame standing across the
                // path, which is what makes the front read as a solid face.
                for (int i = 0; i < 14; i++) {
                    double angle = Math.PI * 2 * i / 14 + travelled;
                    Vector offset = side.clone().multiply(Math.cos(angle) * girth)
                            .add(new Vector(0, Math.sin(angle) * girth, 0));
                    Location p = front.clone().add(offset);
                    world.spawnParticle(Particle.FLAME, p, 0, 0, 0.02D, 0, 1.0D);
                    world.spawnParticle(Particle.DUST, p, 1, 0.05D, 0.05D, 0.05D, 0,
                            new Particle.DustOptions(EMBER_MID, 1.1F));
                }
                // Core of the cylinder, so it is not a hollow hoop.
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, front, 4,
                        girth * 0.4D, girth * 0.4D, girth * 0.4D, 0.02D);
                world.spawnParticle(Particle.LAVA, front, 1, 0.3D, 0.3D, 0.3D, 0);

                if (travelled % 2.0D < 0.6D) {
                    world.playSound(front, Sound.BLOCK_FIRE_AMBIENT, 0.8F, 0.7F);
                }

                // Push along the ram's own axis, not away from a centre point -
                // a charge throws people backwards, it does not scatter them.
                Fx.areaDamage(player, front, girth + 0.6D, 0, (target, falloff) -> {
                    if (target.getNoDamageTicks() > 0) {
                        return;
                    }
                    Fx.hit(player, target, dmg("charge", 8.0D));
                    burn(target, 100);
                    target.setVelocity(forward.clone().multiply(1.1D).setY(0.45D));
                });

                travelled += 0.7D;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Shift then LMB - a ring of fire racing outward from the caster. */
    private void nova(Player player) {
        Location centre = player.getLocation();
        World world = centre.getWorld();

        world.playSound(centre, Sound.ENTITY_BLAZE_SHOOT, 1.6F, 0.5F);
        world.playSound(centre, Sound.ITEM_TRIDENT_THUNDER, 1.0F, 1.4F);

        new BukkitRunnable() {
            double radius = 0.5D;

            @Override
            public void run() {
                if (radius > 9.0D) {
                    cancel();
                    return;
                }
                Fx.ring(world, centre.clone().add(0, 0.35D, 0), radius,
                        Particle.SOUL_FIRE_FLAME, (int) (radius * 12));
                Fx.ring(world, centre.clone().add(0, 0.35D, 0), radius,
                        SOUL, 1.3F, (int) (radius * 8), 0);

                // Only the advancing front hits, so the ring reads as a wave.
                final double front = radius;
                Fx.areaDamage(player, centre, radius, 0, (target, falloff) -> {
                    double dist = target.getLocation().distance(centre);
                    if (dist > front - 1.0D && dist <= front) {
                        Fx.hit(player, target, dmg("nova", 9.0D));
                        burn(target, 120);
                        Fx.knockback(target, centre, 1.1D, 0.55D);
                    }
                });

                radius += 0.7D;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Look down then RMB - six columns of flame erupt away from the caster.
     *
     * <p>The old version put a single pillar on the caster's own position,
     * which blocked their view and threatened nobody. This walks a line of
     * columns forward instead - fire's answer to ice's spike line, but vertical
     * and launching rather than rooting.</p>
     */
    private void pillar(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        Vector forward = Fx.look(player);

        world.playSound(origin, Sound.ITEM_FIRECHARGE_USE, 1.4F, 0.6F);

        final int columns = 6;

        for (int i = 0; i < columns; i++) {
            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }
                    // Stepped outward, so the line visibly races away.
                    Location spot = Fx.ground(origin.clone()
                            .add(forward.clone().multiply(2.0D + index * 1.6D))
                            .add(0, 1, 0));

                    world.playSound(spot, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);

                    // A short-lived column of real geometry per burst.
                    Model.group(plugin, spot)
                            .with(Model.piece(Material.SOUL_FIRE.createBlockData())
                                    .at(0, 0, 0).size(0.9D, 3.6D, 0.9D).glow(SOUL))
                            .with(Model.piece(Material.SOUL_FIRE.createBlockData())
                                    .at(0, 0, 0).size(1.4D, 1.8D, 1.4D)
                                    .spin(0.6D).glow(SOUL))
                            .grow(3)
                            .fadeAfter(20, 6);

                    for (double y = 0; y < 4.2D; y += 0.35D) {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                                spot.clone().add(0, y, 0), 2,
                                0.3D, 0.1D, 0.3D, 0.02D);
                    }
                    world.spawnParticle(Particle.LAVA, spot, 4, 0.3D, 0.1D, 0.3D, 0);

                    Fx.areaDamage(player, spot.clone().add(0, 1.5D, 0),
                            reach("pillar", 1.8D), dmg("pillar", 8.0D),
                            (target, falloff) -> {
                                burn(target, 120);
                                // Straight up - a pillar throws, it does not push.
                                target.setVelocity(new Vector(0, 1.15D, 0));
                            });
                }
            }.runTaskLater(plugin, index * 3L);
        }
    }

    /**
     * A meteor the caster steers down onto a target.
     *
     * <p>Built as a tumbling block-display cluster rather than a particle cloud,
     * so it reads as a solid rock falling out of the sky.</p>
     */
    private void meteor(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 1.8F);

        // Built as a shell of small cubes on a sphere rather than a few large
        // ones stuck together: an off-centre lump looks broken once it starts
        // tumbling, while an even shell reads as a round rock from any angle.
        Model.Group rock = sphere(player.getLocation().add(0, 40, 0),
                1.5D, 0.62D);

        int[] spin = {0};

        Fx.skyfall(plugin, player, 40.0D,
                falling -> {
                    rock.moveTo(falling);
                    spin[0]++;
                    if (spin[0] % 2 == 0) {
                        rock.respin(spin[0] * 0.18D, spin[0] * 0.1D);
                    }
                    world.spawnParticle(Particle.FLAME, falling, 6, 0.6D, 0.6D, 0.6D, 0.03D);
                    world.spawnParticle(Particle.LARGE_SMOKE, falling, 4,
                            0.7D, 0.7D, 0.7D, 0.02D);
                    world.spawnParticle(Particle.LAVA, falling, 1, 0.4D, 0.4D, 0.4D, 0);
                },
                (mark, locked) -> Fx.ring(world, mark, locked ? 3.0D : 3.6D,
                        locked ? EMBER_LOW : EMBER_HOT, 1.3F, locked ? 30 : 22,
                        locked ? 0 : System.currentTimeMillis() / 400.0D),
                impact -> {
                    rock.remove();
                    world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.5F);
                    world.playSound(impact, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5F, 0.7F);

                    world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 4,
                            1.2D, 0.5D, 1.2D, 0);
                    world.spawnParticle(Particle.FLAME, impact, 120, 3.0D, 1.0D, 3.0D, 0.15D);
                    world.spawnParticle(Particle.LAVA, impact, 40, 2.2D, 0.5D, 2.2D, 0);

                    Fx.shockwave(plugin, world, impact.clone().add(0, 0.3D, 0),
                            7.0D, Particle.FLAME, EMBER_MID);

                    // Smouldering debris left in the crater.
                    for (int i = 0; i < 5; i++) {
                        double angle = Math.PI * 2 * i / 5;
                        Location spot = Fx.ground(impact.clone().add(
                                Math.cos(angle) * 2.0D, 1, Math.sin(angle) * 2.0D));
                        Model.group(plugin, spot)
                                .with(Model.piece(Material.MAGMA_BLOCK.createBlockData())
                                        .at(0, 0, 0).size(0.6D, 0.5D, 0.6D)
                                        .spin(Math.random() * 3).glow(EMBER_LOW))
                                .grow(4)
                                .fadeAfter(120, 10);
                    }

                    Fx.areaDamage(player, impact, 6.0D, 20.0D, (target, falloff) -> {
                        burn(target, 160);
                        Fx.knockback(target, impact, 1.2D * falloff, 0.4D * falloff);
                    });
                });
    }

    /**
     * Q Q - eight orbiting fireballs, four low clockwise and four high
     * anticlockwise.
     *
     * <p>Real bodies rather than a particle cloud: they are readable from
     * outside, they do not fog the caster's own view, and each one is a hitbox -
     * so the shield actually stops someone walking in, instead of ticking
     * ambient damage at whatever happens to be nearby.</p>
     */
    private void aegis(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.2F, 0.8F);

        final int count = 8;
        final int perRing = count / 2;
        final double radius = 2.0D;
        final int life = 160;

        // Orbit is recomputed on this beat and interpolated over exactly the
        // same span, so each tween finishes right as the next begins - the
        // motion is continuous instead of restarting every tick.
        final int step = 3;

        List<Model.Body> orbs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean low = i < perRing;
            orbs.add(Model.body(plugin, player.getLocation(),
                    Model.piece(Material.MAGMA_BLOCK.createBlockData())
                            .size(0.45D, 0.45D, 0.45D)
                            .glow(low ? EMBER_MID : EMBER_HOT),
                    4));
        }

        // Each orb remembers when it last connected, so one body cannot machine
        // gun a single target while the other seven idle.
        long[] lastHit = new long[count];

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > life || !player.isOnline()) {
                    orbs.forEach(orb -> orb.fade(plugin, 6));
                    cancel();
                    return;
                }

                Location centre = player.getLocation();
                long now = System.currentTimeMillis();
                boolean moveBeat = ticks % step == 0;

                for (int i = 0; i < count; i++) {
                    Model.Body orb = orbs.get(i);
                    if (!orb.isAlive()) {
                        continue;
                    }

                    boolean low = i < perRing;
                    int index = low ? i : i - perRing;
                    // Low ring clockwise, high ring the other way, so the two
                    // read as separate layers rather than one blurred band.
                    double spin = ticks * 0.16D * (low ? 1.0D : -1.0D);
                    double angle = spin + Math.PI * 2 * index / perRing;
                    double height = low ? 0.45D : 1.55D;

                    double ox = Math.cos(angle) * radius;
                    double oz = Math.sin(angle) * radius;

                    if (moveBeat) {
                        // The caster's own drift is folded into the transform,
                        // so a standing player never interrupts the tween and a
                        // moving one is only re-anchored when they truly move.
                        orb.reanchor(centre);
                        double[] drift = orb.offsetTo(centre);
                        // Aim one beat ahead, so the tween lands where the orb
                        // should be when the next update arrives.
                        double ahead = angle + 0.16D * step * (low ? 1.0D : -1.0D);
                        orb.glideTo(drift[0] + Math.cos(ahead) * radius,
                                drift[1] + height,
                                drift[2] + Math.sin(ahead) * radius,
                                ahead, ticks * 0.12D, step);
                    }

                    Location at = centre.clone().add(ox, height, oz);

                    // A short trailing wisp behind each orb - the particles
                    // follow the bodies now, instead of being the effect.
                    if (ticks % 2 == 0) {
                        Location tail = centre.clone().add(
                                Math.cos(angle - 0.35D) * radius,
                                height,
                                Math.sin(angle - 0.35D) * radius);
                        world.spawnParticle(Particle.SMALL_FLAME, tail, 1,
                                0.03D, 0.03D, 0.03D, 0.005D);
                        world.spawnParticle(Particle.DUST, tail, 1, 0.05D, 0.05D, 0.05D, 0,
                                new Particle.DustOptions(low ? EMBER_MID : EMBER_HOT, 0.7F));
                    }

                    // Contact: this orb hits, and shoves the target outward.
                    if (now - lastHit[i] < 500L) {
                        continue;
                    }
                    final int slot = i;
                    Fx.areaDamage(player, at, 1.1D, 3.0D, (target, falloff) -> {
                        lastHit[slot] = now;
                        burn(target, 60);
                        Fx.knockback(target, centre, 1.15D, 0.35D);
                        world.playSound(at, Sound.ENTITY_BLAZE_HURT, 0.7F, 1.4F);
                        world.spawnParticle(Particle.FLAME, at, 12,
                                0.25D, 0.25D, 0.25D, 0.05D);
                    });
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Q RMB RMB - a cylindrical vortex that rises around the caster.
     *
     * <p>A column, not a cone: rings of block displays stacked from the ground
     * up, each rotating a little out of phase with the one below so the whole
     * thing reads as a twisting funnel. It grows to roughly twelve blocks tall
     * and follows the caster.</p>
     *
     * <p>Anything caught is dragged inward and carried around the rotation,
     * lifting as the storm matures - but a dodge gesture still beats the pull,
     * because a five second hold with no counterplay is not a fight.</p>
     */
    private void inferno(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4F, 0.7F);
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.6F, 0.5F);

        final int life = 100;
        final int layers = 8;
        final int perLayer = 3;
        final double radius = 3.2D;
        final double maxHeight = 12.0D;
        // Same cadence trick as the shield: update and tween on one beat.
        final int step = 3;

        List<Model.Body> blades = new ArrayList<>(layers * perLayer);
        for (int i = 0; i < layers * perLayer; i++) {
            boolean green = i % 3 == 2;
            blades.add(Model.body(plugin, player.getLocation(),
                    Model.piece(green
                                    ? Material.SHROOMLIGHT.createBlockData()
                                    : Material.MAGMA_BLOCK.createBlockData())
                            // Cubes, not slabs: a stretched block reads as a
                            // smeared texture, while a cube tumbling on its own
                            // axis looks like a solid object - same as the
                            // shield orbs.
                            .size(0.55D, 0.55D, 0.55D)
                            .glow(green ? VERDANT : EMBER_MID),
                    4));
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > life || !player.isOnline()) {
                    blades.forEach(b -> b.fade(plugin, 8));
                    // No payoff if the caster left - and collapse() would be
                    // resolving damage against an offline player.
                    if (player.isOnline()) {
                        collapse(player, player.getLocation());
                    }
                    cancel();
                    return;
                }

                // 0 at the start, 1 at full fury - drives height and spin, so
                // the column visibly climbs rather than just existing.
                double growth = Math.min(1.0D, ticks / 50.0D);
                double spin = ticks * (0.10D + growth * 0.12D);
                double top = maxHeight * growth;

                Location centre = player.getLocation();
                boolean moveBeat = ticks % step == 0;

                for (int layer = 0; layer < layers; layer++) {
                    // Only the layers the storm has reached are visible.
                    double layerHeight = top * (layer + 1) / layers;

                    for (int slot = 0; slot < perLayer; slot++) {
                        int index = layer * perLayer + slot;
                        Model.Body blade = blades.get(index);
                        if (!blade.isAlive()) {
                            continue;
                        }

                        // Each layer is offset from the one below, which turns
                        // a stack of rings into a twisting funnel.
                        double angle = spin + Math.PI * 2 * slot / perLayer
                                + layer * 0.7D;
                        // Cylindrical: the radius barely changes with height,
                        // only breathing a little so it is not a dead tube.
                        double r = radius + 0.25D * Math.sin(ticks * 0.1D + layer);

                        if (moveBeat) {
                            blade.reanchor(centre);
                            double[] drift = blade.offsetTo(centre);
                            double ahead = angle + (0.10D + growth * 0.12D) * step;
                            // Tumble on its own axis as well as orbiting, so
                            // each cube reads as a solid object in flight.
                            blade.glideTo(drift[0] + Math.cos(ahead) * r,
                                    drift[1] + layerHeight,
                                    drift[2] + Math.sin(ahead) * r,
                                    ahead, ticks * 0.22D + index, step);
                        }

                        // Particles trail the bodies rather than being the
                        // whole effect - a thin wisp, not a fog bank.
                        if (ticks % 4 == 0 && layer % 2 == 0) {
                            world.spawnParticle(slot == 2
                                            ? Particle.COPPER_FIRE_FLAME
                                            : Particle.FLAME,
                                    centre.clone().add(Math.cos(angle) * r,
                                            layerHeight, Math.sin(angle) * r),
                                    1, 0.08D, 0.08D, 0.08D, 0.01D);
                        }
                    }
                }

                // A slim column marking the eye of the storm.
                if (ticks % 3 == 0) {
                    for (double y = 0; y < top; y += 1.2D) {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                                centre.clone().add(0, y, 0), 1, 0.15D, 0.1D, 0.15D, 0.01D);
                    }
                }

                if (ticks % 10 == 0) {
                    world.playSound(centre, Sound.ENTITY_PHANTOM_FLAP,
                            1.0F, (float) (0.5D + growth * 0.5D));
                }

                pullIn(player, centre, radius + 1.5D, growth, spin);

                // Damage on a slower beat than the pull, so the storm wears
                // people down instead of shredding them instantly.
                if (ticks % 8 == 0) {
                    world.playSound(centre, Sound.BLOCK_FIRE_AMBIENT, 1.2F, 0.7F);
                    Fx.areaDamage(player, centre, radius + 1.5D, 4.0D,
                            (target, falloff) -> burn(target, 100));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Drags everything in range toward the vortex and carries it around the
     * rotation.
     *
     * <p>The pull is deliberately beatable: a dodge gesture applies a burst of
     * velocity far larger than this, so committing a mobility spell gets you
     * out. Standing still does not.</p>
     */
    private void pullIn(Player caster, Location centre, double radius,
                        double growth, double spin) {
        Fx.areaDamage(caster, centre, radius, 0, (target, falloff) -> {
            Vector toCentre = centre.toVector()
                    .subtract(target.getLocation().toVector());
            double distance = toCentre.length();
            if (distance < 0.6D) {
                return;
            }
            Vector inward = toCentre.normalize();
            // Tangent, so victims circle the eye rather than collapsing into it.
            Vector tangent = new Vector(-inward.getZ(), 0, inward.getX());

            Vector drag = inward.multiply(0.28D * growth)
                    .add(tangent.multiply(0.34D * growth))
                    // Lift grows with the storm - the mature vortex throws.
                    .setY(0.06D + growth * 0.16D);

            target.setVelocity(target.getVelocity().multiply(0.6D).add(drag));
        });
    }

    /** The storm collapses inward and detonates - the payoff for 90 mana. */
    private void collapse(Player caster, Location centre) {
        World world = centre.getWorld();

        world.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.6F);
        world.playSound(centre, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.4F, 0.8F);

        world.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 3, 1.0D, 0.5D, 1.0D, 0);
        world.spawnParticle(Particle.COPPER_FIRE_FLAME, centre, 90,
                2.5D, 1.2D, 2.5D, 0.14D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, centre, 60,
                2.0D, 1.0D, 2.0D, 0.12D);

        Fx.shockwave(plugin, world, centre.clone().add(0, 0.3D, 0),
                7.0D, Particle.COPPER_FIRE_FLAME, VERDANT);

        Location from = caster.getLocation();
        Fx.areaDamage(caster, centre, 6.5D, 12.0D, (target, falloff) -> {
            burn(target, 160);
            // Away from the caster, and harder the higher the storm had lifted
            // them - anyone riding the column gets flung clear.
            double lifted = Math.max(0.0D, target.getLocation().getY() - from.getY());
            double push = (1.3D + lifted * 0.18D) * falloff;
            Fx.knockback(target, from, push, (0.5D + lifted * 0.06D) * falloff);
        });
    }

    // ------------------------------------------------------- channel + bolt

    /**
     * The basic attack: spinning magma cubes, not a particle puff.
     *
     * <p>Clicking once throws a single cube. Sustained clicking builds the
     * volley to three, then five, then eight, orbiting each other as they fly -
     * so the charge level is legible from across the arena, and the escalation
     * is something the player can see rather than a number on a bar.</p>
     *
     * <p>Knockback is the reward tier: one cube is chip damage, three stop a
     * charge, five hit like a sword, eight like a knockback-enchanted one.</p>
     */
    @Override
    public void bolt(Player player, double power) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();

        int cubes = cubeCount(power);
        double damage = boltDamage(power);
        double blast = 1.0D + cubes * 0.32D;
        double knock = knockbackFor(cubes);
        int burnTicks = 40 + cubes * 14;

        // The volley shifts up the flame ramp as it grows.
        Particle flame = cubes >= 8 ? Particle.COPPER_FIRE_FLAME
                : cubes >= 5 ? Particle.SOUL_FIRE_FLAME
                : Particle.FLAME;
        Color tint = cubes >= 8 ? VERDANT : cubes >= 5 ? SOUL : EMBER_MID;

        world.playSound(eye, Sound.ITEM_FIRECHARGE_USE, 1.0F,
                (float) (1.5D - power * 0.6D));
        if (cubes >= 5) {
            world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.2F, 0.6F);
        }

        // One body per cube, orbiting the shot's own axis.
        List<Model.Body> bodies = new ArrayList<>(cubes);
        for (int i = 0; i < cubes; i++) {
            bodies.add(Model.body(plugin, eye,
                    Model.piece(Material.MAGMA_BLOCK.createBlockData())
                            .size(0.4D, 0.4D, 0.4D)
                            .glow(tint),
                    2));
        }

        // Ring radius grows with the volley, which is what makes a big shot
        // cover more ground rather than just doing more damage.
        double orbit = cubes == 1 ? 0.0D : 0.3D + cubes * 0.08D;
        int[] age = {0};

        // The ring has to stand perpendicular to the flight path. Building it
        // in world X/Z made a horizontal disc, which reads as a frisbee when
        // fired forward - so the two axes come from the shot direction instead.
        Vector forward = eye.getDirection().normalize();
        Vector ringRight = Fx.right(new Vector(forward.getX(), 0, forward.getZ())
                .normalize());
        Vector ringUp = forward.clone().crossProduct(ringRight).normalize();

        Fx.projectile(plugin, player, 0.95D + power * 0.7D, blast * 0.7D, 70,
                at -> {
                    age[0]++;
                    double spin = age[0] * 0.45D;

                    for (int i = 0; i < bodies.size(); i++) {
                        Model.Body cube = bodies.get(i);
                        if (!cube.isAlive()) {
                            continue;
                        }
                        double angle = spin + Math.PI * 2 * i / bodies.size();
                        // Offset across the ring plane, not across the ground.
                        Vector offset = ringRight.clone().multiply(Math.cos(angle) * orbit)
                                .add(ringUp.clone().multiply(Math.sin(angle) * orbit));
                        Location slot = orbit == 0.0D ? at : at.clone().add(offset);

                        // Re-anchor, then let the transform carry the tumble -
                        // the client tweens it, so the spin stays smooth.
                        cube.reanchor(slot);
                        cube.glideTo(0, 0, 0, spin, spin * 0.7D, 2);
                    }

                    // A thin trail behind the volley, not a cloud around it.
                    world.spawnParticle(flame, at, 1 + cubes / 3,
                            0.08D, 0.08D, 0.08D, 0.01D);
                    if (age[0] % 2 == 0) {
                        world.spawnParticle(Particle.DUST, at, 1,
                                0.12D, 0.12D, 0.12D, 0.0D,
                                new Particle.DustOptions(tint, 0.8F));
                    }
                },
                at -> {
                    // Every cube blows outward from the ring rather than simply
                    // vanishing, so the volley's size is legible in the impact.
                    for (int i = 0; i < bodies.size(); i++) {
                        Model.Body cube = bodies.get(i);
                        if (!cube.isAlive()) {
                            continue;
                        }
                        double angle = Math.PI * 2 * i / bodies.size();
                        Vector fling = ringRight.clone()
                                .multiply(Math.cos(angle) * (0.7D + cubes * 0.1D))
                                .add(ringUp.clone()
                                        .multiply(Math.sin(angle) * (0.7D + cubes * 0.1D)));
                        cube.reanchor(at);
                        cube.glideTo(fling.getX(), fling.getY(), fling.getZ(),
                                angle * 2, angle * 3, 4);
                        cube.fade(plugin, 4);
                    }

                    // Blast scale is driven by the tier, so eight cubes really
                    // do land like eight and one stays a pebble.
                    world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE,
                            (float) (0.5D + cubes * 0.09D),
                            (float) (1.7D - cubes * 0.09D));
                    if (cubes >= 5) {
                        world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE,
                                (float) (0.5D + cubes * 0.06D), 1.2F);
                    }

                    world.spawnParticle(Particle.EXPLOSION, at,
                            cubes >= 8 ? 3 : cubes >= 5 ? 2 : 1, 0.3D, 0.3D, 0.3D, 0);
                    if (cubes >= 8) {
                        // Only the full volley earns the big emitter.
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1,
                                0.2D, 0.2D, 0.2D, 0);
                    }
                    world.spawnParticle(flame, at, 16 + cubes * 12,
                            blast * 0.45D, blast * 0.45D, blast * 0.45D, 0.09D);
                    world.spawnParticle(Particle.DUST, at, 10 + cubes * 7,
                            blast * 0.55D, blast * 0.55D, blast * 0.55D, 0.03D,
                            new Particle.DustOptions(tint, 1.2F + cubes * 0.1F));
                    world.spawnParticle(Particle.LAVA, at, cubes,
                            blast * 0.3D, blast * 0.3D, blast * 0.3D, 0);

                    // A ground ring for the heavier tiers, so a big hit leaves
                    // a mark on the arena rather than a puff in the air.
                    if (cubes >= 5) {
                        Fx.shockwave(plugin, world, at.clone(), blast * 1.3D,
                                flame, tint);
                    }

                    Fx.areaDamage(player, at, blast, damage, (target, falloff) -> {
                        burnFor(target, (int) (burnTicks * falloff));
                        if (knock > 0.0D) {
                            Fx.knockback(target, at, knock * falloff, 0.3D * falloff);
                        }
                    });
                });
    }

    /**
     * How many cubes a given charge throws.
     *
     * <p>Deliberately stepped rather than continuous: the player should be able
     * to hear and see which tier they are at, and aim for it.</p>
     */
    private static int cubeCount(double power) {
        return power >= 0.85D ? 8
                : power >= 0.55D ? 5
                : power >= 0.25D ? 3
                : 1;
    }

    /** Knockback by tier - nothing, a stop, a sword, an enchanted sword. */
    private double knockbackFor(int cubes) {
        double fallback = switch (cubes) {
            case 8 -> 1.5D;
            case 5 -> 0.85D;
            case 3 -> 0.4D;
            default -> 0.0D;
        };
        return plugin.balance().tierKnockback(Element.FIRE, cubes, fallback);
    }

    @Override
    public double boltDamage(double power) {
        // Per tier, so the listed number matches what the volley actually is.
        int cubes = cubeCount(power);
        double fallback = switch (cubes) {
            case 8 -> 17.0D;
            case 5 -> 11.0D;
            case 3 -> 7.0D;
            default -> 3.0D;
        };
        return plugin.balance().tierDamage(Element.FIRE, cubes, fallback);
    }

    @Override
    public int boltMana(double power) {
        return plugin.balance().boltManaBase(Element.FIRE, 10)
                + (int) Math.round(power
                        * plugin.balance().boltManaPerCharge(Element.FIRE, 35));
    }

    @Override
    public String boltName(double power) {
        return switch (cubeCount(power)) {
            case 8 -> "8 КУБІВ";
            case 5 -> "5 КУБІВ";
            case 3 -> "3 КУБИ";
            default -> "МАГМОВИЙ КУБ";
        };
    }

    /**
     * The flamethrower: pure particles, tightening the longer it runs.
     *
     * <p>It starts short and scattered - a pilot light. Keep clicking without a
     * break and the cone narrows and reaches further, which rewards committing
     * to the channel instead of tapping it. All three flame colours are in play:
     * orange at the mouth, soul-blue through the middle once it is focused, and
     * copper-green at the tip when fully concentrated.</p>
     *
     * @param power how hard the player is clicking, 0..1
     */
    @Override
    public void channel(Player player, int tick, double power) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection().normalize();

        // Focus builds over roughly three seconds of unbroken clicking, then
        // click rate decides how much of that focus is actually delivered.
        double sustain = Math.min(1.0D, tick / 60.0D);
        double focus = sustain * (0.45D + power * 0.55D);

        double reach = 4.0D + focus * 7.0D;
        // Scatter collapses as focus rises - wide and soft to tight and hot.
        double cone = 0.55D - focus * 0.44D;

        for (double d = 1.0D; d < reach; d += 0.38D) {
            double along = d / reach;
            double spread = 0.05D + along * cone;
            Location p = eye.clone().add(dir.clone().multiply(d));

            if (p.getBlock().getType().isSolid()) {
                world.spawnParticle(Particle.LARGE_SMOKE, p, 3,
                        spread, spread, spread, 0.02D);
                break;
            }

            // Colour by distance along the jet, gated by how focused it is:
            // an unfocused jet never reaches the hotter colours at all.
            Particle flame;
            if (along > 0.72D && focus > 0.75D) {
                flame = Particle.COPPER_FIRE_FLAME;
            } else if (along > 0.4D && focus > 0.45D) {
                flame = Particle.SOUL_FIRE_FLAME;
            } else {
                flame = Particle.FLAME;
            }

            world.spawnParticle(flame, p, 2, spread, spread, spread, 0.015D);
            if (tick % 2 == 0) {
                world.spawnParticle(Particle.SMALL_FLAME, p, 1,
                        spread * 0.7D, spread * 0.7D, spread * 0.7D, 0.01D);
            }
            // Smoke only at the fringes of a loose jet - a tight one is clean.
            if (along > 0.6D && focus < 0.6D) {
                world.spawnParticle(Particle.LARGE_SMOKE, p, 1,
                        spread, spread, spread, 0.01D);
            }
        }

        if (tick % 4 == 0) {
            world.playSound(eye, Sound.BLOCK_FIRE_AMBIENT,
                    (float) (0.6D + focus * 0.6D), (float) (0.6D + focus * 0.3D));
        }

        if (tick % 5 != 0) {
            return;
        }
        double damage = 1.5D + focus * 3.5D;
        Location mid = eye.clone().add(dir.clone().multiply(reach * 0.5D));
        Fx.areaDamage(player, mid, reach * 0.6D, 0, (target, falloff) -> {
            Vector toTarget = target.getLocation().add(0, 0.9D, 0)
                    .toVector().subtract(eye.toVector());
            // A focused jet is also a narrower hit cone, not just longer.
            double aim = 0.75D + focus * 0.14D;
            if (toTarget.length() > reach || toTarget.normalize().dot(dir) < aim) {
                return;
            }
            Fx.hit(player, target, damage);
            burn(target, 60 + (int) (focus * 60));
        });
    }

    @Override
    public double channelDps(double power) {
        // Peak output, once the jet has had time to focus.
        return plugin.balance().channelDamage(Element.FIRE, power, 1.5D, 5.0D) * 4.0D;
    }

    @Override
    public int channelMana() {
        return plugin.balance().channelMana(Element.FIRE, 3);
    }

    @Override
    public String channelName() {
        return "ВОГНЕМЕТ";
    }

    // -------------------------------------------------------------- utility

    /**
     * Fire's out-of-combat use: a lighter.
     *
     * <p>Lights campfires and nether portals, melts snow. Deliberately does not
     * set blocks on fire - a mage who burns down the build is not fun.</p>
     */
    @Override
    public boolean interact(Player player, Block block) {
        Material type = block.getType();
        World world = block.getWorld();
        Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);

        // Campfires and candles light up.
        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Campfire fire
                    && !fire.isLit()) {
                fire.setLit(true);
                block.setBlockData(fire);
                world.playSound(at, Sound.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.1F);
                world.spawnParticle(Particle.FLAME, at, 15, 0.3D, 0.3D, 0.3D, 0.05D);
                return true;
            }
        }

        // Obsidian frame becomes a portal.
        if (type == Material.OBSIDIAN) {
            Block above = block.getRelative(0, 1, 0);
            if (above.getType() == Material.AIR) {
                world.playSound(at, Sound.ITEM_FLINTANDSTEEL_USE, 1.0F, 0.9F);
                world.spawnParticle(Particle.FLAME, above.getLocation().add(0.5, 0.5, 0.5),
                        20, 0.4D, 0.4D, 0.4D, 0.06D);
                above.setType(Material.FIRE);
                return true;
            }
        }

        // Snow and ice give way.
        if (type == Material.SNOW || type == Material.SNOW_BLOCK
                || type == Material.ICE || type == Material.POWDER_SNOW) {
            block.setType(type == Material.ICE ? Material.WATER : Material.AIR);
            world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.9F, 1.3F);
            world.spawnParticle(Particle.CLOUD, at, 12, 0.3D, 0.3D, 0.3D, 0.02D);
            return true;
        }

        return false;
    }

    /**
     * Fire's passive: the mage does not burn.
     *
     * <p>Thematically obvious, and it solves a real problem - half of fire's
     * kit sets the ground alight or detonates at arm's length, so a fire mage
     * without this spends the fight setting themselves on fire. Also clears
     * burning outright, so walking out of your own wall is survivable.</p>
     *
     * <p>Refreshed on a short timer rather than granted once: an effect with a
     * long duration would linger after the focus is holstered, which would make
     * the passive worth keeping by swapping items mid-fight.</p>
     */
    @Override
    public void passive(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false, false));
        if (player.getFireTicks() > 0) {
            player.setFireTicks(0);
        }
    }

    @Override
    public void clearPassive(Player player) {
        // Only strip what this school granted. A short duration means a potion
        // the player drank themselves is gone in two seconds anyway, so the
        // check is about not stealing a longer one.
        PotionEffect existing = player.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
        if (existing != null && existing.getDuration() <= 40) {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }

    @Override
    public String passiveName() {
        return "вогонь не шкодить";
    }

    @Override
    public void aura(Player player, double manaFraction, int tick) {
        Location base = player.getLocation();
        World world = base.getWorld();

        double radius = 0.85D + 0.08D * Math.sin(tick * 0.15D);
        Color tint = manaFraction > 0.6D ? EMBER_HOT
                : manaFraction > 0.3D ? EMBER_MID : EMBER_LOW;

        for (int i = 0; i < 3; i++) {
            double angle = tick * 0.18D + Math.PI * 2 * i / 3;
            world.spawnParticle(Particle.DUST, base.clone().add(
                            Math.cos(angle) * radius, 0.06D, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, new Particle.DustOptions(tint, 0.65F));
        }
        if (tick % 6 == 0 && manaFraction > 0.15D) {
            world.spawnParticle(Particle.SMALL_FLAME,
                    base.clone().add(0, 0.7D, 0), 1, 0.45D, 0.12D, 0.45D, 0.003D);
        }
    }

    @Override
    public void chargeGlow(Player player, double power, int tick) {
        Location hand = handPoint(player);
        World world = hand.getWorld();

        double size = 0.09D + power * 0.28D;
        Particle flame = power >= 0.9D ? Particle.COPPER_FIRE_FLAME
                : power >= 0.7D ? Particle.SOUL_FIRE_FLAME : Particle.FLAME;
        Color tint = power >= 0.9D ? VERDANT : power >= 0.7D ? SOUL : EMBER_HOT;

        world.spawnParticle(flame, hand, 1 + (int) (power * 3), size, size, size, 0.003D);
        world.spawnParticle(Particle.DUST, hand, 1 + (int) (power * 3),
                size, size, size, 0.0D,
                new Particle.DustOptions(tint, (float) (0.6D + power * 0.8D)));

        if (power > 0.3D) {
            double angle = tick * 0.5D;
            double r = 0.5D * (1.0D - power);
            world.spawnParticle(Particle.SMALL_FLAME,
                    hand.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r),
                    0, 0, 0, 0, 1.0D);
        }
    }

    // -------------------------------------------------------------- helpers

    /**
     * A rough sphere of cubes.
     *
     * <p>Points are spread with a Fibonacci lattice, which distributes them far
     * more evenly over a sphere than nested latitude/longitude loops - those
     * bunch up at the poles and the clumping is obvious once the body spins.</p>
     */
    private Model.Group sphere(Location at, double radius, double cube) {
        Model.Group group = Model.group(plugin, at);

        final int points = 26;
        final double golden = Math.PI * (3.0D - Math.sqrt(5.0D));

        for (int i = 0; i < points; i++) {
            double y = 1.0D - (i / (double) (points - 1)) * 2.0D;
            double ring = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double theta = golden * i;

            double x = Math.cos(theta) * ring;
            double z = Math.sin(theta) * ring;

            // The molten core glows; the crust does not, so the rock reads as
            // rock with fire inside rather than one uniform blob.
            boolean hot = i % 4 == 0;
            group.with(Model.piece(hot
                            ? Material.MAGMA_BLOCK.createBlockData()
                            : Material.NETHERRACK.createBlockData())
                    .at(x * radius, y * radius, z * radius)
                    .size(cube, cube, cube)
                    .spin(theta)
                    .tilt(y)
                    .glow(hot ? EMBER_MID : null));
        }

        // A bright heart, visible through the gaps between crust cubes.
        group.with(Model.piece(Material.SHROOMLIGHT.createBlockData())
                .at(0, 0, 0).size(radius * 1.1D, radius * 1.1D, radius * 1.1D)
                .glow(EMBER_CORE));

        return group.grow(3);
    }

    /** Ignites a target, and lights a creeper's fuse deliberately. */
    private void burn(LivingEntity target, int ticks) {
        burnFor(target, ticks);
    }

    private void burnFor(LivingEntity target, int ticks) {
        target.setFireTicks(Math.max(target.getFireTicks(), ticks));
        primeCreeper(target);
    }

    /**
     * Fire lights a creeper's fuse on the spot.
     *
     * <p>A fire mage setting one off is the flavour, not an accident - so the
     * fuse is short and the countdown is visible, rather than the creeper
     * wandering over and choosing its own moment.</p>
     */
    private void primeCreeper(LivingEntity target) {
        if (!(target instanceof org.bukkit.entity.Creeper creeper) || creeper.isIgnited()) {
            return;
        }
        World world = creeper.getWorld();
        creeper.setMaxFuseTicks(CREEPER_FUSE);
        creeper.setFuseTicks(0);
        creeper.ignite();

        world.playSound(creeper.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.3F, 1.1F);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= CREEPER_FUSE || creeper.isDead()) {
                    cancel();
                    return;
                }
                double t = (double) ticks / CREEPER_FUSE;
                Fx.ring(world, creeper.getLocation().add(0, 0.3D, 0),
                        2.4D * (1.0D - t) + 0.6D,
                        t > 0.6D ? EMBER_CORE : EMBER_LOW, 1.1F, 14, ticks * 0.3D);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    /** Both schools land softly - falling is never the punishment. */
    static void softLanding(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, 90, 0, false, false, false));
    }

    /** Roughly where the hand sits in first person - clear of the crosshair. */
    static Location handPoint(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector side = new Vector(-look.getZ(), 0, look.getX()).normalize();
        return eye.clone()
                .add(look.clone().multiply(0.8D))
                .add(side.multiply(-0.5D))
                .add(0, -0.45D, 0);
    }
}
