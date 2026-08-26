# QShop KubeJS Wiki

QShop is a Forge 1.20.1 server-shop mod with a KubeJS integration. The integration exposes a global `QShop` binding for shop management, currencies, sub-shops, trade entries, random tab refreshes, and trade events.

This is the current Builder-first API. Shop and entry JSON CRUD methods are not exposed on the global binding. Use `.add()` with a stable UUID to create or replace data.

This document describes the public KubeJS API shipped by QShop. Put examples that change server data in `kubejs/server_scripts/`, then run `/reload` when appropriate.

## Requirements

- Minecraft Forge 1.20.1
- QShop
- KubeJS 6 for Forge
- Optional: FTB Quests for quest requirements and the QShop money task/reward integration
- GameStages (`gamestages`) for stage requirements; KubeJS PlayerStages is also supported when available

The KubeJS integration is optional. Without KubeJS, the core shop GUI, commands, currencies, limits, and JSON configuration still work.

## Script context

Use QShop server-side. The normal entry point is a server event:

```js
ServerEvents.loaded(event => {
  // ShopManager is ready here in normal server startup.
  QShop.createShop('vip', 'VIP Shop', 'coins')
})
```

For player actions, use a player event or command:

```js
PlayerEvents.loggedIn(event => {
  QShop.open('starter', event.player)
})
```

Most mutating methods save the shop or currency immediately. Avoid calling them every server tick unless that is intentional. `QShop.reload()` reloads the JSON configuration from disk and should normally be called after external file edits, not after every API mutation.

## The global binding

The plugin registers one global object:

```js
QShop
```

Methods return `true` or `false` when an operation can fail, so scripts should check the result when the shop, tab, currency, or entry may not exist.

### Public method groups

The global binding is intentionally small and Builder-first:

| Group | Methods |
| --- | --- |
| Open/query | `open`, `openByUuid`, `exists`, `getShopIds`, `getShopUuid`, `getShop`, `getTab`, `getEntry`, `getTabCount`, `getEntryCount` |
| Shop/tab/entry writes | `createShop`, `removeShop`, `tab`, `removeTab`, `entry`, `removeEntry` |
| Currency | `getCurrencies`, `createCurrency`, `getBalance`, `giveCurrency`, `takeCurrency`, `setCurrency` |
| Limits/refresh | `clearEntryLimits`, `clearTabLimits`, `clearShopLimits`, `refreshTab`, `reload` |

The old JSON CRUD methods (`addEntry`, `updateEntry`, `addTab`, `updateTab`) and direct `JsonIO` usage are not part of the public global API.

## Reference rules (important)

The API has three different object levels. A reference is always interpreted relative to its parent:

| Name | Accepted values | Meaning |
| --- | --- | --- |
| `shopRef` | shop id string or shop UUID string | Selects the top-level shop. Example: `'vip'`. |
| `tabRef` | zero-based number, tab UUID string, or `null` | Selects a tab inside the shop. `null` means tab `0`. |
| `entryRef` | zero-based number or entry UUID string | Selects an entry inside the selected tab. |

The number is never a shop id. It is only an index at the level where it appears:

```js
// vip is the shop id. There is no tabRef, so count entries in tab 0.
QShop.getEntryCount('vip')

// vip is the shop id. 1 means the second tab. Count entries in that tab.
QShop.getEntryCount('vip', 1)

// vip = shop id, 1 = second tab index, 0 = first entry index in that tab.
const entry = QShop.getEntry('vip', 1, 0)
const uuid = entry ? entry.uuid : null
```

Prefer UUIDs for long-lived scripts because indexes change when tabs or entries are added, removed, refreshed, or reordered:

```js
const shop = QShop.getShop('vip')
const tab = QShop.getTab('vip', 'daily-offers')
const entry = QShop.getEntry('vip', 'daily-offers', 'daily-diamond')

// UUIDs are available on the returned Java objects.
console.log(shop.uuid)
console.log(tab.uuid)
console.log(entry.uuid)

// Equivalent default-tab shortcuts:
const firstTab = QShop.getTab('vip')
const firstEntry = QShop.getEntry('vip', 0)
```

`QShop.getShop`, `QShop.getTab`, and `QShop.getEntry` return the actual Java objects exposed by the plugin, or `null` when the reference is invalid. Their public fields can be read directly from KubeJS. Mutating these objects directly is not supported; use builders so the shop is saved and clients are refreshed.

Useful read-only fields are:

| Object | Fields commonly used in scripts |
| --- | --- |
| `Shop` | `id`, `uuid`, `displayName`, `currency`, `icon`, `tabs` |
| `ShopTab` | `uuid`, `name`, `icon`, `description`, `entries`, `requiredQuests`, `requiredStages` |
| `ShopEntry` | `uuid`, `type`, `displayName`, `description`, `item`, `displayItem`, `give`, `receive`, `currencyId`, `price`, `globalLimit`, `playerLimit`, `reset`, `commands`, `requiredQuests`, `requiredStages`, `count` |

For example:

```js
const tab = QShop.getTab('vip', 0)
if (tab) {
  console.log(`${tab.name}: ${tab.entries.length} entries`)
}
```

## Item values

All Builder item arguments accept the following forms:

```js
// Item id
'minecraft:diamond'

// Item object with count and SNBT
{ item: 'minecraft:oak_log', count: 8, nbt: '{display:{Name:"Oak Bundle"}}' }

// A KubeJS ItemStack is also accepted.
```

The public QShop binding is Builder-first. JSON CRUD calls such as `addEntry` and `updateEntry` are intentionally not exposed. This keeps item parsing, UUID handling, validation, saving, and client refresh in one API path.

Invalid item ids are parsed as an empty item. Non-command entries with no usable item are rejected.

## Shop API

### Open and inspect shops

```js
QShop.open(shopIdOrUuid, player)
QShop.openByUuid(shopUuid, player)
QShop.exists(shopIdOrUuid)
QShop.getShopIds()
QShop.getShopUuid(shopIdOrUuid)
```

`open` accepts either a shop id or a shop UUID. The player must be a server-side player; calls made with a client-only player are ignored.

```js
PlayerEvents.chat(event => {
  if (event.message.trim() === '!shop') {
    event.cancel()
    QShop.open('starter', event.player)
  }
})
```

### Create and remove shops

```js
QShop.createShop('vip')
QShop.createShop('vip', 'VIP Shop')
QShop.createShop('vip', 'VIP Shop', 'coins')
QShop.removeShop('vip')
```

`createShop` creates a UUID and at least one default sub-shop. The optional currency is the currency shown in the main shop screen. A blank currency defaults to `coins`. Creating an existing id returns `false`. `removeShop` also removes the shop JSON file.

## Currency API

Currencies are stored in the world serverconfig under `qshop/currencies.json`.

```js
QShop.getCurrencies()
QShop.createCurrency('coins', 'Coins', '#FFD700')

QShop.getBalance(player, 'coins')
QShop.giveCurrency(player, 'coins', 100)
QShop.takeCurrency(player, 'coins', 25)
QShop.setCurrency(player, 'coins', 500)
```

These three KubeJS mutation methods update the wallet, sync the client, and fire the currency-change event when the effective balance changes. The event also includes `getDelta()`, `getSource()`, and `getSourcePos()`.

`createCurrency` returns `false` for a duplicate id or invalid color. Amounts are numeric. Wallet updates are synchronized to the client immediately.

Example command:

```js
ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(
    Commands.literal('vipcoins')
      .requires(source => source.hasPermission(2))
      .executes(ctx => {
        const player = ctx.source.player
        QShop.giveCurrency(player, 'coins', 100)
        return 1
      })
  )
})
```

## Sub-shops (tabs)

Every shop has at least one sub-shop. The first sub-shop is index `0`. A tab can be referenced by its numeric index or stable UUID:

```js
const tab = QShop.getTab('vip', 0)
const tabUuid = tab ? tab.uuid : null
QShop.getTabCount('vip')
```

### Add or update tabs

Use `TabBuilder` for both operations. A new UUID creates a tab. An existing UUID updates the tab metadata and preserves its entries.

```js
QShop.tab('vip')
  .name('Daily Offers')
  .icon({
    item: 'minecraft:paper',
    count: 1,
    nbt: '{display:{Name:"Daily Card"}}'
  })
  .uuid('daily-offers')
  .description('Refreshes every day')
  .quest('chapter_1')
  .stage('vip_unlocked')
  .add()
```

Tab option fields:

| Field | Type | Meaning |
| --- | --- | --- |
| `name` | string | Tab label. Required in options form. |
| `icon` | item | Icon shown in the tab list. |
| `uuid` | string | Stable reference. Generated when omitted. |
| `description` | string | Hover tooltip. Newlines are allowed. |
| `requiredQuests` | string[] | FTB Quests task ids that must be complete. |
| `requiredStages` | string[] | Stage ids that must be present. |

The `icon` accepts the same item forms as trade entries, including `{ item, count, nbt }`. The tab `description` is shown as a hover tooltip and may contain newlines.

When requirements are not satisfied, the tab and its entries are hidden from normal players. Edit mode may still show restricted content for administration.

### Remove tabs

```js
QShop.removeTab('vip', 2)                 // third tab by index
QShop.removeTab('vip', 'daily-offers')   // by tab UUID
```

The last remaining tab cannot be removed.

## Trade entries

Entries belong to a sub-shop. Omitting the tab reference targets the first tab. Use `EntryBuilder` for both creation and replacement. A builder with an existing UUID replaces that entry in place.

```js
QShop.entry('vip')
  .sell('minecraft:diamond')
  .price(100, 'coins')
  .uuid('diamond-sale')
  .add()

QShop.entry('vip', 'daily-offers')
  .buy({ item: 'minecraft:oak_log', count: 8 })
  .price(2, 'coins')
  .uuid('oak-bundle')
  .add()
```

Numeric `tabRef` values are zero-based indexes. String `tabRef` values are tab UUIDs, not tab names.

### Entry schema

| Field | Type | Applies to | Meaning |
| --- | --- | --- | --- |
| `uuid` | string | all | Stable entry reference. Generated when omitted. |
| `type` | `BUY\|SELL\|BARTER\|COMMAND` | all | Trade behavior. Defaults to `BUY`. |
| `displayName` | string | all | Optional custom name. |
| `description` | string | all | Optional hover description. |
| `displayItem` | item | all | Icon shown in the GUI, independent from the real item. |
| `item` | item | BUY, SELL, COMMAND | Main trade item. COMMAND may use it as an icon. |
| `give` | item[] | BARTER | Items paid by the player. |
| `receive` | item[] | BARTER | Items received by the player. |
| `currency` | string | BUY, SELL, COMMAND, optional BARTER fee | Currency id. Blank uses the shop default. |
| `price` | number | all | Price per trade unit. BARTER treats it as an optional extra fee. |
| `count` | int | all | Read-only convenience property: item count per trade unit (`item.count` for normal entries, received-item total for BARTER). |
| `globalLimit` | int | all | Server-wide trade-unit/purchase-count limit. `-1` means unlimited. |
| `playerLimit` | int | all | Per-player trade-unit/purchase-count limit. `-1` means unlimited. |
| `limitReset` | `NEVER\|DAILY\|WEEKLY\|MONTHLY` | limited entries | Counter reset period. |
| `commands` | object[] | COMMAND or post-trade actions | Commands run after a successful trade. |
| `requiredQuests` | string[] | all | FTB Quests requirements. |
| `requiredStages` | string[] | all | Stage requirements. |

`BUY` means the player pays currency and receives `item`. `SELL` means the player gives `item` and receives currency. `BARTER` exchanges `give` for `receive`, optionally charging `price` in `currency`. `COMMAND` charges the configured price and executes `commands`.

`count` is not the number of clicks or the number of units requested by a player. It describes the item stack quantity represented by one entry unit. The requested/completed transaction quantity is `beforeTrade.units` or `afterTrade.tradedUnits`.

### Commands in an entry

```js
commands: [
  { command: 'give %player% minecraft:elytra 1', op: true, silent: true },
  { command: 'say %player% purchased an elytra', op: false, silent: false }
]
```

- `command`: command text without the leading `/`.
- `op`: execute with operator level 4 when `true`; otherwise execute as a normal command source.
- `silent`: suppress successful command output when `true`.

Supported placeholders include `%player%`, `%player_uuid%`, `%shop%`, `%shop_uuid%`, `%entry%`, `%units%`, `%items%`, `%price%`, `%currency%`, and `%multiplier%`.

For `COMMAND` entries, a bulk purchase executes every configured command once per purchased unit. In each execution, `%units%`, `%items%`, `%price%`, and `%multiplier%` refer to that single unit. Commands attached to other trade types remain post-trade commands and execute once with aggregate values.

### Query and remove entries

```js
QShop.getEntryCount('vip')
QShop.getEntryCount('vip', 1)

const firstEntry = QShop.getEntry('vip', 1, 0)
const entryUuid = firstEntry ? firstEntry.uuid : null

QShop.removeEntry('vip', 0, 0)             // tab index 0, entry index 0
QShop.removeEntry('vip', 'daily-offers', 'oak-bundle')
```

For updates, call the builder again with the same UUID. This is the only supported write path for entry contents.

## Builder API

Builders are an alternative to JSON and can be chained. The final `.add()` returns a boolean.

### EntryBuilder

```js
QShop.entry('vip', 'daily-offers')
  .sell('minecraft:diamond')
  .price(100, 'coins')
  .displayName('Shiny Diamond')
  .description('One diamond')
  .playerLimit(10, 'DAILY')
  .globalLimit(100)
  .uuid('daily-diamond')
  .add()
```

If a builder supplies an existing UUID, `.add()` performs an upsert instead of creating a duplicate:

```js
// Existing entry with this UUID is replaced in place.
QShop.entry('vip', 'daily-offers')
  .uuid('daily-diamond')
  .sell('minecraft:netherite_ingot')
  .price(500, 'coins')
  .add()
```

The same rule applies to tabs. A `TabBuilder` with an existing UUID updates the tab metadata and preserves its existing entries:

```js
QShop.tab('vip')
  .uuid('daily-offers')
  .name('New Daily Offers')
  .icon('minecraft:clock')
  .add()
```

Available methods:

```text
buy(item)                  sell(item)
command()                  barter(give, receive)
item(item)                 give(item)
receive(item)              price(number)
price(number, currency)    currency(id)
globalLimit(number)        playerLimit(number)
playerLimit(number, reset) limitReset(reset)
displayName(text)          description(text)
displayItem(item)          uuid(id)
quest(id)                  stage(id)
cmd(command)               cmd(command, op)
cmd(command, op, silent)   add()
```

Calling `cmd(...)` changes a BUY or SELL builder to `COMMAND`. `give(...)` and `receive(...)` append additional barter items.

### TabBuilder

```js
QShop.tab('vip')
  .name('Daily Offers')
  .icon('minecraft:paper')
  .description('Refreshes every day')
  .uuid('daily-offers')
  .quest('chapter_1')
  .stage('vip_unlocked')
  .add()
```

Available methods:

```text
name(text)       icon(item)
description(text) uuid(id)
quest(id)        stage(id)
add()
```

## Random tab refresh

`refreshTab` clears a tab and draws a new set of entries from a weighted pool. Every generated entry receives a new UUID and its limit counters start from zero.

The second argument, `tabRef`, accepts a zero-based numeric index or the tab UUID. In `QShop.refreshTab('vip', 'daily-offers', ...)`, `daily-offers` must be the tab's `uuid`, not its display name. Tab names are not accepted for lookup. For long-lived scripts, read the UUID from the tab object:

```js
const tab = QShop.getTab('vip', 0)
QShop.refreshTab('vip', tab.uuid, 5, pool)
```

```js
QShop.refreshTab('vip', 'daily-offers', 5, [
  {
    type: 'BUY',
    item: { item: 'minecraft:diamond', count: 1 },
    price: 100,
    currency: 'coins',
    playerLimit: 5,
    limitReset: 'DAILY',
    weight: 20
  },
  {
    type: 'BARTER',
    give: [{ item: 'minecraft:emerald', count: 3 }],
    receive: [{ item: 'minecraft:netherite_ingot', count: 1 }],
    weight: 10
  },
  {
    type: 'COMMAND',
    commands: [{ command: 'give %player% minecraft:elytra 1', op: true, silent: true }],
    price: 5000,
    currency: 'coins',
    weight: 5
  }
])
```

Options form:

```js
QShop.refreshTab('vip', 0, {
  count: 10,
  type: 'BUY',
  currency: 'coins',
  pool: [
    { item: 'minecraft:iron_ingot', price: 2, weight: 50 },
    { item: 'minecraft:gold_ingot', price: 5, weight: 10 }
  ]
})
```

Pool entries use the normal entry schema plus `weight`. A missing `weight` is `1`. Negative weights count as zero. If every weight is zero, one entry is chosen uniformly. Unknown item ids and invalid entries are skipped. At least one valid pool entry is required.

## Purchase limits

Limits are counted in trade units (purchase count), not item stacks. One completed BUY/SELL/BARTER/COMMAND unit consumes one limit even when that unit contains multiple items. A trade may complete partially when wallet balance, inventory space, stock, or limits allow fewer units than requested.

```js
QShop.entry('vip')
  .sell({ item: 'minecraft:diamond', count: 1 })
  .price(100, 'coins')
  .globalLimit(1000)
  .playerLimit(10, 'WEEKLY')
  .add()
```

Reset values are `NEVER`, `DAILY`, `WEEKLY`, and `MONTHLY`. Counters survive death and dimension changes. The server resets them when the configured period changes.

To clear counters:

```js
QShop.clearEntryLimits(shopRef, tabUuid, entryUuid)
QShop.clearTabLimits(shopRef, tabUuid)
QShop.clearShopLimits(shopRef)
```

`clearEntryLimits` and `clearTabLimits` identify the tab and entry by index or UUID (`shopId` may be either the shop id or shop UUID). Use `getTab(...).uuid` and `getEntry(...).uuid` when stable UUID references are preferred.

`shopRef` accepts either the shop ID or shop UUID. The tab reference accepts a zero-based numeric index or tab UUID, and the entry reference accepts a zero-based numeric index or entry UUID. Names are not accepted. The older parameter names `tabUuid` and `entryUuid` in examples are kept for compatibility, but UUIDs and indexes are both supported:

```js
const tab = QShop.getTab('vip', 0)
const entry = QShop.getEntry('vip', tab.uuid, 0)
QShop.clearEntryLimits('vip', tab.uuid, entry.uuid)
QShop.clearTabLimits('vip', tab.uuid)
```

Numeric index form:

```js
QShop.clearEntryLimits('vip', 0, 0) // first entry in the first tab
QShop.clearTabLimits('vip', 0)      // all entries in the first tab
```

These methods clear the global counter and personal counters for both online and offline players. Online players are updated through their live Capability; offline players are updated directly in `world/playerdata/<uuid>.dat` and do not need to log in.

## Requirements: FTB Quests and stages

Both tabs and entries support:

```js
requiredQuests: ['quest_id']
requiredStages: ['stage_id']
```

Quest checks require FTB Quests. Stage checks use GameStages (`gamestages`) through `GameStageHelper.hasStage`; when GameStages is not installed, QShop also tries the KubeJS PlayerStages provider. If neither provider is available, a configured stage requirement is treated as unsatisfied. Requirements are checked server-side and restricted tabs/entries are hidden from normal players.

## Trade events

The plugin registers the `QShopEvents` event group:

```js
QShopEvents.beforeTrade(event => { ... })
QShopEvents.afterTrade(event => { ... })
QShopEvents.currencyChanged(event => { ... })
```

### beforeTrade

This event runs before payment, item removal, rewards, and commands. Cancel it to reject the trade:

```js
QShopEvents.beforeTrade(event => {
  if (event.getEntry() && event.getEntry().uuid === 'maintenance-entry') {
    event.cancel()
    event.player.tell('This entry is temporarily unavailable.')
  }
})
```

Event-level properties:

```text
player, playerName
tabIndex, entryIndex, units
```

`units` is the number requested by the player, before inventory, balance, and limit adjustments.

The shop hierarchy is exposed as complete objects. Read every shop, tab, and entry field through these objects:

```js
QShopEvents.beforeTrade(event => {
  const shop = event.getShop()
  const tab = event.getTab()
  const entry = event.getEntry()

  if (entry && entry.type.name() === 'BUY' && entry.getCount() > 64) {
    event.cancel()
  }

  console.log(`${shop.id}/${tab.uuid}/${entry.uuid}`)
  console.log(`${entry.displayName} x${entry.count} for ${entry.price} ${entry.currencyId}`)
})
```

`entry.count` is the KubeJS property form of `entry.getCount()`. Both forms are supported. `entry.type` is a Java enum, so compare it with `entry.type.name()` (for example, `entry.type.name() === 'BUY'`). Public fields such as `entry.item`, `entry.give`, `entry.receive`, `entry.commands`, `entry.requiredQuests`, and `entry.requiredStages` can be read the same way. `shop` or `tab` may be `null` only when the event payload does not point to a valid hierarchy object.

### afterTrade

This event runs after a successful trade and all configured post-trade commands:

```js
QShopEvents.afterTrade(event => {
  const entry = event.getEntry()
  console.log(
    `${event.playerName} bought ${entry.displayName} x${event.tradedUnits}`
  )
  if (event.isPartial()) {
    event.player.tell(`Only ${event.tradedUnits} unit(s) were available.`)
  }
})
```

Event-level properties:

```text
player, playerName
tabIndex, entryIndex
tradedUnits, totalItems, paidPrice, partial (or `isPartial()`)
```

`paidPrice` is `price * tradedUnits`. `partial` is `true` when fewer units completed than requested.

`afterTrade` also provides `event.getShop()`, `event.getTab()`, and `event.getEntry()`. Use `event.getEntry().type`, `event.getEntry().count`, `event.getEntry().price`, and `event.getEntry().currencyId` instead of flattened event properties. For example:

```js
QShopEvents.afterTrade(event => {
  const entry = event.getEntry()
  console.log(`${entry.type} ${entry.displayName} count=${entry.count}`)
})
```

### currencyChanged

This event runs after a player's effective currency balance changes:

```js
QShopEvents.currencyChanged(event => {
  const player = event.getPlayer()
  console.log(`${player.getGameProfile().getName()} ${event.getCurrency()}: `
    + `${event.getOldValue()} -> ${event.getNewValue()}`)
})
```

The event provides:

```text
getPlayer()     the affected ServerPlayer
getCurrency()   the currency id
getOldValue()   the effective balance before the change
getNewValue()   the effective balance after the change
getDelta()      newValue - oldValue
getSource()     source ResourceLocation, or null for legacy/manual calls
getSourcePos()  source block position, or null when not block-related
```

It fires for BUY/COMMAND currency payments, SELL income, the FTB Quests `qshop:money` reward, the FTB Quests `qshop:money` task's consumed balance, `/qshop currency give/take/set`, KubeJS currency methods, configured death retention, and Java addon currency services. Append `false` after the amount to disable the command event, or `true` to enable it explicitly:

```text
/qshop currency give Steve coins 100 false
/qshop currency take Steve coins 25 true
```

`QShop.giveCurrency/takeCurrency/setCurrency` now fire this event when the effective balance changes. The event is also emitted for configured death retention and Java addon currency services.

## Java addon API

Forge addon mods can use the stable facade in `com.qshop.api`:

```java
QShopAddonApi.currency().deposit(player, "coins", 25,
        ResourceLocation.fromNamespaceAndPath("my_mod", "auto_sell"), blockPos);

// UUID works for both online and offline players.
QShopAddonApi.currency().deposit(server, ownerUuid, "coins", 25,
        ResourceLocation.fromNamespaceAndPath("my_mod", "auto_sell"), blockPos);
double balance = QShopAddonApi.currency().getBalance(server, ownerUuid, "coins");
int used = QShopAddonApi.currency().getLimitCount(
        server, ownerUuid, "vip|entry-uuid", "DAILY");

TradeResult result = QShopAddonApi.sell(
        player, itemHandler, "vip", 0, 0, 16,
        ResourceLocation.fromNamespaceAndPath("my_mod", "auto_sell"), blockPos);
```

`sell` and `buy` accept Forge `IItemHandler` inventories and return `TradeResult`. They support tab/entry indexes or UUID references, enforce requirements and limits, and trigger the normal trade and currency events. For scheduled container settlement, save the owner's UUID and call `currency().deposit(server, ownerUuid, ...)` or `withdraw(...)`; these methods work for online and offline players by reading/writing `playerdata/<uuid>.dat`. `getLimitCount(server, uuid, key, period)` reads the same offline personal-limit data.

UUID mutations require a `MinecraftServer`, because the server is needed to locate the current world's playerdata directory. If the player is online, the live Capability is used and the client wallet is synchronized. If the playerdata file does not exist, the operation returns zero/false and does not create a partial player file.

The Java `CurrencyChangedEvent` always includes `getPlayerUuid()`. For offline mutations `getPlayer()` is `null`; no client packet or KubeJS player event is emitted because there is no `ServerPlayer` instance. The Forge event is still posted with the UUID and source metadata.

## Configuration files and reload

Shop data is stored per world:

```text
<world>/serverconfig/qshop/currencies.json
<world>/serverconfig/qshop/shops/<shop-id>.json
config/qshop-common.toml
```

When the server root contains `defaultconfigs/qshop/`, QShop copies that directory into a new world's `serverconfig/qshop/` directory on first load. Existing world files are never overwritten. Use this layout for pack-provided default shops:

If `defaultconfigs/qshop/shops/` contains any shop JSON other than `starter.json`, the built-in `starter.json` is skipped during import. This lets a modpack provide its own shops without also receiving the example shop. If `starter.json` is the only shop, it is imported normally.

```text
defaultconfigs/qshop/
├── currencies.json
└── shops/
    ├── sdm.json
    └── vip.json
```

### Currency loss on death

The common config is `config/qshop-common.toml`. By default, `loseCurrencyOnDeath = false`, so a death keeps the complete wallet. When enabled, `defaultCurrencyRetention` applies to currencies without an override and `currencyRetention` can define per-currency ratios:

```toml
[death]
loseCurrencyOnDeath = true
defaultCurrencyRetention = 0.0
currencyRetention = ["coins=0.2", "points=0.5"]
```

`coins=0.2` keeps 20% of coins after death. Ratios are clamped to `0.0..1.0`; `coins:0.2` is also accepted. The ratios are ignored while `loseCurrencyOnDeath` is disabled.

The API writes these files when it mutates data. After manually editing JSON:

```js
QShop.reload()
```

or run:

```text
/qshop reload
```

The first tab is kept as the legacy `entries` list for compatibility with older configuration files.

## Complete example

Save as `kubejs/server_scripts/qshop_example.js`:

```js
ServerEvents.loaded(event => {
  if (!QShop.exists('adventurer')) {
    QShop.createShop('adventurer', 'Adventurer Shop', 'coins')
  }

  const tabs = QShop.getTabCount('adventurer')
  if (tabs < 2) {
    QShop.tab('adventurer')
      .name('Daily Deals')
      .icon('minecraft:clock')
      .uuid('daily-deals')
      .description('Rotating daily offers')
      .stage('adventurer_unlocked')
      .add()
  }

  if (QShop.getEntryCount('adventurer', 0) === 0) {
    QShop.entry('adventurer')
      .sell('minecraft:diamond')
      .price(100, 'coins')
      .playerLimit(10, 'DAILY')
      .displayName('Diamond')
      .add()
  }

  if (QShop.getEntryCount('adventurer', 1) === 0) {
    QShop.entry('adventurer', 'daily-deals')
      .buy({ item: 'minecraft:oak_log', count: 8 })
      .price(2, 'coins')
      .playerLimit(20, 'DAILY')
      .uuid('daily-oak-bundle')
      .add()
  }
})

QShopEvents.beforeTrade(event => {
  if (event.getEntry().type.name() === 'COMMAND' && event.player.isCrouching()) {
    event.cancel()
    event.player.tell('Do not crouch while buying this command entry.')
  }
})

QShopEvents.afterTrade(event => {
  if (event.partial) {
    event.player.tell(`Completed ${event.tradedUnits} unit(s).`)
  }
})
```

## Troubleshooting

### `QShop` is undefined

Confirm that KubeJS and QShop are installed on the server, and that the QShop KubeJS plugin resource is present. Restart the server after installing a new mod; `/reload` does not reload Java plugins.

### A method returns `false`

Check the shop id, tab index/UUID, entry index/UUID, and JSON item format. Non-command entries need at least one valid item. A tab cannot be removed when it is the last tab.

### A tab or entry is hidden

Check `requiredQuests` and `requiredStages`. These restrictions are intentional for normal players; use edit mode or remove the requirement while testing.

### Refresh produces fewer entries than requested

Invalid pool entries are skipped. Check item ids, `type`, `item`/`give`/`receive`, and `commands` for COMMAND entries.

### Changes do not appear immediately

The server immediately pushes shop refresh packets after saved mutations. The client no longer polls for changes. For manual JSON edits, call `QShop.reload()` or use `/qshop reload`. A currently open trade dialog is closed when the shop data is refreshed so a player cannot purchase stale content.

## License

QShop and its assets are All Rights Reserved (ARR). See [`LICENSE`](LICENSE). Redistribution, modification, or inclusion in another project requires prior written permission from the copyright holder.
