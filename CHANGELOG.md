# 更新日志

## Unreleased

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
