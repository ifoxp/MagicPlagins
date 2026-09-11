package dev.magic.fire.spell;

import dev.magic.fire.combo.InputType;
import dev.magic.fire.element.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One school's spells, and the matching of gestures onto them.
 *
 * <p>Each school owns its own book, so the two no longer have to share a single
 * gesture table - a fire-only spell and an ice-only spell can sit on patterns
 * that make sense for each, rather than being forced onto the same key just
 * because the other school had something there.</p>
 */
public final class SpellBook {

    private final Element element;
    private final List<SpellDef> spells = new ArrayList<>();

    public SpellBook(Element element) {
        this.element = element;
    }

    public Element element() {
        return element;
    }

    /** Declares a spell. Everything about it lives in this one call. */
    public SpellBook add(String id, String title, int mana, long cooldownMs,
                         double damage, double range, int duration,
                         SpellEffect effect, InputType... pattern) {
        spells.add(new SpellDef(id, title, List.of(pattern),
                mana, cooldownMs, damage, range, duration, effect));
        // Longest first, so the most specific pattern is found first.
        spells.sort(Comparator.comparingInt(SpellDef::length).reversed());
        return this;
    }

    /** All spells, shortest pattern first - the order the tooltip wants. */
    public List<SpellDef> listed() {
        return spells.stream()
                .sorted(Comparator.comparingInt(SpellDef::length)
                        .thenComparing(SpellDef::id))
                .toList();
    }

    public Optional<SpellDef> byId(String id) {
        return spells.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    // ---------------------------------------------------------------- matching

    /** The most specific spell whose pattern ends the given input list. */
    public Optional<SpellDef> match(List<InputType> recent) {
        return spells.stream()
                .filter(spell -> endsWith(recent, spell.pattern()))
                .findFirst();
    }

    /**
     * True when the matched pattern is the start of a longer one, so firing now
     * would make the longer spell unreachable.
     *
     * <p>Only the matched pattern is extended - not every suffix of the buffer.
     * Scanning suffixes made spells wait for patterns that shared only a
     * trailing input and could never actually complete.</p>
     */
    public boolean canExtend(SpellDef matched) {
        return spells.stream().anyMatch(other ->
                other.length() > matched.length()
                        && startsWith(other.pattern(), matched.pattern()));
    }

    /**
     * True if appending {@code next} to some suffix of {@code recent} keeps us
     * heading toward a real pattern. Used to decide whether a click belongs to a
     * gesture or to the charge channel.
     */
    public boolean continuesWith(List<InputType> recent, InputType next) {
        for (int from = 0; from < recent.size(); from++) {
            List<InputType> tail = new ArrayList<>(recent.subList(from, recent.size()));
            tail.add(next);
            for (SpellDef spell : spells) {
                if (spell.length() >= tail.size() && startsWith(spell.pattern(), tail)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean endsWith(List<InputType> seq, List<InputType> suffix) {
        if (suffix.isEmpty() || suffix.size() > seq.size()) {
            return false;
        }
        int offset = seq.size() - suffix.size();
        for (int i = 0; i < suffix.size(); i++) {
            if (seq.get(offset + i) != suffix.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(List<InputType> seq, List<InputType> prefix) {
        if (prefix.size() > seq.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (seq.get(i) != prefix.get(i)) {
                return false;
            }
        }
        return true;
    }
}
