# Changelog

## 1.2.1 - 2026-08-25

### Added

- Added a 7x3 / 8x4 shop layout toggle with a separate wide panel texture.
- Added optional F8 layout debugging with persisted offsets in `config/qshop_layout.json`.
- Added independent `layout.png` and `layout_hover.png` icon materials for the layout toggle.

### Changed

- Merged client GUI settings into `config/qshop-common.toml` under the `client` section; `qshop-client.toml` is no longer registered.
- Increased the 8x4 row pitch by 1 pixel while keeping the final row inside the panel viewport.
- Limited entries at their global or personal cap are hidden outside edit mode.
- Layout debug offsets are now stored independently for the 7x3 and 8x4 layouts; existing version 1 files are migrated to the 7x3 layout.
- The selected 7x3/8x4 layout is persisted as `client.lastLayout` in `qshop-common.toml` and restored after restarting the game.
- Added an optional item search box with normal name/ID, `#tag`, and `@namespace` filters; its active button state is persisted as `client.searchActive`.
- Converted the add-entry and edit-mode controls to independent 16x16 icon buttons; layout debugging now moves each top control independently and applies changes immediately.

### Fixed

- Fixed bulk purchases of `COMMAND` entries executing the configured command only once; commands now execute once for each purchased unit.

## 1.1.2 - 2026-08-24

### Fixed

- Fixed purchased and bartered items exceeding their native stack size, such as ender pearls being placed in stacks of 64.

## 1.1.1 - 2026-08-23

### Fixed

- Fixed the QShop item picker not showing TACZ guns, attachments and ammo added by server datapacks.
- The picker now reads TACZ's server-synchronized index cache and builds the corresponding item stacks without making TACZ a required dependency.
- Fixed wallet balances being lost after player death when `loseCurrencyOnDeath = false` by restoring the original player's capability before cloning.
- Fixed purchased and bartered items exceeding their native stack size, such as ender pearls being placed in stacks of 64.

## 1.1.0 - 2026-08-21

### Added

- Added the official Java addon API under `com.qshop.api`.
- Added centralized currency mutations with client synchronization and Forge `CurrencyChangedEvent` dispatch.
- Added `IItemHandler`-based addon BUY/SELL transactions for server-side container mods.
- Added source and block-position metadata to Java and KubeJS currency-change events.

### Changed

- Existing trades, commands, FTB money integration, KubeJS currency methods, and configured death retention now use the centralized currency service.
- KubeJS `QShop.giveCurrency/takeCurrency/setCurrency` now fire `currencyChanged` when the effective balance changes.
- Purchase limits are counted in trade units/purchase counts instead of item quantities. One completed trade unit consumes one limit even when it contains multiple items.
- `QShop.clearEntryLimits`, `QShop.clearTabLimits` and `QShop.clearShopLimits` now clear personal counters for both online and offline players.
- New-world `defaultconfigs/qshop/` import skips the example `starter.json` when another default shop JSON is present; `starter.json` is imported normally when it is the only shop.

### Fixed

- Fixed offline players retaining personal purchase-limit records after a KubeJS limit-clear operation.
- Fixed custom default shop packs receiving the example starter shop unexpectedly.

## 1.0.7 - 2026-08-20

### Added

- Added automatic first-load import from `defaultconfigs/qshop/` into a new world's `serverconfig/qshop/` directory.
- Existing world shop and currency files are never overwritten by the default import.

## 1.0.6 - 2026-08-20

### Changed

- `QShop.clearEntryLimits` and `QShop.clearTabLimits` now accept zero-based indexes or UUIDs; names are still not accepted.
- Documented that the `refreshTab` tab reference accepts a zero-based index or tab UUID, not a tab name.

### Added

- Added `config/qshop-common.toml` currency-on-death settings with a global toggle, default retention ratio, and per-currency overrides.
- Added wallet synchronization after respawn so the client does not keep showing the pre-death balance.

## 1.0.5 - 2026-08-19

### 新增

- 新增 KubeJS `QShopEvents.currencyChanged` 玩家货币变动事件，提供 `getPlayer()`、`getCurrency()`、`getOldValue()`、`getNewValue()`。
- 交易、FTB 货币奖励/任务扣款和 `/qshop currency` 指令默认触发事件；指令支持追加 `true/false` 控制是否触发。

### 说明

- `QShop.giveCurrency/takeCurrency/setCurrency` 仍不会触发货币变动事件。

## 1.0.4 - 2026-08-18

### 变更

- 商店同步改为服务端主动推送：服务端保存商店后立即向在线客户端发送更新，不再由客户端每 2 秒轮询。
- `/qshop reload` 和 `QShop.reload()` 完成后会主动同步重新加载的商店数据。

### 修复

- 修复交易子窗口打开时无法应用商店刷新数据的问题。
- 收到商店刷新时会强制关闭交易子窗口并重建主界面，避免使用已经过期的交易项目购买。

### 文档

- 更新同步机制说明和 1.0.4 构建产物版本号。
- 新增 GitHub Actions CurseForge 自动发布工作流：发布 GitHub Release 后自动构建并上传 Forge 1.20.1 JAR。

## 1.0.3 - 2026-08-18

### 新增

- 新增 Builder-first KubeJS API：`QShop.entry(...)` 和 `QShop.tab(...)`。
- 新增统一对象查询：`QShop.getShop(...)`、`QShop.getTab(...)`、`QShop.getEntry(...)`。
- `ShopEntry` 提供 `count` / `getCount()`，可直接读取单个交易单位的物品数量。
- 交易事件提供 `getShop()`、`getTab()`、`getEntry()`，可以读取完整的商店层级对象。
- 新增中文 KubeJS Wiki：`KUBEJS_WIKI_CN.md`。

### 变更

- `EntryBuilder.add()` 使用相同 UUID 时会原位替换已有交易项目。
- `TabBuilder.add()` 使用相同 UUID 时会更新子商店信息并保留已有交易项目。
- 商店、子商店、交易项目引用统一支持 ID、UUID 和零基索引（按对象层级区分）。
- 交易事件的商店、子商店、交易项目字段统一从对象读取；交易结果只保留事件专属数据。

### 不兼容调整

- 旧的全局 JSON CRUD 方法（`addEntry`、`updateEntry`、`addTab`、`updateTab`）不再公开。
- KubeJS 全局 API 不再需要或暴露 `JsonIO` 写入流程。
- 事件中的扁平层级字段（如 `entryName`、`entryType`、`price`、`currency`、`shopId`）已移除。
- 请改用 `event.getShop()`、`event.getTab()`、`event.getEntry()`，例如：

```js
const entry = event.getEntry()
console.log(entry.type.name(), entry.count, entry.price, entry.currencyId)
```

### 修复

- 修复 Builder 更新时重复创建交易项目或子商店的问题。
- 修复交易事件无法直接访问完整商店层级数据的问题。
- 更新 KubeJS API 文档，统一参数引用和 UUID 使用规则。

### 文档修正

- 明确阶段条件的标准依赖是 GameStages（模组 ID：`gamestages`），并说明 KubeJS PlayerStages 的兼容路径。
- README 已改为展示 1.0.3 Builder-first API，并将旧 JSON 示例标记为历史参考。
