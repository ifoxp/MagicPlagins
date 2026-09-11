package dev.magic.fire.school;

import dev.magic.fire.element.Element;
import dev.magic.fire.fx.Fx;
import dev.magic.fire.fx.Model;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two-phase wall, shared by both schools.
 *
 * <p>Cast once and it stands, blocking a lane. Cast again while it still burns -
 * without releasing Shift - and it sets off in whatever direction the caster is
 * now looking, sweeping everything ahead of it. Holding it is a choice; so is
 * spending it.</p>
 *
 * <p>The manager owns the per-player state, so neither school has to track a
 * wall reference and the launch path is identical for both.</p>
 */
public final class WallManager {

    private static final double HALF_WIDTH = 2.5D;
    private static final double TRAVEL = 16.0D;
    private static final double SPEED = 0.42D;
    private static final double SPREAD_PER_BLOCK = 0.06D;

    private final Plugin plugin;
    private final Map<UUID, Wall> walls = new HashMap<>();

    public WallManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Places a wall for the caster, replacing any it still had standing. */
    public void place(Player player, School school) {
        Wall existing = walls.get(player.getUniqueId());
        if (existing != null) {
            existing.finish();
        }
        Wall wall = new Wall(player, school);
        walls.put(player.getUniqueId(), wall);
        wall.start();
    }

    /**
     * Sends the caster's standing wall forward.
     *
     * @return true if there was one to launch
     */
    public boolean launch(Player player) {
        Wall wall = walls.get(player.getUniqueId());
        if (wall == null || !wall.canLaunch()) {
            return false;
        }
        wall.launch();
        walls.remove(player.getUniqueId());
        return true;
    }

    /** True when the caster has a wall standing that could still be launched. */
    public boolean hasStanding(Player player) {
        Wall wall = walls.get(player.getUniqueId());
        return wall != null && wall.canLaunch();
    }

    public void forget(Player player) {
        Wall wall = walls.remove(player.getUniqueId());
        if (wall != null) {
            wall.finish();
        }
    }

    public void clear() {
        walls.values().forEach(Wall::finish);
        walls.clear();
    }

    // -------------------------------------------------------------------- wall

    private final class Wall {

        private final Player caster;
        private final School school;
        private final boolean fire;

        private Location base;
        private Vector facing;
        private Model.Group body;

        private boolean launched = false;
        private boolean done = false;
        private double travelled = 0.0D;
        private int ticksLeft;

        Wall(Player caster, School school) {
            this.caster = caster;
            this.school = school;
            this.fire = school.element() == Element.FIRE;
            this.facing = Fx.look(caster);
            // Placed in front of the caster, then settled onto whatever floor
            // is actually there. Anchoring straight to the caster's feet left a
            // wall cast in mid-air hanging at head height, and settle() then
            // hauled it down the moment it launched.
            Location ahead = caster.getLocation().add(facing.clone().multiply(2.6D));
            this.base = Fx.ground(ahead.clone().add(0, 1, 0));
            this.ticksLeft = fire ? 160 : 200;
        }

        void start() {
            World world = base.getWorld();
            if (fire) {
                world.playSound(base, Sound.ITEM_FIRECHARGE_USE, 1.3F, 0.7F);
                world.playSound(base, Sound.BLOCK_FIRE_AMBIENT, 1.5F, 0.8F);
            } else {
                world.playSound(base, Sound.BLOCK_GLASS_PLACE, 1.5F, 0.5F);
                world.playSound(base, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0F, 0.9F);
            }

            // Ice builds real geometry; fire is a sheet of flame particles.
            if (!fire) {
                buildIceBody();
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (done || !caster.isOnline()) {
                        cancel();
                        return;
                    }
                    if (launched ? !tickRolling() : !tickStanding()) {
                        finish();
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void buildIceBody() {
            Vector side = Fx.right(facing);
            Model.Group group = Model.group(plugin, base);
            for (double offset = -HALF_WIDTH; offset <= HALF_WIDTH; offset += 0.9D) {
                // Tall in the middle, tapering at the edges.
                double height = 2.8D - Math.abs(offset) * 0.28D;
                Vector o = side.clone().multiply(offset);
                group.with(Model.piece(Material.PACKED_ICE.createBlockData())
                        .at(o.getX(), 0, o.getZ())
                        .size(0.75D, height, 0.5D)
                        .tilt((Math.random() - 0.5D) * 0.12D)
                        .glow(Color.fromRGB(0xA8, 0xEC, 0xFF)));
            }
            body = group.grow(5);
        }

        void launch() {
            if (launched || done) {
                return;
            }
            launched = true;
            travelled = 0.0D;

            // Re-aim at launch time, so the player picks the direction now
            // rather than being locked to where they placed it. Horizontal
            // only - a wall is a ground effect, it does not fly up at the sky.
            facing = Fx.look(caster);
            base = Fx.ground(base.clone().add(0, 1, 0));

            World world = base.getWorld();
            world.playSound(base, fire
                    ? Sound.ENTITY_BLAZE_SHOOT
                    : Sound.BLOCK_GLASS_BREAK, 1.5F, 0.6F);
            world.playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.7F, 1.5F);
        }

        boolean canLaunch() {
            return !launched && !done;
        }

        // ---------------------------------------------------------- phases

        private boolean tickStanding() {
            if (ticksLeft-- <= 0) {
                return false;
            }
            draw(HALF_WIDTH, 2.4D, ticksLeft);

            if (ticksLeft % 10 == 0) {
                Fx.areaDamage(caster, base, HALF_WIDTH + 0.7D,
                        fire ? 3.0D : 2.0D, this::onTouch);
            }
            return true;
        }

        private boolean tickRolling() {
            if (travelled >= TRAVEL) {
                return false;
            }

            base = base.clone().add(facing.clone().multiply(SPEED));
            travelled += SPEED;
            base = settle(base);

            if (body != null) {
                body.moveTo(base);
            }

            double halfWidth = HALF_WIDTH + travelled * SPREAD_PER_BLOCK;
            draw(halfWidth, 2.8D, (int) (travelled * 4));

            // A moving wall hits on contact, not on a slow beat.
            Fx.areaDamage(caster, base, halfWidth + 0.8D, 0, (target, falloff) -> {
                // One hit per pass; the no-damage window does the bookkeeping.
                if (target.getNoDamageTicks() > 0) {
                    return;
                }
                Fx.hit(caster, target, fire ? 6.0D : 4.0D);
                onTouch(target, falloff);
                target.setVelocity(facing.clone().multiply(0.85D).setY(0.4D));
            });
            return true;
        }

        private void onTouch(LivingEntity target, double falloff) {
            if (fire) {
                target.setFireTicks(Math.max(target.getFireTicks(), 100));
            } else {
                IceSchool.chill(target, 80, 2);
                IceSchool.freeze(target, 40);
            }
        }

        // --------------------------------------------------------- drawing

        private void draw(double halfWidth, double height, int phase) {
            World world = base.getWorld();
            Vector side = Fx.right(facing);
            Particle flame = fire
                    ? (launched ? Particle.SOUL_FIRE_FLAME : Particle.FLAME)
                    : Particle.SNOWFLAKE;

            for (double offset = -halfWidth; offset <= halfWidth; offset += 0.5D) {
                Location column = base.clone().add(side.clone().multiply(offset));
                // Ripple, so the sheet looks alive.
                double h = height + 0.35D * Math.sin(phase * 0.3D + offset);
                for (double y = 0; y < h; y += 0.4D) {
                    Location p = column.clone().add(0, y, 0);
                    world.spawnParticle(flame, p, 0, 0, 0.06D, 0, 1.0D);
                    if (y < 0.5D && fire) {
                        world.spawnParticle(Particle.LAVA, p, 0, 0, 0, 0, 0);
                    }
                }
                if (launched) {
                    world.spawnParticle(
                            fire ? Particle.SMALL_FLAME : Particle.ITEM_SNOWBALL,
                            column.clone().add(facing.clone().multiply(-0.6D))
                                    .add(0, 0.3D, 0),
                            1, 0.1D, 0.2D, 0.1D, 0.01D);
                }
            }
        }

        void finish() {
            if (done) {
                return;
            }
            done = true;
            World world = base.getWorld();
            world.playSound(base, fire
                    ? Sound.BLOCK_FIRE_EXTINGUISH
                    : Sound.BLOCK_GLASS_BREAK, 1.0F, 0.9F);
            world.spawnParticle(fire ? Particle.LARGE_SMOKE : Particle.SNOWFLAKE,
                    base.clone().add(0, 1.0D, 0), 30, HALF_WIDTH, 0.8D, 0.5D, 0.02D);
            if (body != null) {
                body.remove();
                body = null;
            }
        }

        /**
         * Keeps a rolling wall on the surface: steps up over a rise and down
         * into a dip, rather than clipping through the ground or floating.
         */
        private Location settle(Location at) {
            Location probe = at.clone();
            for (int up = 0; up < 2 && probe.getBlock().getType().isSolid(); up++) {
                probe.add(0, 1, 0);
            }
            for (int down = 0; down < 3; down++) {
                Location below = probe.clone().subtract(0, 1, 0);
                if (below.getBlock().getType().isSolid()) {
                    break;
                }
                probe = below;
            }
            return probe;
        }
    }
}
