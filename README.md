**English** · [Українська](README.uk.md)

# MagicPlugin — gesture-driven fire magic

A Paper **26.2** plugin. The client is vanilla Minecraft — players install
nothing.

## Quick start

```
cd MagicPlugin
gradlew build          # builds and copies the jar into ../Server_26.2/plugins
cd ../Server_26.2
start.bat
```

In game: `/magic` gives you the "Fire focus" item and lists the gestures.

## Controls

Hold **right mouse button** with the focus in hand — that is the combat
stance. While it is held:

| Gesture | Ability | Mana |
|---|---|---|
| `Space` `Space` | Fire jump | 18 |
| `W` `W` (sprint) | Fire dash | 14 |
| `W` `W` + `LMB` | Dash with strike (7 damage + ignite) | 26 |

Mana drains at 4/sec while the stance is held, plus the ability's cost.
Regeneration of 8/sec starts 1.5s after releasing the button. Hitting zero
forces you out of the stance and applies 3s of exhaustion.

## How it works

### Detecting a held right-click

A vanilla server sees right-click as a **one-shot event** — holding is not
transmitted. The exception is items with a use animation. So the focus is
given a `consumable` data component with the `BLOCK` animation and a duration
of 3600s:

```java
stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
        .consumeSeconds(3600.0F)
        .animation(ItemUseAnimation.BLOCK)
        .hasConsumeParticles(false)
        .build());
```

After that, `player.isHandRaised()` reports the real button state every tick —
without the side effects of a shield, which would block damage, slow the
player and be disabled by an axe.

The focus is identified through `PersistentDataContainer` rather than
`CustomModelData`, so a player cannot forge the item.

### The input alphabet

WASD is **not sent to the server**. `InputType` contains only what is actually
observable:

| Symbol | Source |
|---|---|
| `LEFT_CLICK` | `PlayerAnimationEvent` (ARM_SWING) |
| `SNEAK_DOWN` / `SNEAK_UP` | `PlayerToggleSneakEvent` |
| `JUMP` | `PlayerJumpEvent` (Paper only) |
| `SPRINT_ON` / `SPRINT_OFF` | `PlayerToggleSprintEvent` |
| `DROP` | `PlayerDropItemEvent` (cancelled while in stance) |
| `SWAP_HAND` | `PlayerSwapHandItemsEvent` (cancelled) |
| `LOOK_UP` / `LOOK_DOWN` | pitch delta ≥ 30° |

**`W`+`W` is `SPRINT_ON`.** Double-tapping W *is* toggling sprint — that is
exactly what it looks like from the server's side.

### Resolving combo conflicts

`W W` and `W W + LMB` share a prefix. If the dash fired immediately, the
strike variant would be unreachable. So `MagicListener` defers resolution by
5 ticks, and `ComboMatcher` returns the **longest** match.

The buffer window is 1200ms, with up to 600ms between inputs. The windows are
deliberately generous: inputs arrive with network latency, and tight timings
simply would not work above ~100ms ping.

## Adventure 5

26.2 brought Adventure 5, with deprecated APIs removed. What is used here:

- `BossBar.bossBar(...)` + `bar.progress(float)` — the old percent methods are gone
- `BossBar.MAX_PROGRESS` instead of numeric constants
- `TextColor.color(0xRRGGBB)` — `Style.of(...)` no longer exists
- `Component` throughout, no legacy `§` strings
- `player.sendActionBar(Component)`, `player.showTitle(Title.title(...))`
- `DataComponentTypes.ITEM_NAME` / `LORE` instead of `ItemMeta.setDisplayName`

The gradient in `MagicHud` is built character by character through `TextColor`
interpolation — cheap, and it reads equally well in titles and in chat.

## Java 25

Paper 26.x **requires Java 25** — not 21, not 24. The Gradle toolchain
downloads the right JDK itself via the foojay resolver; `start.bat` uses the
same JDK from `~/.gradle/jdks/`.

## Structure

```
combo/     InputType, InputBuffer (ring buffer), Combo, ComboMatcher
element/   (reserved for four elements)
ability/   FireAbilities — jump, dash, dash with strike
mana/      ManaPool — drain, regeneration with a pause, exhaustion
ui/        MagicHud — BossBar, ActionBar, titles, gradients
item/      FocusItem — consumable component, PDC marker
```

## Adding an ability

1. Register the pattern in `MagicPlugin.onEnable()`:
   ```java
   matcher.register(Combo.of("fire_wall", 700L,
           InputType.SNEAK_DOWN, InputType.LEFT_CLICK));
   ```
2. Add the cost and the call in `MagicPlugin.fire()`.
3. Write the method in `FireAbilities`.

## Next

- Stances: `getItemInUse()` already gives the held item → fists / sword / staff
- The remaining elements: `element/` plus a switch, e.g. `Q` while in stance
- A resource pack for a custom focus model

---

**Chekaliuk Dmytro** · [@ifoxp](https://github.com/ifoxp) ·
[ifoxp.top](https://ifoxp.top) · Telegram [@ifoxp](https://t.me/ifoxp) ·
Discord `ifoxp`
