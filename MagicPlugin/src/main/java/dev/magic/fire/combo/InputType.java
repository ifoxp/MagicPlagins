package dev.magic.fire.combo;

/**
 * The alphabet of inputs the combo system understands.
 *
 * <p>Since Paper 26.x, {@link org.bukkit.Input} exposes the movement keys
 * directly, so W/A/S/D/Space/Shift are separately observable rather than
 * inferred from motion. Movement spells are triggered by <em>double taps</em> of
 * a direction rather than by a Shift modifier: a modifier chord forces the
 * player to hold a key that also means "crouch", and it collides with every
 * other Shift gesture. A double tap is unambiguous and reads as a deliberate
 * burst - it is what action games use for dodges.</p>
 */
public enum InputType {
    /** W tapped twice. */
    TAP_FORWARD("W W"),
    /** S tapped twice. */
    TAP_BACKWARD("S S"),
    /** A tapped twice. */
    TAP_LEFT("A A"),
    /** D tapped twice. */
    TAP_RIGHT("D D"),
    /** Space tapped twice. */
    TAP_JUMP("Space Space"),

    /** Shift pressed. */
    SNEAK_DOWN("Shift↓"),
    /** Shift released. */
    SNEAK_UP("Shift↑"),

    /** Left click / attack swing. */
    LEFT_CLICK("ЛКМ"),
    /** Right click while already in the stance - a gesture, not the toggle. */
    RIGHT_CLICK("ПКМ"),
    /** Q. */
    DROP("Q"),
    /** F. */
    SWAP_HAND("F"),
    /** Sharp upward flick of the camera. */
    LOOK_UP("Погляд↑"),
    /** Sharp downward flick of the camera. */
    LOOK_DOWN("Погляд↓");

    private final String display;

    InputType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
