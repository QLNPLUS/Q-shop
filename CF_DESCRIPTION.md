# QShop — CurseForge Project Description

> Paste the **Short Description** into the "Summary" field and the **Full Description**
> (everything under the second heading) into the "Description" field.

---

## Short Description (one sentence)

> A flexible server-side shop mod for Forge 1.20.1 with a full trade GUI, custom currencies, buy/sell/barter/command trades, purchase limits, in-game editing, and KubeJS integration.

---

## Full Description

# QShop

**QShop** is a server-side shop mod for **Minecraft Forge 1.20.1**. Create one or more shops, fill them with trades through a friendly in-game GUI (or via commands / KubeJS), use custom non-item currencies backed by per-player wallets, and let your players buy, sell, barter, or trigger server commands — all with configurable quantity, purchase limits, and quest/stage requirements.

> **No mandatory mod dependencies.** Works on a plain Forge 1.20.1 server. KubeJS, FTB Quests and
> GameStages are optional and only required for their respective features (scripting / quest gates / stage gates).

## Features

### Trade GUI
- Buy, sell, item-for-item **barter**, and **command** trades in one grid.
- **Barter** trades can optionally charge a small currency fee on top of the items.
- **Command** trades run server commands on purchase — pay with items or currency, your choice.
- Each trade supports custom display name, description (with `§` color codes), and a display item for the slot.
- Per-trade quantity: buy any amount up to your balance/stock, with a live total price preview.

### Custom currencies
- Any number of non-item currencies (e.g. coins, points, tokens), each with a name and color.
- Stored per player in a persistent wallet — no item spam, no chests.
- Manage with `/qshop currency give|take|set`, `/qshop currency create`, or KubeJS.
- Big-number safe formatting with K/M/B abbreviation in the GUI.

### Shops & sub-shops
- Multiple shops, identified by id **or** uuid.
- Each shop has a scrollable **tab list** (sub-shops) with icons.
- Each shop has a **default currency** (the balance shown at the bottom of the GUI) — set it when creating the shop (command / KubeJS) or later in the **Edit Shop** dialog.
- Shop data lives in per-shop JSON files (`world/serverconfig/qshop/shops/`) — fully hand-editable.
- In-game editing (creative + OP): add/remove/copy/reorder entries by drag & drop, edit items with a full item picker (including NBT), rename tabs, edit shop info (name, icon, default currency).

### Purchase limits
- Per-entry **global** (server-wide) and **per-player** limits, counted in items.
- Reset cycles: never / daily / weekly / monthly.

### Requirements (gates)
- Gate entries **and whole tabs** behind **FTB Quests** or **GameStages / KubeJS stages**.
- These are **optional, per-trade features**: a trade is only affected if you actually configure requirements on it.
- Stage detection requires the **GameStages** mod (or KubeJS's stage system) to be installed.
- If you configure a requirement but the matching mod is not installed, that requirement is treated as **not met** (the trade stays blocked) — so only set up quest/stage gates on servers that have those mods.

### Commands on purchase
- Run one or more commands after a purchase with placeholders:
  `%player%`, `%player_uuid%`, `%shop%`, `%shop_uuid%`, `%entry%`, `%units%`, `%items%`, `%price%`, `%currency%`, `%multiplier%`.
- Per-command OP level (console-level or player-level) and silent execution.

### Polish
- Custom item tooltips, colored text, fade masks, smooth scrolling — the GUI is built for usability.
- Trade feedback messages can be sent to chat **or** the actionbar (statsMessage area) to avoid chat spam — server-config toggle.
- Edit-mode state is remembered across screen closes (creative), and survival mode always overrides it off.

## Commands

```
/qshop open <shop> [player]              Open a shop (OP can open for others)
/qshop list                              List all shops
/qshop balance                           Show your wallet balances
/qshop reload                            Reload all shop configs from disk
/qshop currency list                     List currencies
/qshop currency create <id> <name> [color]
/qshop currency give|take|set <player> <currency> <amount>
/qshop shop create <id> [displayName] [currency]
/qshop edit <shop> add <type> [price] [currency]
/qshop edit <shop> remove <index>
/qshop edit <shop> setitem <index>
/qshop edit <shop> set <index> <field> <value>
/qshop item                              Print your held item as Base64 (for configs)
```
> `type` for `/qshop edit add`: `buy`, `sell`, `barter`, `command`. Editing commands require permission level 2.
> `/qshop shop create`: currency defaults to `coins` when omitted; use quotes for names with spaces, e.g. `/qshop shop create vip "VIP Shop" tokens`.

## Configuration

- **Shops**: `world/serverconfig/qshop/shops/<id>.json` — one file per shop, auto-generated on first run (`starter` shop included as an example).
- **Currencies**: `world/serverconfig/qshop/currencies.json`.
- **Server settings** (`world/serverconfig/qshop-server.toml`): toggle trade feedback messages and switch them to the actionbar.
- **Client settings** (`config/qshop-client.toml`): tab-list fade masks and their color.

Item format in configs (all three are accepted): `"minecraft:diamond"`, `{"item": "minecraft:oak_log", "count": 8, "nbt": "{...}"}`, or Base64 (what the mod saves and `/qshop item` prints).

## KubeJS Integration

With **KubeJS** installed, the `QShop` binding is available in `server_scripts`. Full API:

```js
// ---- Shops ----
QShop.open('starter', event.player);           // open for a player (by id or uuid)
QShop.openByUuid('xxxxxxxx-...', event.player);
QShop.exists('starter');                       // boolean
QShop.getShopIds();                            // string[]
QShop.getShopUuid('starter');
QShop.createShop('vip', 'VIP Shop', 'coins'); // display name + default currency (empty = 'coins')
QShop.removeShop('vip');                       // boolean
QShop.reload();

// ---- Currency (wallet) ----
QShop.getBalance(event.player, 'coins');       // double
QShop.giveCurrency(event.player, 'coins', 100);
QShop.takeCurrency(event.player, 'coins', 10);
QShop.setCurrency(event.player, 'points', 50);
QShop.getCurrencies();                         // string[]
QShop.createCurrency('tokens', 'Tokens', '55ff55'); // boolean (hex color)

// ---- Sub-shops (tabs) ----
QShop.addTab('vip', 'Weapons', 'minecraft:iron_sword'); // icon optional (positional form)
QShop.addTab('vip', 'Armor', 'minecraft:diamond_chestplate', 'my-fixed-tab-uuid'); // + uuid (positional)
QShop.addTab('vip', {                                  // recommended: options object (ALL tab fields)
    name: 'Daily Pool',
    icon: 'minecraft:paper',           // optional (item id / {item,count,nbt} / base64)
    uuid: 'daily-tab-uuid',            // optional, random when omitted
    description: 'Refreshes every day',// optional, shown in hover tooltip
    requiredQuests: ['quest-id-1'],    // optional, FTB quest gates
    requiredStages: ['vip']            // optional, stage gates
});
QShop.updateTab('vip', 0, 'Armor');                    // rename by index (positional)
QShop.updateTab('vip', 0, 'Armor', 'minecraft:diamond_chestplate'); // + icon (positional)
QShop.updateTab('vip', 'daily-tab-uuid', {             // recommended: only listed fields change
    name: 'New Name',
    description: 'New description',
    icon: null,                        // null clears the icon
    requiredQuests: [],                // empty array clears the list
    requiredStages: ['vip']
});
QShop.updateTabByUuid('vip', tabUuid, 'Armor', null);  // by uuid (positional)
QShop.removeTab('vip', 0);
QShop.removeTabByUuid('vip', tabUuid);         // keeps at least one tab
QShop.getTabCount('vip');
QShop.getShopTabUuid('vip', 0);

// ---- Entries ----
// Build entry JSON with JsonIO.of({...})  (note: JsonIO, not JsonUtils!)
QShop.addEntry('vip', JsonIO.of({
    type: 'SELL',                              // BUY | SELL | BARTER | COMMAND
    item: 'minecraft:diamond',
    price: 100,
    currency: 'coins',
    globalLimit: 100,
    playerLimit: 10,
    limitReset: 'DAILY',                       // NEVER | DAILY | WEEKLY | MONTHLY
    uuid: 'my-fixed-entry-uuid'                // optional, empty/omitted = random
}));
QShop.addEntry('vip', 1, JsonIO.of({ type: 'BUY', item: {item: 'minecraft:oak_log', count: 8}, price: 2, currency: 'coins' }));
// 2nd argument: Number = tab index (0-based), String = tab uuid
QShop.addEntry('vip', tabUuid, JsonIO.of({ type: 'COMMAND', commands: [{command: 'give %player% diamond 1', op: true, silent: true}] }));
QShop.updateEntry('vip', 0, JsonIO.of({ type: 'SELL', item: 'minecraft:netherite_ingot', price: 500, currency: 'coins' }));
QShop.updateEntryByUuid('vip', tabUuid, entryUuid, JsonIO.of({ type: 'SELL', item: 'minecraft:emerald', price: 50 }));
QShop.removeEntry('vip', 1, 0);                // tab 1, index 0
QShop.removeEntryByUuid('vip', tabUuid, entryUuid);
QShop.getEntryCount('vip');                    // entries in the default tab
QShop.getEntryCount('vip', 1);
QShop.getShopEntryUuid('vip', 0, 0);

// ---- Refresh a tab (reroll its content from a weighted pool) ----
// Pool entries are STANDARD entry JSON (all 16 ShopEntry fields) plus `weight` for weighted selection.
QShop.refreshTab('card', 'daily-tab-uuid', 10, [
    { type: 'BUY', item: {item: 'minecraft:diamond', count: 1, nbt: '{Enchantments:[{id:"minecraft:sharpness",lvl:3s}]}'},
      price: 1000000, currency: 'coins', globalLimit: 50, playerLimit: 5, limitReset: 'DAILY',
      displayName: 'Sharp Diamond', description: '§aWeekly limited', displayItem: 'minecraft:diamond_sword',
      requiredQuests: ['quest-b'], weight: 20 },
    { type: 'BARTER', give: [{item: 'minecraft:emerald', count: 3}], receive: [{item: 'minecraft:netherite_ingot', count: 1}], weight: 10 },
    { type: 'COMMAND', commands: [{command: 'give %player% minecraft:elytra 1', op: true, silent: true}], price: 5000, weight: 5 },
    { type: 'SELL', item: 'minecraft:iron_ingot', price: 100, weight: 30 }
]);
// options form: {count, currency (default for entries without explicit currency), pool}
QShop.refreshTab('card', 0, { count: 10, currency: 'coins', pool: [ ... ] });
// Note: every generated entry gets a fresh uuid (purchase limits start at zero); unknown items are skipped with a warning.

// ---- Limit cleanup ----
QShop.clearEntryLimits('vip', tabUuid, entryUuid);
QShop.clearTabLimits('vip', tabUuid);
QShop.clearShopLimits('vip');
```

### KubeJS field reference

**ShopEntry (trade entry) — 16 fields**, same schema as shop JSON files and `addEntry`/`updateEntry`/`refreshTab` pool entries:

| Field | Type | Meaning |
| --- | --- | --- |
| `uuid` | string | stable id (auto-generated when omitted; refreshed entries always get a new one) |
| `type` | BUY / SELL / BARTER / COMMAND | default BUY |
| `displayName` | string | custom title (empty = item name) |
| `description` | string | hover tooltip description |
| `displayItem` | item | slot icon only (can differ from the traded item) |
| `item` | item | traded item for BUY/SELL/COMMAND (with count & NBT) |
| `give` / `receive` | item[] | BARTER give/receive lists |
| `currency` | string | price currency id (empty = shop default) |
| `price` | number | unit price |
| `globalLimit` | int | server-wide limit, -1 = unlimited |
| `playerLimit` | int | per-player limit, -1 = unlimited |
| `limitReset` | NEVER / DAILY / WEEKLY / MONTHLY | limit reset cycle |
| `commands` | `[{command, op, silent}]` | commands run on purchase |
| `requiredQuests` | string[] | FTB quest gates |
| `requiredStages` | string[] | stage gates |

Item formats (all accepted): `"minecraft:diamond"`, `{"item":"minecraft:oak_log","count":8,"nbt":"{...}"}`, or Base64.

**ShopTab (sub-shop) — 7 fields**, settable via `addTab(shopId, options)` / `updateTab(shopId, tabRef, options)`:

| Field | Type | Meaning |
| --- | --- | --- |
| `uuid` | string | stable id (auto-generated when omitted) |
| `name` | string | tab name |
| `icon` | item | tab icon |
| `description` | string | hover tooltip description |
| `entries` | entry[] | trade entries (managed via entry APIs) |
| `requiredQuests` | string[] | FTB quest gates (whole tab hidden when not met) |
| `requiredStages` | string[] | stage gates |

Notes: `updateTab` only changes fields present in the options object — `icon: null` clears it, `requiredQuests: []` clears the list.

### Fluent builder (alternative to the JSON form)

Both APIs are equivalent and fully tested — use whichever you prefer. Builders are more discoverable and validate step by step; the JSON form mirrors the shop config files.

```js
// Trade entry
QShop.entry('vip')                          // target shop (2nd arg optional: Number = tab index, String = tab uuid)
    .sell('minecraft:diamond')               // or .buy(...) / .command() / .barter(give, receive)
    .price(100, 'coins')                     // unit price + currency
    .playerLimit(10, 'DAILY')                // player limit + reset (NEVER/DAILY/WEEKLY/MONTHLY)
    .globalLimit(100)
    .description('§aRare material')
    .uuid('my-entry-id')                     // optional, random when omitted
    .add();                                  // returns boolean

// Command entry (cmd() switches the type to COMMAND automatically)
QShop.entry('vip')
    .cmd('give %player% minecraft:elytra 1', true, true)   // command, op, silent
    .price(50, 'coins')
    .add();

// Barter with a JS object item ({item, count, nbt} also accepted)
QShop.entry('vip', 0)
    .barter({item: 'minecraft:stone', count: 2}, 'minecraft:cobblestone')
    .add();

// Sub-shop (tab)
QShop.tab('vip')
    .name('Weapons')
    .icon('minecraft:iron_sword')
    .uuid('my-tab-id')                       // optional, random when omitted
    .add();
```

Item arguments in builders accept: an item id string (`'minecraft:diamond'`), an ItemStack, an item JSON, or a JS object (`{item, count, nbt}`).

### Events

```js
// 购买前事件(可取消):扣费/扣物之前触发
QShopEvents.beforeTrade(event => {
    console.log(event.playerName + ' 想买 ' + event.entryName + ' x' + event.units
            + ' 单价 ' + event.price + ' ' + event.currency);
    if (event.entryUuid === 'some-entry') {
        event.cancel();   // 取消这笔交易
        event.player.tell('该条目已下架');
    }
});

// 购买后事件(只读):成交后触发(含实际数量/实付/是否部分成交)
QShopEvents.afterTrade(event => {
    console.log(event.playerName + ' 买了 ' + event.entryName + ' x' + event.tradedUnits
            + ',实付 ' + event.paidPrice + ' ' + event.currency + (event.partial ? ' (部分成交)' : ''));
});
```

Available fields: `player` / `playerName`, `shopId` / `shopUuid`, `tabIndex` / `entryIndex` / `entryUuid`, `entryType` (BUY/SELL/BARTER/COMMAND), `entryName`, `price` / `currency`, `units` (before) or `tradedUnits` / `totalItems` / `paidPrice` / `partial` (after).

### Live sync

Players with the shop GUI open get **modified entries/tabs pushed to them within ~2 seconds** — if another admin or a KubeJS script changes the shop (price, entries, tabs), open GUIs refresh automatically while keeping scroll/tab/edit-mode state.

### Example server script

```js
// server_scripts/qshop_example.js
// 1) One-time setup: create a shop once the server is fully up
let qshopSetupDone = false;
ServerEvents.tick(event => {
    if (qshopSetupDone) return;
    qshopSetupDone = true;
    if (!QShop.exists('vip')) {
        QShop.createShop('vip', 'VIP Shop', 'coins');
        QShop.addTab('vip', 'Weapons', 'minecraft:iron_sword');
        QShop.addEntry('vip', 0, JsonIO.of({
            type: 'SELL',
            item: 'minecraft:diamond',
            price: 100,
            currency: 'coins',
            playerLimit: 10,
            limitReset: 'DAILY'
        }));
        QShop.addEntry('vip', 0, JsonIO.of({
            type: 'COMMAND',
            commands: [{ command: 'give %player% minecraft:elytra 1', op: true, silent: true }]
        }));
    }
});

// 2) Welcome bonus whenever a player joins
PlayerEvents.loggedIn(event => {
    QShop.giveCurrency(event.player, 'coins', 50);
});

// 3) A command to open the shop: /openshop
ServerEvents.commandRegistry(event => {
    const { commands } = event;
    event.register(
        commands.literal('openshop')
            .requires(src => src.hasPermission(2))
            .executes(ctx => {
                QShop.open('vip', ctx.source.entity);
                return 1;
            })
    );
});
```

> **Tip:** a freshly created shop already contains one default sub-shop, so `QShop.getTabCount()` starts at 1 and every `QShop.addTab()` adds one more. Prefer player-triggered events (`PlayerEvents.*`, commands, `ServerEvents.tick`) for shop setup — the server's shop manager is fully loaded at that point.

## Requirements

> **QShop has NO mandatory mod dependencies.** It runs on a plain Forge 1.20.1 server out of the box.
> KubeJS, FTB Quests and GameStages are all **optional** and only needed if you want their specific features.

- **Required:** Minecraft 1.20.1, Forge 47.x
- **Optional — KubeJS:** scripting integration (`QShop` binding). Auto-detected, loads only when installed.
- **Optional — FTB Quests:** quest gates. Only needed if you configure `requiredQuests` on trades or tabs.
- **Optional — GameStages:** stage gates. Only needed if you configure `requiredStages`. Stage checks also work through KubeJS's player stages if KubeJS is installed (then GameStages is not strictly required), but for classic GameStages-based gating you need the **GameStages** mod (e.g. `GameStages-Forge-1.20.1-15.0.2.jar`).

> ⚠️ If a trade has quest/stage requirements configured but the corresponding mod is missing, the requirements
> cannot be verified and the trade will be treated as **locked**. Keep requirement configs on servers that
> actually have FTB Quests / GameStages (or KubeJS) installed.

## Permissions

- `/qshop open` (to others), `/qshop reload`, `/qshop currency`, `/qshop shop create`, `/qshop edit`: **permission level 2 (OP)**
- In-game shop editing: **permission level 2 + Creative mode**

## License

MIT
