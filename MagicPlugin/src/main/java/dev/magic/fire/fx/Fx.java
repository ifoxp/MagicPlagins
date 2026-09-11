package dev.magic.fire.fx;

import org.bukkit.Color;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/**
 * Shared effect helpers.
 *
 * <p>Both schools draw rings, trace projectiles, find the ground and deal
 * area damage. Those used to be duplicated in each ability class, which meant a
 * fix to one copy silently left the other broken - the meteor and comet bugs
 * were both that. They live here once now.</p>
 */
public final class Fx {

    private Fx() {
    }

    // ----------------------------------------------------------------- geometry

    /** First solid block below a location, standing on top of it. */
    public static Location ground(Location from) {
        Location probe = from.clone();
        int floor = from.getWorld().getMinHeight();
        while (probe.getY() > floor && !probe.getBlock().getType().isSolid()) {
            probe.subtract(0, 1, 0);
        }
        return probe.add(0, 1, 0);
    }

    /** Where the player is aiming, projected onto the ground. */
    public static Location aimGround(Player player, double reach) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = eye.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), reach, FluidCollisionMode.NEVER, true);
        Location aimed = hit != null
                ? hit.getHitPosition().toLocation(eye.getWorld())
                : eye.clone().add(eye.getDirection().multiply(reach * 0.6D));
        return ground(aimed);
    }

    /** Horizontal unit vector the player faces. */
    public static Vector look(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        return look.lengthSquared() < 0.001D ? new Vector(1, 0, 0) : look.normalize();
    }

    /**
     * Unit vector to the player's right.
     *
     * <p>In Minecraft's coordinates this cross product points right, not left -
     * getting that backwards is why the sideways dodges were once mirrored.</p>
     */
    public static Vector right(Vector look) {
        return new Vector(-look.getZ(), 0, look.getX());
    }

    // ---------------------------------------------------------------- particles

    /** Flat ring of particles at a fixed radius. */
    public static void ring(World world, Location centre, double radius,
                            Particle particle, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(particle,
                    centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    0, 0, 0.04D, 0, 1.0D);
        }
    }

    /** Coloured ring, for reticles and markers. */
    public static void ring(World world, Location centre, double radius,
                            Color colour, float size, int points, double spin) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points + spin;
            world.spawnParticle(Particle.DUST,
                    centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, new Particle.DustOptions(colour, size));
        }
    }

    /** Ring that races outward along the ground. */
    public static void shockwave(Plugin plugin, World world, Location centre,
                                 double maxRadius, Particle particle, Color tint) {
        new BukkitRunnable() {
            double r = 0.6D;

            @Override
            public void run() {
                if (r > maxRadius) {
                    cancel();
                    return;
                }
                int points = (int) Math.max(10, r * 11);
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2 * i / points;
                    Location p = centre.clone().add(
                            Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    world.spawnParticle(particle, p, 0, 0, 0.06D, 0, 1.0D);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(tint, 1.1F));
                }
                r += 0.7D;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Line of particles between two points. */
    public static void line(World world, Location from, Location to,
                            Particle particle, double density) {
        Vector step = to.toVector().subtract(from.toVector());
        int marks = Math.max(2, (int) (step.length() * density));
        for (int i = 0; i <= marks; i++) {
            Location p = from.clone().add(step.clone().multiply((double) i / marks));
            world.spawnParticle(particle, p, 1, 0.03D, 0.03D, 0.03D, 0);
        }
    }

    /** Short particle trail following a moving player. */
    public static void trail(Plugin plugin, Player player, int ticks,
                             Particle particle, int count, double spread) {
        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (left-- <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 0.4D, 0);
                at.getWorld().spawnParticle(particle, at, count,
                        spread, 0.25D, spread, 0.01D);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // --------------------------------------------------------------- projectile

    /**
     * Traces a projectile forward, calling {@code draw} each step.
     *
     * <p>Both schools fire a charged bolt; only the visuals and the impact
     * differ, so the flight, collision and lifetime logic belong in one
     * place.</p>
     *
     * <p>{@code onHit} runs exactly once - on a block, on a living entity, on
     * running out of range, or if the caster disconnects. That makes it the
     * right place to clean up anything the shot owns.</p>
     */
    public static void projectile(Plugin plugin, Player caster, double speed,
                                  double hitBox, int maxTicks,
                                  Consumer<Location> draw,
                                  Consumer<Location> onHit) {
        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        Vector dir = eye.getDirection().normalize();

        new BukkitRunnable() {
            final Location at = eye.clone().add(dir.clone().multiply(0.9D));
            int lived = 0;

            @Override
            public void run() {
                // Running out of range still counts as an impact. Without this
                // a spent projectile just stopped being updated - and anything
                // it was carrying, like the volley's magma cubes, hung in the
                // air forever because nothing ever cleaned it up.
                if (lived++ > maxTicks) {
                    onHit.accept(at.clone());
                    cancel();
                    return;
                }
                if (!caster.isOnline()) {
                    onHit.accept(at.clone());
                    cancel();
                    return;
                }

                draw.accept(at.clone());

                RayTraceResult hit = world.rayTraceBlocks(
                        at, dir, speed, FluidCollisionMode.NEVER, true);
                if (hit != null) {
                    onHit.accept(hit.getHitPosition().toLocation(world));
                    cancel();
                    return;
                }

                for (Entity entity : world.getNearbyEntities(at, hitBox, hitBox, hitBox)) {
                    if (entity instanceof LivingEntity && !entity.equals(caster)) {
                        onHit.accept(at.clone());
                        cancel();
                        return;
                    }
                }

                at.add(dir.clone().multiply(speed));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Drops something from the sky onto a ground target the caster keeps
     * steering, then calls {@code onImpact}.
     *
     * <p>Shared by the meteor and the comet. The steering, the easing, the
     * lock-in and the substepping that stops it tunnelling through the floor
     * were all duplicated before.</p>
     */
    public static void skyfall(Plugin plugin, Player caster, double startHeight,
                              Consumer<Location> drawFalling,
                              java.util.function.BiConsumer<Location, Boolean> drawMarker,
                              Consumer<Location> onImpact) {
        World world = caster.getWorld();

        new BukkitRunnable() {
            Location mark = aimGround(caster, 60.0D);
            final Location body = mark.clone().add(0, startHeight, 0);
            double speed = 0.7D;
            int tick = 0;
            boolean locked = false;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                tick++;

                // Steer until it is close, then commit - so the target still has
                // a moment to move.
                if (!locked) {
                    if (body.getY() - mark.getY() < 10.0D) {
                        locked = true;
                    } else {
                        Location aimed = aimGround(caster, 60.0D);
                        mark = mark.clone().add(aimed.toVector()
                                .subtract(mark.toVector()).multiply(0.3D));
                        body.setX(mark.getX());
                        body.setZ(mark.getZ());
                    }
                }

                drawMarker.accept(mark.clone(), locked);

                speed = Math.min(2.4D, speed + 0.1D);
                int steps = (int) Math.ceil(speed / 0.4D);
                for (int i = 0; i < steps; i++) {
                    body.subtract(0, speed / steps, 0);
                    drawFalling.accept(body.clone());

                    if (body.getY() <= mark.getY() + 0.4D
                            || body.getBlock().getType().isSolid()) {
                        onImpact.accept(body.clone());
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------- damage

    /**
     * Hits everything living in range, weaker toward the edge.
     *
     * @param onHit extra per-target effect - burn, chill, knockback
     */
    public static void areaDamage(Player caster, Location at, double radius,
                                  double damage,
                                  java.util.function.BiConsumer<LivingEntity, Double> onHit) {
        World world = at.getWorld();
        for (Entity entity : world.getNearbyEntities(at, radius, radius * 0.8D, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(caster)) {
                continue;
            }
            // The edge of a blast should be survivable.
            double falloff = Math.max(0.25D,
                    1.0D - target.getLocation().distance(at) / radius);

            if (damage > 0.0D) {
                hit(caster, target, damage * falloff);
            }
            if (onHit != null) {
                onHit.accept(target, falloff);
            }
        }
    }

    /**
     * Applies spell damage, piercing armour by a configurable fraction.
     *
     * <p>Armour normally soaks up to 80% of a hit, which would make a whole
     * magic school irrelevant against anyone geared. Bypassing it entirely has
     * the opposite problem - armour stops mattering at all - so the hit is split
     * instead: {@code pierce} of it goes through as {@link DamageType#MAGIC},
     * which ignores protection the way an Instant Damage potion does, and the
     * remainder lands as an ordinary attack that armour reduces normally.</p>
     *
     * <p>At 0 the school plays entirely by vanilla rules; at 1 armour is
     * irrelevant; the interesting values are in between. Splitting it this way
     * rather than flipping a flag is what makes a future progression - piercing
     * rising as a mage levels - a matter of changing one number.</p>
     *
     * <p>The caster is recorded as the source either way, so kill credit, death
     * messages and mob aggro all behave normally.</p>
     */
    public static void hit(Player caster, LivingEntity target, double amount) {
        if (amount <= 0.0D) {
            return;
        }

        double piercing = amount * pierce;
        double physical = amount - piercing;

        if (piercing > 0.0D) {
            target.damage(piercing, DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(caster)
                    .withDirectEntity(caster)
                    .build());
        }
        if (physical > 0.0D) {
            // A target already in its no-damage window would swallow this half,
            // so the physical part is only worth applying on its own.
            if (piercing > 0.0D) {
                target.setNoDamageTicks(0);
            }
            target.damage(physical, caster);
        }
    }

    /**
     * Fraction of spell damage that ignores armour, 0..1.
     *
     * <p>Held statically because {@link #areaDamage} runs from dozens of call
     * sites on the hot path; threading a config object through all of them would
     * be noise for no gain.</p>
     */
    private static double pierce = 0.5D;

    public static void setPierce(double fraction) {
        pierce = Math.clamp(fraction, 0.0D, 1.0D);
    }

    public static double pierce() {
        return pierce;
    }

    /** Pushes a target away from a point. */
    public static void knockback(LivingEntity target, Location from,
                                 double strength, double lift) {
        Vector push = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() > 0.001D) {
            target.setVelocity(push.normalize().multiply(strength).setY(lift));
        }
    }
}
