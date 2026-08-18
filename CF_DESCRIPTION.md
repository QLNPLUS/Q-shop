# QShop - CurseForge Project Description

> Paste the **Short Description** into the CurseForge Summary field and the
> **Full Description** into the Description field.

## Short Description

> A flexible Forge 1.20.1 server shop with buy, sell, barter and command trades, custom currencies, limits, in-game editing and a Builder-first KubeJS API.

## Full Description

# QShop 1.0.3

**QShop** is a configurable shop mod for Minecraft Forge 1.20.1. Create multiple shops and sub-shops, edit them in game, use custom non-item currencies, and let players buy, sell, barter or trigger server commands.

QShop has no mandatory gameplay integration dependencies. KubeJS, FTB Quests and GameStages are optional and only needed for their respective features.

## Features

### Trade GUI

- Buy, sell, item-for-item barter and command entries in one interface.
- Barter entries can optionally charge an additional currency fee.
- Command entries execute configured server commands after a successful purchase.
- Custom display names, descriptions, display items and item NBT are supported.
- Quantity controls use a slider and input box with live limit, inventory and balance checks.
- Smooth scrolling for entry grids and sub-shop tabs.

### Shops and sub-shops

- Multiple shops identified by a stable ID or UUID.
- Every shop has one or more sub-shops with an icon, description and independent entries.
- Sub-shop icons support item data and NBT; descriptions are shown as hover tooltips.
- Creative-mode editing supports adding, removing, copying, reordering and editing entries.
- Shop and sub-shop changes are saved and synchronized to open clients.

### Custom currencies

- Create any number of non-item currencies such as coins, points or tokens.
- Balances are stored per player and synchronized to the client.
- Manage currencies with commands or KubeJS.

### Purchase limits

- Global server-wide and per-player limits for each entry.
- Limits are counted in item units, not clicks.
- Reset periods: `NEVER`, `DAILY`, `WEEKLY` and `MONTHLY`.

### Requirements

- Gate complete sub-shops or individual entries behind FTB Quests tasks.
- Stage requirements use **GameStages** (`gamestages`) through `GameStageHelper`.
- KubeJS PlayerStages is also supported when available.
- If a configured requirement has no matching provider installed, it is treated as unmet and the content stays hidden from normal players.

## Commands

```text
/qshop open <shop> [player]
/qshop list
/qshop balance
/qshop reload
/qshop currency list
/qshop currency create <id> <name> [color]
/qshop currency give|take|set <player> <currency> <amount>
/qshop shop create <id> [displayName] [currency]
/qshop edit <shop> add <type> [price] [currency]
/qshop edit <shop> remove <index>
/qshop edit <shop> setitem <index>
/qshop edit <shop> set <index> <field> <value>
/qshop item
```

Shop editing requires permission level 2 and Creative mode. The final sub-shop cannot be removed.

## Configuration

```text
<world>/serverconfig/qshop/currencies.json
<world>/serverconfig/qshop/shops/<shop-id>.json
```

Items accept an ID, an item object with `count` and `nbt`, a KubeJS ItemStack, or the Base64 format written by QShop.

## KubeJS integration

Install KubeJS on the server to enable the global `QShop` binding. The public 1.0.3 API is Builder-first. JSON CRUD methods and direct `JsonIO` writes are not part of the public global API.

### Create or update data

```js
QShop.createShop('vip', 'VIP Shop', 'coins')

QShop.tab('vip')
  .name('Daily Offers')
  .icon({
    item: 'minecraft:paper',
    count: 1,
    nbt: '{display:{Name:"Daily Card"}}'
  })
  .description('Refreshes every day')
  .uuid('daily-offers')
  .stage('vip_unlocked')
  .add()

QShop.entry('vip', 'daily-offers')
  .buy({ item: 'minecraft:oak_log', count: 8 })
  .price(2, 'coins')
  .playerLimit(20, 'DAILY')
  .uuid('oak-bundle')
  .add()
```

Calling `add()` with an existing entry UUID replaces that entry in place. Calling `add()` with an existing tab UUID updates tab metadata while preserving its entries.

### Read objects

```js
const shop = QShop.getShop('vip')
const tab = QShop.getTab('vip', 'daily-offers')
const entry = QShop.getEntry('vip', 'daily-offers', 'oak-bundle')

console.log(shop.id, tab.uuid, entry.uuid)
console.log(entry.type.name(), entry.count, entry.getCount())
```

`getShop`, `getTab` and `getEntry` return complete Java objects. Read fields directly from the returned object: `entry.type`, `entry.item`, `entry.give`, `entry.receive`, `entry.price`, `entry.currencyId`, `entry.commands`, `entry.requiredQuests` and `entry.requiredStages`.

`entry.count` and `entry.getCount()` are equivalent. `count` is the item quantity represented by one entry unit, not the number of clicks. `entry.type` is an enum; compare it with `entry.type.name()`, for example `entry.type.name() === 'BUY'`.

### Reference rules

| Reference | Accepted values |
| --- | --- |
| `shopRef` | Shop ID or shop UUID |
| `tabRef` | Zero-based index, tab UUID or `null` for the first tab |
| `entryRef` | Zero-based index or entry UUID |

Use UUIDs for long-lived scripts because indexes change when content is reordered or refreshed.

```js
QShop.getEntryCount('vip')       // entries in tab 0
QShop.getEntryCount('vip', 1)    // entries in tab 1
QShop.removeEntry('vip', 'daily-offers', 'oak-bundle')
QShop.removeTab('vip', 'daily-offers')
```

### Currencies and refresh

```js
QShop.createCurrency('tokens', 'Tokens', '#55ff55')
QShop.giveCurrency(player, 'tokens', 100)
QShop.takeCurrency(player, 'tokens', 10)
QShop.setCurrency(player, 'tokens', 50)

QShop.refreshTab('vip', 'daily-offers', 10, [
  { item: 'minecraft:iron_ingot', price: 2, weight: 50 },
  { item: 'minecraft:gold_ingot', price: 5, weight: 10 }
])
```

Every generated entry receives a new UUID and starts with empty limit counters.

### Trade events

```js
QShopEvents.beforeTrade(event => {
  const entry = event.getEntry()
  if (entry && entry.type.name() === 'COMMAND') {
    event.cancel()
    event.player.tell('This entry is temporarily unavailable.')
  }
})

QShopEvents.afterTrade(event => {
  const shop = event.getShop()
  const tab = event.getTab()
  const entry = event.getEntry()

  console.log(`${shop.id}/${tab.uuid}/${entry.uuid}`)
  console.log(`${entry.type.name()} x${event.tradedUnits}`)
  if (event.isPartial()) {
    event.player.tell(`Completed ${event.tradedUnits} unit(s).`)
  }
})
```

Read shop, tab and entry fields through `getShop()`, `getTab()` and `getEntry()`. Event-only data includes `units` for `beforeTrade`, and `tradedUnits`, `totalItems`, `paidPrice` and `partial` for `afterTrade`.

## Documentation

- [English KubeJS Wiki](KUBEJS_WIKI.md)
- [Chinese KubeJS Wiki](KUBEJS_WIKI_CN.md)
- [Changelog](CHANGELOG.md)

## License

QShop and its assets are **All Rights Reserved (ARR)**. See `LICENSE` for the full terms.
