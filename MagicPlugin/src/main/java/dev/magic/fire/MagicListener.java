package dev.magic.fire;

import dev.magic.fire.combo.InputType;
import dev.magic.fire.item.FocusItem;
import dev.magic.fire.school.InfernoForm;
import dev.magic.fire.school.School;
import dev.magic.fire.spell.SpellBook;
import dev.magic.fire.spell.SpellDef;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Optional;

/**
 * Turns client input into spells.
 *
 * <p>Two independent paths, which is what stops casts cutting each other off:</p>
 *
 * <ol>
 *   <li><b>Gestures</b> - an ordered buffer fed by double taps (real key state,
 *       via {@link PlayerInputEvent}), camera flicks, Q, Shift and right
 *       clicks. Resolved against the held school's book.</li>
 *   <li><b>Channel</b> - Shift plus sustained left-clicking. Own state, so
 *       firing a gesture spell mid-channel leaves the channel running.</li>
 * </ol>
 */
public final class MagicListener implements Listener {

    /** Ticks between an input landing and resolution - small, so casts feel immediate. */
    private static final long RESOLVE_DELAY = 2L;

    /** How long a complete-but-extendable pattern waits for the longer one. */
    private static final long EXTEND_WAIT_MS = 260L;

    /** Pitch change that counts as a deliberate camera flick. */
    private static final float FLICK_DEGREES = 28.0F;

    /** Right click can arrive twice in a tick; ignore the echo. */
    private static final long DEBOUNCE_MS = 200L;

    /** How long after a drop a swing is treated as that drop's echo. */
    private static final long SWING_ECHO_MS = 120L;

    /** No right-click repeat for this long means the button was released. */
    private static final long HOLD_LAPSE_MS = 350L;

    private final MagicPlugin plugin;

    public MagicListener(MagicPlugin plugin) {
        this.plugin = plugin;
        startTicker();
    }

    // ---------------------------------------------------------------- inputs

    /** Paper reports real key state, so double taps are detected directly. */
    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        CasterState state = plugin.state(player);

        InputType gesture = state.taps.update(event.getInput());
        if (gesture == null || !state.stanceActive) {
            return;
        }

        // Shift + S leaves the inferno form. Checked before the buffer, so the
        // exit never doubles as a backstep.
        if (gesture == InputType.TAP_BACKWARD && player.isSneaking()
                && plugin.formActive(player)) {
            plugin.formExit(player, "форма скинута");
            return;
        }

        feed(player, gesture);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        // Only the press edge is a gesture; release would double every pattern.
        if (inStance(player) && event.isSneaking()) {
            feed(player, InputType.SNEAK_DOWN);
        }
    }

    /**
     * Left click: the basic attack, the Shift channel, or a gesture.
     *
     * <p>Both uses of the button feed the same charge tracker; Shift only
     * decides <em>what the charge becomes</em> when it is released. Gating the
     * tracker on Shift was a bug - it meant a bare click charged nothing and so
     * fired nothing at all.</p>
     */
    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!inStance(player)) {
            return;
        }

        CasterState state = plugin.state(player);
        SpellBook book = plugin.heldSchool(player).book();

        // A click finishing a pending pattern belongs to that pattern.
        List<InputType> recent = state.inputs.snapshot();
        if (!recent.isEmpty() && book.continuesWith(recent, InputType.LEFT_CLICK)) {
            feed(player, InputType.LEFT_CLICK);
            return;
        }

        // Dropping an item makes the vanilla client send a swing too, so a Q
        // gesture arrived here as a left click and started charging. Ignore
        // swings that land in the same tick as a drop.
        if (System.currentTimeMillis() - state.lastDropAt < SWING_ECHO_MS) {
            return;
        }

        // Otherwise it drives the charge - with or without Shift.
        state.charge.onSwing();
        // Shift at the moment of clicking picks the mode. Sampling it here
        // rather than on release means letting go of Shift mid-volley does not
        // silently switch weapons.
        if (player.isSneaking()) {
            state.channelMode = true;
        }
        state.lastInputAt = System.currentTimeMillis();
    }

    /**
     * Right click is a plain gesture input now.
     *
     * <p>The stance used to be toggled with it, which spent the single most
     * useful button on a mode switch. Holding the focus <em>is</em> the stance,
     * so right click is free for patterns - and that roughly doubles the
     * available gesture vocabulary.</p>
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!FocusItem.isFocus(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);

        CasterState state = plugin.state(player);
        long now = System.currentTimeMillis();

        // A held button repeats the use packet several times a second. Only the
        // first press of a run is a gesture - otherwise a three second hold
        // would stuff a dozen right clicks into the buffer and fire whatever
        // pattern they happened to spell.
        boolean fresh = now - state.lastRightClickAt > HOLD_LAPSE_MS;
        state.lastRightClickAt = now;

        if (fresh) {
            state.rightHeldSince = now;
        }
        if (!fresh) {
            // Mid-hold repeat: it keeps the hold alive, nothing more.
            return;
        }

        // Sneaking on a block is the school's utility - fire lights things,
        // ice freezes them. Checked first so it never eats a gesture.
        if (player.isSneaking() && action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && plugin.heldSchool(player).interact(player, event.getClickedBlock())) {
            return;
        }

        // Launching a standing wall bypasses the gesture buffer. It has to: the
        // buffer cleared when the wall was placed, and Shift is still held, so
        // no second SNEAK_DOWN will arrive to rebuild the pattern.
        if (player.isSneaking() && plugin.walls().launch(player)) {
            state.hud.wallLaunched(player);
            state.lastInputAt = now;
            return;
        }

        feed(player, InputType.RIGHT_CLICK);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!inStance(player)) {
            return;
        }
        // In stance Q is a gesture, not a drop.
        event.setCancelled(true);
        plugin.state(player).lastDropAt = System.currentTimeMillis();
        feed(player, InputType.DROP);
    }

    /**
     * F is a gesture input.
     *
     * <p>It used to close the stance, but with the stance tied to the held item
     * there is nothing to close - so the key is free.</p>
     */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inStance(player)) {
            return;
        }
        event.setCancelled(true);
        feed(player, InputType.SWAP_HAND);
    }

    /** Casting lifts the player deliberately - never punish the landing. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player
                && plugin.state(player).isFallImmune()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }

    // ------------------------------------------------------------ stance loop

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tick(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tick(Player player) {
        CasterState state = plugin.state(player);

        // Keep the grace alive while a cast still has the player airborne.
        if (state.isFallImmune() && !player.isOnGround()) {
            state.refreshFallImmunity();
        }

        // Holding the focus is the stance. No toggle, no timeout - draw the
        // wand and you are ready, put it away and you are not.
        boolean holding = FocusItem.isFocus(player.getInventory().getItemInMainHand());

        if (holding && !state.stanceActive) {
            enterStance(player, state);
            state.hud.stanceOpened(player);
        } else if (!holding && state.stanceActive) {
            // Drop the passive with the stance, so it cannot be kept by
            // holstering the focus at the right moment.
            if (state.lastPassive != null) {
                state.lastPassive.clearPassive(player);
                state.lastPassive = null;
            }
            exitStance(player, state);
            state.hud.stanceClosed(player);
        }

        if (!state.stanceActive) {
            state.mana.regenTick();
            state.hud.updateMana(state.mana.fraction(), false, state.mana.isExhausted());
            return;
        }

        School school = plugin.heldSchool(player);
        // Swapping focus repaints the HUD immediately.
        state.hud.setElement(school.element());

        // Swapping schools mid-stance has to drop the old passive first, or a
        // player could stack both by alternating focuses.
        if (state.lastPassive != school) {
            if (state.lastPassive != null) {
                state.lastPassive.clearPassive(player);
            }
            state.lastPassive = school;
        }
        school.passive(player);

        // Holding the stance is free; only spells cost mana.
        state.mana.stanceRegenTick();

        tickFormCharge(player, state);

        detectFlick(player, state);
        state.stanceTicks++;
        school.aura(player, state.mana.fraction(), state.stanceTicks);

        tickChannel(player, state, school);

        state.hud.updateMana(state.mana.fraction(), true, false);
        state.hud.status(player, gestureText(state), state.charge, school);
    }

    /**
     * Watches a held right click and enters the inferno form at three seconds.
     *
     * <p>Right click only reports its press, never its release, so a hold is
     * inferred: the client repeats the use packet while the button is down, and
     * a gap longer than that repeat means it came up.</p>
     */
    private void tickFormCharge(Player player, CasterState state) {
        if (state.rightHeldSince == 0L) {
            return;
        }
        long now = System.currentTimeMillis();

        // No repeat for a while means the button was released.
        if (now - state.lastRightClickAt > HOLD_LAPSE_MS) {
            state.rightHeldSince = 0L;
            return;
        }
        if (plugin.formActive(player)) {
            state.rightHeldSince = 0L;
            return;
        }

        long held = now - state.rightHeldSince;
        if (held < InfernoForm.CHARGE_MS) {
            // A visible wind-up, so the three seconds are not a mystery.
            double progress = held / (double) InfernoForm.CHARGE_MS;
            plugin.heldSchool(player).chargeGlow(player, progress, state.stanceTicks);
            return;
        }

        state.rightHeldSince = 0L;
        plugin.formEnter(player);
    }

    /**
     * Runs the left-click channel.
     *
     * <p>Two weapons on one button, told apart by whether Shift was held when
     * the clicking started: without it the charge builds a volley and fires on
     * release; with it the clicking is a sustained stream instead.</p>
     */
    private void tickChannel(Player player, CasterState state, School school) {
        if (!state.charge.isActive()) {
            state.channelMode = false;
            return;
        }

        if (!state.charge.tick()) {
            // Clicking stopped: a volley fires, a stream simply ends.
            if (state.channelMode) {
                state.charge.reset();
            } else {
                plugin.bolt(player, state.charge.release());
            }
            state.channelMode = false;
            return;
        }

        if (state.channelMode) {
            if (!plugin.channel(player, state.charge.ticks(), state.charge.intensity())) {
                state.charge.reset();
                state.channelMode = false;
            }
            return;
        }
        school.chargeGlow(player, state.charge.power(), state.charge.ticks());
    }

    private void enterStance(Player player, CasterState state) {
        state.stanceActive = true;
        state.inputs.clear();
        state.charge.reset();
        state.channelMode = false;
        state.taps.clear();
        state.lastPitch = player.getLocation().getPitch();
        state.stanceTicks = 0;
        state.lastInputAt = System.currentTimeMillis();
        state.hud.setElement(plugin.heldSchool(player).element());
        state.hud.show(player);
    }

    private void exitStance(Player player, CasterState state) {
        state.stanceActive = false;
        state.inputs.clear();
        state.charge.reset();
        state.channelMode = false;
        state.taps.clear();
        state.hud.hide(player);
        player.sendActionBar(Component.empty());
    }

    private void detectFlick(Player player, CasterState state) {
        float pitch = player.getLocation().getPitch();
        float delta = pitch - state.lastPitch;
        if (Math.abs(delta) >= FLICK_DEGREES) {
            feed(player, delta < 0 ? InputType.LOOK_UP : InputType.LOOK_DOWN);
            state.lastPitch = pitch;
        } else {
            // Drift toward the current pitch, so small movements do not stack.
            state.lastPitch += delta * 0.35F;
        }
    }

    // ------------------------------------------------------------- resolving

    private boolean inStance(Player player) {
        return plugin.state(player).stanceActive;
    }

    private void feed(Player player, InputType input) {
        if (!inStance(player)) {
            return;
        }
        CasterState state = plugin.state(player);
        state.inputs.push(input);
        state.lastInputAt = System.currentTimeMillis();
        scheduleResolve(player);
    }

    private void scheduleResolve(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                CasterState state = plugin.state(player);
                if (!state.stanceActive || !player.isOnline()) {
                    return;
                }

                List<InputType> recent = state.inputs.snapshot();
                if (recent.isEmpty()) {
                    return;
                }

                SpellBook book = plugin.heldSchool(player).book();
                Optional<SpellDef> hit = book.match(recent);
                if (hit.isEmpty()) {
                    return;
                }

                SpellDef spell = hit.get();
                // If this pattern starts a longer one, wait briefly for the
                // rest - then fire anyway, so a complete gesture is never lost.
                if (book.canExtend(spell)
                        && state.inputs.sinceLastInputMs() < EXTEND_WAIT_MS) {
                    scheduleResolve(player);
                    return;
                }

                if (plugin.cast(player, spell)) {
                    // Only the gesture buffer clears; the channel is untouched.
                    state.inputs.clear();
                }
            }
        }.runTaskLater(plugin, RESOLVE_DELAY);
    }

    private static String gestureText(CasterState state) {
        return String.join(" ", state.inputs.snapshot().stream()
                .map(InputType::display).toList());
    }
}
