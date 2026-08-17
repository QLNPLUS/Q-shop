# QShop KubeJS 中文 Wiki

本文档对应 QShop `1.0.3`。QShop 是 Forge 1.20.1 的服务端商店模组，KubeJS 集成只在安装 KubeJS 时启用。

## 环境要求

- Minecraft Forge 1.20.1
- QShop
- KubeJS 6（Forge）
- 可选：FTB Quests，以及用于阶段检测的兼容模组

脚本放在 `kubejs/server_scripts/`。修改 JSON 后使用 `/reload` 或 `QShop.reload()`。

## 设计原则

1. **Builder-first**：写入商店、子商店和交易项目只使用 Builder。
2. **对象读取**：使用 `getShop()`、`getTab()`、`getEntry()` 读取完整对象字段。
3. **稳定引用**：长期脚本优先使用 UUID，临时脚本可以使用零基索引。
4. **立即同步**：写入操作会保存数据并刷新客户端；刷新过程中打开的交易子窗口会被打断，避免购买旧内容。

旧的 JSON CRUD（`addEntry`、`updateEntry`、`addTab`、`updateTab`）和直接使用 `JsonIO` 的流程不属于当前公开 API。

## 基本入口

KubeJS 中的全局对象是：

```js
QShop
```

常用方法：

| 分类 | 方法 |
| --- | --- |
| 打开和查询 | `open`、`openByUuid`、`exists`、`getShopIds`、`getShopUuid`、`getShop`、`getTab`、`getEntry`、`getTabCount`、`getEntryCount` |
| 商店写入 | `createShop`、`removeShop`、`tab`、`removeTab`、`entry`、`removeEntry` |
| 货币 | `getCurrencies`、`createCurrency`、`getBalance`、`giveCurrency`、`takeCurrency`、`setCurrency` |
| 限购和刷新 | `clearEntryLimits`、`clearTabLimits`、`clearShopLimits`、`refreshTab`、`reload` |

失败的写入方法返回 `false`，成功返回 `true`。查询不到对象时返回 `null`，数量查询返回 `0`。

## 引用规则

参数名称中的 `Ref` 表示引用：

| 参数 | 可用形式 | 含义 |
| --- | --- | --- |
| `shopRef` | 商店 ID 或商店 UUID | 选择顶层商店 |
| `tabRef` | 零基数字、子商店 UUID、`null` | 选择商店中的子商店；`null` 表示第一个子商店 |
| `entryRef` | 零基数字或交易项目 UUID | 选择子商店中的交易项目 |

数字只在所属层级内表示索引：

```js
// vip 是商店 ID；省略 tabRef 表示第一个子商店
QShop.getEntryCount('vip')

// 1 表示第二个子商店，不是商店 ID
QShop.getEntryCount('vip', 1)

// vip = 商店，1 = 第二个子商店，0 = 该子商店的第一个项目
const entry = QShop.getEntry('vip', 1, 0)
const entryUuid = entry ? entry.uuid : null
```

长期脚本建议使用 UUID：

```js
const shop = QShop.getShop('vip')
const tab = QShop.getTab('vip', 'daily-offers')
const entry = QShop.getEntry('vip', 'daily-offers', 'daily-diamond')

console.log(shop.uuid.toString())
console.log(tab.uuid)
console.log(entry.uuid)
```

## 对象读取

`getShop()`、`getTab()`、`getEntry()` 返回实际的 Java 对象。对象字段可直接读取，但不建议直接修改字段；直接修改不会自动保存或同步，应使用 Builder。

### Shop

常用字段：`id`、`uuid`、`displayName`、`currency`、`icon`、`tabs`。

### ShopTab

常用字段：`uuid`、`name`、`icon`、`description`、`entries`、`requiredQuests`、`requiredStages`。

子商店的 `description` 会在鼠标悬停时显示为 tooltip，支持换行。`icon` 支持物品 ID、物品对象和 NBT。

### ShopEntry

常用字段：

```text
uuid, type, displayName, description
item, displayItem, give, receive
currencyId, price, globalLimit, playerLimit, reset
commands, requiredQuests, requiredStages, count
```

`count` 是 `getCount()` 的 KubeJS 属性形式，两种写法都支持：

```js
const entry = QShop.getEntry('vip', 0, 0)
console.log(entry.count)
console.log(entry.getCount())
console.log(entry.type.name())
```

`type` 是 Java 枚举，类型判断使用：

```js
if (entry.type.name() === 'BUY') {
  // BUY、SELL、BARTER、COMMAND
}
```

`count` 的含义是一个交易单位包含的物品数量：普通项目读取 `item.count`，BARTER 项目读取 `receive` 的总数量。它不是玩家点击次数。

## 打开和管理商店

```js
QShop.open('starter', event.player)
QShop.openByUuid('shop-uuid', event.player)

QShop.exists('starter')
QShop.getShopIds()
QShop.getShopUuid('starter')
```

创建商店：

```js
QShop.createShop('vip')
QShop.createShop('vip', 'VIP 商店')
QShop.createShop('vip', 'VIP 商店', 'coins')
```

创建时会生成 UUID，并保证至少存在一个子商店。重复使用已有 ID 会返回 `false`。

删除商店：

```js
QShop.removeShop('vip')
```

## 货币

货币定义保存在世界存档的 `serverconfig/qshop/currencies.json`。

```js
QShop.getCurrencies()
QShop.createCurrency('coins', '金币', '#FFD700')

QShop.getBalance(player, 'coins')
QShop.giveCurrency(player, 'coins', 100)
QShop.takeCurrency(player, 'coins', 25)
QShop.setCurrency(player, 'coins', 500)
```

## 子商店和 TabBuilder

每个商店至少有一个子商店。第一个子商店的索引是 `0`。

```js
QShop.tab('vip')
  .name('每日特惠')
  .icon({
    item: 'minecraft:paper',
    count: 1,
    nbt: '{display:{Name:"每日卡片"}}'
  })
  .description('每天刷新一次')
  .uuid('daily-offers')
  .quest('chapter_1')
  .stage('vip_unlocked')
  .add()
```

可用方法：

```text
name(text)       icon(item)
description(text) uuid(id)
quest(id)        stage(id)
add()
```

如果 UUID 已存在，`add()` 会更新子商店的名称、图标、描述和检测条件，并保留该子商店已有的交易项目。

删除子商店：

```js
QShop.removeTab('vip', 2)
QShop.removeTab('vip', 'daily-offers')
```

最后一个子商店不能删除。

当 `requiredQuests` 或 `requiredStages` 不满足时，普通玩家看不到该子商店及其交易项目；编辑模式可以继续显示受限内容。

## 交易项目和 EntryBuilder

交易项目属于子商店。省略 `tabRef` 时使用第一个子商店。

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

物品参数支持：

```js
'minecraft:diamond'
{ item: 'minecraft:oak_log', count: 8, nbt: '{display:{Name:"橡木包"}}' }
// 也支持 KubeJS ItemStack
```

Builder 方法：

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

`give()` 和 `receive()` 会追加 BARTER 物品。调用 `cmd()` 会将项目类型设为 `COMMAND`。

### 使用 UUID 更新

EntryBuilder 使用已有 UUID 时，会在原位置替换完整交易项目，不会创建重复项目：

```js
QShop.entry('vip', 'daily-offers')
  .uuid('oak-bundle')
  .buy({ item: 'minecraft:birch_log', count: 8 })
  .price(3, 'coins')
  .add()
```

删除交易项目：

```js
QShop.removeEntry('vip', 0, 0)
QShop.removeEntry('vip', 'daily-offers', 'oak-bundle')
```

## 随机刷新子商店

`refreshTab` 会清空目标子商店，并根据权重池生成新项目。新项目会生成新的 UUID，限购计数从零开始。

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
  }
])
```

选项对象写法：

```js
QShop.refreshTab('vip', 0, {
  count: 10,
  currency: 'coins',
  pool: [
    { item: 'minecraft:iron_ingot', price: 2, weight: 50 },
    { item: 'minecraft:gold_ingot', price: 5, weight: 10 }
  ]
})
```

权重缺省为 `1`，负数按 `0` 处理；所有权重都是零时会均匀随机。无效物品和无效项目会被跳过。

## 限购

`globalLimit` 是全服限购，`playerLimit` 是玩家限购，统计单位是物品数量而不是点击次数。周期为：`NEVER`、`DAILY`、`WEEKLY`、`MONTHLY`。

```js
QShop.entry('vip')
  .sell({ item: 'minecraft:diamond', count: 1 })
  .price(100, 'coins')
  .globalLimit(1000)
  .playerLimit(10, 'WEEKLY')
  .add()
```

清理限购记录：

```js
QShop.clearEntryLimits(shopRef, tabUuid, entryUuid)
QShop.clearTabLimits(shopRef, tabUuid)
QShop.clearShopLimits(shopRef)
```

这里的 `tabUuid` 和 `entryUuid` 必须使用 UUID；`shopRef` 可以使用商店 ID 或 UUID。

## FTB Quests 和阶段检测

子商店和交易项目都支持：

```js
requiredQuests: ['quest_id']
requiredStages: ['stage_id']
```

这些条件由服务端检查。缺少可选集成时不会额外限制；条件不满足时普通玩家看不到对应内容。

## 交易事件

事件组：

```js
QShopEvents.beforeTrade(event => { ... })
QShopEvents.afterTrade(event => { ... })
```

### beforeTrade

交易扣款、扣物品、发放奖励和执行命令之前触发。调用 `event.cancel()` 可以拒绝交易。

```js
QShopEvents.beforeTrade(event => {
  const entry = event.getEntry()

  if (entry && entry.type.name() === 'COMMAND') {
    event.cancel()
    event.player.tell('该项目暂时不可购买')
  }
})
```

事件级数据：

```text
player, playerName
tabIndex, entryIndex, units
```

商店层级必须从对象读取：

```js
const shop = event.getShop()
const tab = event.getTab()
const entry = event.getEntry()

console.log(shop.id)
console.log(tab.uuid)
console.log(entry.type.name())
console.log(entry.count)       // 等同于 entry.getCount()
console.log(entry.price)
console.log(entry.currencyId)
```

### afterTrade

成功交易和所有交易后命令执行完成后触发。

```js
QShopEvents.afterTrade(event => {
  const entry = event.getEntry()
  console.log(`${entry.displayName} x${event.tradedUnits}`)

  if (event.isPartial()) {
    event.player.tell(`只完成了 ${event.tradedUnits} 个交易单位`)
  }
})
```

事件级数据：

```text
player, playerName
tabIndex, entryIndex
tradedUnits, totalItems, paidPrice, partial
```

`paidPrice` 是 `entry.price * tradedUnits`。`partial` 表示实际完成数量小于请求数量；也可以调用 `event.isPartial()`。

## 配置文件和重载

```text
<world>/serverconfig/qshop/currencies.json
<world>/serverconfig/qshop/shops/<shop-id>.json
```

手动编辑 JSON 后执行：

```js
QShop.reload()
```

或者使用：

```text
/qshop reload
```

## 完整示例

保存为 `kubejs/server_scripts/qshop_example.js`：

```js
ServerEvents.loaded(event => {
  if (!QShop.exists('adventurer')) {
    QShop.createShop('adventurer', '冒险者商店', 'coins')
  }

  if (QShop.getTabCount('adventurer') < 2) {
    QShop.tab('adventurer')
      .name('每日特惠')
      .icon('minecraft:clock')
      .uuid('daily-deals')
      .description('每天刷新')
      .stage('adventurer_unlocked')
      .add()
  }

  if (QShop.getEntryCount('adventurer', 0) === 0) {
    QShop.entry('adventurer')
      .sell('minecraft:diamond')
      .price(100, 'coins')
      .playerLimit(10, 'DAILY')
      .uuid('diamond')
      .add()
  }
})

QShopEvents.beforeTrade(event => {
  if (event.getEntry().type.name() === 'COMMAND' && event.player.isCrouching()) {
    event.cancel()
  }
})

QShopEvents.afterTrade(event => {
  if (event.isPartial()) {
    event.player.tell(`完成 ${event.tradedUnits} 个交易单位`)
  }
})
```

## 常见问题

### `QShop` 未定义

确认服务端同时安装 QShop 和 KubeJS。新增或升级 Java 插件后必须重启服务端，`/reload` 不会重新加载 Java 插件。

### 方法返回 `false`

检查商店 ID/UUID、子商店索引/UUID、交易项目索引/UUID，以及物品 ID 和参数格式。最后一个子商店不能删除。

### 子商店或项目隐藏

检查 `requiredQuests` 和 `requiredStages`。这是普通玩家的预期限制，编辑模式可以用于管理和测试。

### 刷新后数量不足

权重池中的无效物品和无效项目会被跳过。检查 `type`、`item`、`give`、`receive` 和 `commands`。

### 修改没有立即显示

API 写入会自动保存并同步客户端。手动编辑 JSON 后执行 `QShop.reload()` 或 `/qshop reload`。

## 许可

QShop 及其资源使用 ARR（All Rights Reserved）许可。详见 `LICENSE`。
