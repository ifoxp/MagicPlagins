# MagicPlugin — вогняна магія на жестах

Плагін для Paper **26.2**. Клієнт — чистий ванільний Minecraft, нічого встановлювати не треба.

## Швидкий старт

```
cd MagicPlugin
gradlew build          # збирає і одразу копіює jar у ../Server_26.2/plugins
cd ../Server_26.2
start.bat
```

У грі: `/magic` — видає предмет «Вогняний фокус» і показує список жестів.

## Керування

Тримай **ПКМ** з фокусом у руці — це бойова стійка. Поки вона активна:

| Жест | Здібність | Мана |
|---|---|---|
| `Space` `Space` | Вогняний стрибок | 18 |
| `W` `W` (спринт) | Вогняний ривок | 14 |
| `W` `W` + `ЛКМ` | Ривок з ударом (7 шкоди + підпал) | 26 |

Мана витрачається 4/сек поки стійка тримається, плюс кост здібності.
Регенерація 8/сек починається через 1.5 с після відпускання ПКМ.
Мана на нулі → примусовий вихід зі стійки і 3 с виснаження.

## Як це працює

### Утримування ПКМ

Ванільний сервер бачить ПКМ як **одноразову подію** — утримування не передається.
Виняток: предмети з use-анімацією. Тому фокус отримує data-компонент
`consumable` з анімацією `BLOCK` і тривалістю 3600 с:

```java
stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
        .consumeSeconds(3600.0F)
        .animation(ItemUseAnimation.BLOCK)
        .hasConsumeParticles(false)
        .build());
```

Після цього `player.isHandRaised()` показує справжній стан кнопки кожен тік —
і без побічних ефектів щита (не блокує урон, не замідлює, не дизейблиться сокирою).

Ідентифікація фокуса — через `PersistentDataContainer`, а не `CustomModelData`,
щоб гравець не міг підробити предмет.

### Алфавіт вводів

WASD напряму серверу **не передається**. [`InputType`](src/main/java/dev/magic/fire/combo/InputType.java)
містить тільки те, що справді видно:

| Символ | Джерело |
|---|---|
| `LEFT_CLICK` | `PlayerAnimationEvent` (ARM_SWING) |
| `SNEAK_DOWN` / `SNEAK_UP` | `PlayerToggleSneakEvent` |
| `JUMP` | `PlayerJumpEvent` (тільки Paper) |
| `SPRINT_ON` / `SPRINT_OFF` | `PlayerToggleSprintEvent` |
| `DROP` | `PlayerDropItemEvent` (скасовується у стійці) |
| `SWAP_HAND` | `PlayerSwapHandItemsEvent` (скасовується) |
| `LOOK_UP` / `LOOK_DOWN` | дельта pitch ≥ 30° |

**`W`+`W` = `SPRINT_ON`.** Подвійне W це і є вмикання спринту — саме так воно
виглядає з боку сервера.

### Розв'язання конфліктів комбо

`W W` і `W W + ЛКМ` мають спільний префікс. Якби ривок спрацьовував одразу,
до варіанту з ударом дійти було б неможливо. Тому
[`MagicListener`](src/main/java/dev/magic/fire/MagicListener.java) відкладає
розв'язання на 5 тіків, а [`ComboMatcher`](src/main/java/dev/magic/fire/combo/ComboMatcher.java)
віддає **найдовший** збіг.

Вікно буфера — 1200 мс, пауза між вводами — до 600 мс. Вікна навмисно щедрі:
вводи приходять з сітьовою затримкою, і жорсткі таймінги просто не працювали б
на пінгу вище ~100 мс.

## Adventure 5

26.2 приніс Adventure 5 з вирізаними deprecated API. Що використано тут:

- `BossBar.bossBar(...)` + `bar.progress(float)` — старі percent-методи прибрані
- `BossBar.MAX_PROGRESS` замість числових констант
- `TextColor.color(0xRRGGBB)` — `Style.of(...)` більше немає
- `Component` усюди, без legacy-стрічок з `§`
- `player.sendActionBar(Component)`, `player.showTitle(Title.title(...))`
- `DataComponentTypes.ITEM_NAME` / `LORE` замість `ItemMeta.setDisplayName`

Градієнт у [`MagicHud`](src/main/java/dev/magic/fire/ui/MagicHud.java)
будується посимвольно через інтерполяцію `TextColor` — дешево і читається
однаково добре в титулах і в чаті.

## Java 25

Paper 26.x **вимагає Java 25** (не 21, не 24). Gradle toolchain завантажує
потрібний JDK сам через foojay-resolver; `start.bat` бере той самий JDK з
`~/.gradle/jdks/`.

## Структура

```
combo/     InputType, InputBuffer (ring buffer), Combo, ComboMatcher
element/   (зарезервовано під 4 стихії)
ability/   FireAbilities — стрибок, ривок, ривок з ударом
mana/      ManaPool — дрейн, регена з паузою, виснаження
ui/        MagicHud — BossBar, ActionBar, титули, градієнти
item/      FocusItem — consumable-компонент, PDC-маркер
```

## Як додати здібність

1. Зареєструвати патерн в `MagicPlugin.onEnable()`:
   ```java
   matcher.register(Combo.of("fire_wall", 700L,
           InputType.SNEAK_DOWN, InputType.LEFT_CLICK));
   ```
2. Додати кост і виклик у `MagicPlugin.fire()`.
3. Написати метод у `FireAbilities`.

## Далі

- Стилі: `getItemInUse()` вже дає предмет у руці → руки / меч / посох
- Решта стихій: `element/` + перемикач (наприклад `Q` у стійці)
- Ресурспак для власної моделі фокуса
