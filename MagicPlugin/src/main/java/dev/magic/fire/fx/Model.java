package dev.magic.fire.fx;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-display models: real geometry built from blocks, without touching the
 * world.
 *
 * <p>Everything here is a {@link BlockDisplay}, so nothing can be mined, nothing
 * griefs terrain, and a server stop cleans up. Growth and fade use
 * {@link Display#setInterpolationDuration}, which makes the <em>client</em> tween
 * the transform - smooth animation without a packet every tick.</p>
 */
public final class Model {

    private Model() {
    }

    /** Builder for one display entity. */
    public static final class Piece {
        private final BlockData block;
        private Vector3f offset = new Vector3f();
        private Vector3f scale = new Vector3f(1, 1, 1);
        private float yaw = 0.0F;
        private float roll = 0.0F;
        private int glow = -1;
        private boolean bright = true;

        private Piece(BlockData block) {
            this.block = block;
        }

        public Piece at(double x, double y, double z) {
            this.offset = new Vector3f((float) x, (float) y, (float) z);
            return this;
        }

        public Piece size(double x, double y, double z) {
            this.scale = new Vector3f((float) x, (float) y, (float) z);
            return this;
        }

        public Piece spin(double radians) {
            this.yaw = (float) radians;
            return this;
        }

        public Piece tilt(double radians) {
            this.roll = (float) radians;
            return this;
        }

        /** Glow outline colour, or -1 for none. */
        public Piece glow(org.bukkit.Color colour) {
            this.glow = colour == null ? -1 : colour.asRGB();
            return this;
        }

        public Piece dim() {
            this.bright = false;
            return this;
        }
    }

    public static Piece piece(BlockData block) {
        return new Piece(block);
    }

    /**
     * A group of pieces that grows in, holds, then fades out together.
     *
     * <p>Used for every large construct - a meteor, a glacier, a spike - so all
     * of them animate the same way and clean themselves up identically.</p>
     */
    public static final class Group {

        private final Plugin plugin;
        private final List<BlockDisplay> parts = new ArrayList<>();
        private final List<Piece> specs = new ArrayList<>();
        private Location anchor;

        private Group(Plugin plugin, Location anchor) {
            this.plugin = plugin;
            this.anchor = anchor;
        }

        public Group with(Piece piece) {
            specs.add(piece);
            return this;
        }

        /** Spawns the pieces collapsed, then grows them over {@code growTicks}. */
        public Group grow(int growTicks) {
            World world = anchor.getWorld();
            for (Piece spec : specs) {
                BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
                    d.setBlock(spec.block);
                    d.setViewRange(2.0F);
                    if (spec.bright) {
                        // Constructs should read clearly in a cave, not go black.
                        d.setBrightness(new Display.Brightness(13, 15));
                    }
                    if (spec.glow >= 0) {
                        d.setGlowColorOverride(org.bukkit.Color.fromRGB(spec.glow));
                    }
                    d.setTransformation(transform(spec, 0.02F));
                });
                parts.add(display);
            }
            // Second pass, so the client sees the collapsed state first.
            for (int i = 0; i < parts.size(); i++) {
                BlockDisplay display = parts.get(i);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(growTicks);
                display.setTransformation(transform(specs.get(i), 1.0F));
            }
            return this;
        }

        /** Moves the whole group, keeping its shape. */
        public void moveTo(Location where) {
            this.anchor = where;
            for (BlockDisplay part : parts) {
                if (!part.isDead()) {
                    part.teleport(where);
                }
            }
        }

        /** Re-spins every piece - cheap way to make a construct tumble. */
        public void respin(double extraYaw, double extraTilt) {
            for (int i = 0; i < parts.size(); i++) {
                BlockDisplay part = parts.get(i);
                if (part.isDead()) {
                    continue;
                }
                Piece spec = specs.get(i);
                part.setInterpolationDelay(0);
                part.setInterpolationDuration(2);
                part.setTransformation(transform(spec, 1.0F,
                        spec.yaw + (float) extraYaw, spec.roll + (float) extraTilt));
            }
        }

        /** Collapses and removes after {@code lifeTicks}. */
        public Group fadeAfter(int lifeTicks, int fadeTicks) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < parts.size(); i++) {
                        BlockDisplay part = parts.get(i);
                        if (part.isDead()) {
                            continue;
                        }
                        part.setInterpolationDelay(0);
                        part.setInterpolationDuration(fadeTicks);
                        part.setTransformation(transform(specs.get(i), 0.02F));
                    }
                    // Remove only once the client has finished the tween.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            remove();
                        }
                    }.runTaskLater(plugin, fadeTicks + 2L);
                }
            }.runTaskLater(plugin, lifeTicks);
            return this;
        }

        public void remove() {
            for (BlockDisplay part : parts) {
                if (!part.isDead()) {
                    part.remove();
                }
            }
            parts.clear();
        }

        public Location anchor() {
            return anchor;
        }
    }

    /**
     * A single display that can be moved and re-oriented independently.
     *
     * <p>{@link Group} teleports all its pieces together, which is right for a
     * rigid construct like a meteor. Orbiting bodies each need their own
     * position, so they get their own handle instead.</p>
     */
    public static final class Body {

        private final BlockDisplay display;
        private final Piece spec;

        /** Where the entity itself sits; the orbit is an offset from this. */
        private Location origin;

        private Body(BlockDisplay display, Piece spec, Location origin) {
            this.display = display;
            this.spec = spec;
            this.origin = origin;
        }

        /**
         * Moves the body relative to its origin, in one interpolated step.
         *
         * <p>This is the whole trick behind smooth display animation: entity
         * <em>position</em> is not interpolated - a teleport is a hard jump
         * every tick - but {@link Transformation} is. So the orbit lives in the
         * transform's translation, and the client tweens the arc at its own
         * frame rate instead of stuttering at 20 Hz.</p>
         *
         * @param ticks how long the client should take; give it at least as
         *              many ticks as the interval between calls, or each update
         *              cuts the previous tween short and the motion judders
         */
        public void glideTo(double dx, double dy, double dz,
                            double yaw, double tilt, int ticks) {
            if (display.isDead()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(transform(spec, 1.0F,
                    (float) yaw, (float) tilt, (float) dx, (float) dy, (float) dz));
        }

        /**
         * Re-anchors the entity, but only if it has drifted meaningfully.
         *
         * <p>A teleport resets any running interpolation, so doing it on the
         * same tick as {@link #glideTo} destroys the tween - which is exactly
         * what made orbiting bodies judder. Skipping the move while the anchor
         * is effectively unchanged means a standing caster never interrupts the
         * animation at all, and a running one only nudges it occasionally.</p>
         *
         * @return true if the entity actually moved
         */
        public boolean reanchor(Location where) {
            if (display.isDead()) {
                return false;
            }
            // Half a block of slack: below that the visual difference is
            // invisible but the interpolation reset is not.
            if (origin.getWorld().equals(where.getWorld())
                    && origin.distanceSquared(where) < 0.25D) {
                return false;
            }
            this.origin = where.clone();
            display.teleport(where);
            return true;
        }

        /** Offset from the anchor to a world point, for follow-the-caster effects. */
        public double[] offsetTo(Location target) {
            return new double[] {
                    target.getX() - origin.getX(),
                    target.getY() - origin.getY(),
                    target.getZ() - origin.getZ()};
        }

        /** Re-orients in place; the client tweens over {@code ticks}. */
        public void orient(double yaw, double tilt, int ticks) {
            if (display.isDead()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(transform(spec, 1.0F,
                    (float) yaw, (float) tilt));
        }

        /** Collapses to nothing over {@code ticks}, then removes itself. */
        public void fade(Plugin plugin, int ticks) {
            if (display.isDead()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(transform(spec, 0.02F));
            new BukkitRunnable() {
                @Override
                public void run() {
                    remove();
                }
            }.runTaskLater(plugin, ticks + 2L);
        }

        public void remove() {
            if (!display.isDead()) {
                display.remove();
            }
        }

        public Location origin() {
            return origin;
        }

        public boolean isAlive() {
            return !display.isDead();
        }
    }

    /** Spawns one free-standing display, grown in over {@code growTicks}. */
    public static Body body(Plugin plugin, Location at, Piece spec, int growTicks) {
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(spec.block);
            d.setViewRange(2.0F);
            if (spec.bright) {
                d.setBrightness(new Display.Brightness(13, 15));
            }
            if (spec.glow >= 0) {
                d.setGlowColorOverride(org.bukkit.Color.fromRGB(spec.glow));
            }
            d.setTransformation(transform(spec, 0.02F));
        });
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(growTicks);
        display.setTransformation(transform(spec, 1.0F));
        return new Body(display, spec, at.clone());
    }

    public static Group group(Plugin plugin, Location anchor) {
        return new Group(plugin, anchor);
    }

    // ---------------------------------------------------------------- internals

    private static Transformation transform(Piece spec, float factor) {
        return transform(spec, factor, spec.yaw, spec.roll);
    }

    private static Transformation transform(Piece spec, float factor,
                                            float yaw, float roll) {
        return transform(spec, factor, yaw, roll, 0.0F, 0.0F, 0.0F);
    }

    private static Transformation transform(Piece spec, float factor,
                                            float yaw, float roll,
                                            float dx, float dy, float dz) {
        Vector3f scale = new Vector3f(
                spec.scale.x * factor, spec.scale.y * factor, spec.scale.z * factor);
        // Offsets are centres, so shift by half the scale to keep pieces put.
        Vector3f translation = new Vector3f(
                spec.offset.x + dx - scale.x / 2,
                spec.offset.y + dy,
                spec.offset.z + dz - scale.z / 2);

        return new Transformation(
                translation,
                new AxisAngle4f(yaw, 0.0F, 1.0F, 0.0F),
                scale,
                new AxisAngle4f(roll, 1.0F, 0.0F, 0.3F));
    }
}
