# QShop —— Forge 1.20.1 服务器商店模组

一个灵活的多功能商店模组:交易 GUI、多种非物品货币、购买/出售/以物换物、游戏内编辑、
多商店(id / uuid)、KubeJS 集成、全服/个人限购、购买后执行指令。

当前版本：**1.1.0**（Forge 1.20.1）

## 功能清单

| 需求 | 实现 |
| --- | --- |
| 1. 交易界面 | 客户端 GUI:网格布局(列×行),条目显示物品+价格;点击条目弹出**交易子窗口**,用**滑块+输入框**设置数量(滑块上限按余额/背包/限额自动计算) |
| 2. 多种非物品货币 | 货币定义在 `serverconfig/qshop/currencies.json`,余额保存在玩家数据(Capability);每个商店可配置 `currency` 作为**默认展示货币**,主界面只显示该货币余额 |
| 3. 购买 / 出售 / 一次性数量 | 交易子窗口里通过滑块/输入框自由设置每次交易数量(单位数);条目物品的 `count` 表示一个交易单位包含的物品数量 |
| 4. 以物换物 | `BARTER` 类型条目(give ⇄ receive,可附加货币费用),格子上显示需求物品数量+图标;另有 `COMMAND` 类型:支付货币后执行指令(无物品交换) |
| 5. 游戏内编辑 | **仅创造模式**显示编辑模式(GUI):点击条目编辑价格/货币/数量/限购/重置周期/指令(提权、静默用**勾选框**);"添加条目"按钮支持购买/出售/交换/指令四种类型,字段随类型联动;垃圾桶图标删除条目;另有 `/qshop edit` 指令 |
| 6. 多商店 | 一个商店一个 JSON 文件,以 `id` 或自动生成的 `uuid` 区分 |
| 7. KubeJS 打开商店 | 脚本绑定 `QShop.open('id', player)` / `QShop.openByUuid('uuid', player)` |
| 8. 限购 | 每个条目可设 `globalLimit`(全服)和 `playerLimit`(个人),按物品件数统计,支持每日/每周/每月自动重置 |
| 9. 购买指令 | 交易完成后执行指令,每条可设 `op`(提权到 4 级)和 `silent`(静默) |
| 10. KubeJS 商店管理 | Builder-first: `QShop.entry(...).add()` / `QShop.tab(...).add()` / `QShop.getShop/getTab/getEntry` |
| 11. 材质 GUI | 界面元素全部使用**拆分贴图**(`assets/qshop/textures/gui/` 下一个元素一个 PNG:面板/格子/按钮/输入框/滑块/勾选框/图标),可直接单独修改;带边框元素九宫格绘制不拉伸 |

## 构建

- 需要 JDK 17(本机路径已写在 `gradle.properties` 的 `org.gradle.java.home`,不对请修改)
- 首次构建需要联网下载依赖

```bash
# 如果 gradle/wrapper/gradle-wrapper.jar 不存在,先下载(需要联网):
curl -L -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.1.1/gradle/wrapper/gradle-wrapper.jar

gradlew.bat build          # Windows
# 产物在 build/libs/qshop-1.1.0.jar
```

也可以直接用 IntelliJ IDEA 打开 `build.gradle` 导入,运行 `runClient` / `runServer` 调试。

## CurseForge 自动发布

仓库包含 `.github/workflows/curseforge-publish.yml`。创建并发布 GitHub Release 后, GitHub Actions 会自动构建 Java 17 的 Forge 1.20.1 JAR, 并将其上传到 CurseForge。也可以从 Actions 页面手动触发工作流补发指定 tag。

首次使用前,在 GitHub 仓库的 `Settings -> Secrets and variables -> Actions` 中配置:

- Repository variable `CURSEFORGE_PROJECT_ID`: CurseForge 项目数字 ID(不是项目 slug)
- Repository secret `CURSEFORGE_TOKEN`: CurseForge API token

`CF_DESCRIPTION.md` 是用于复制到 CurseForge 项目页面的文档, CurseForge API 发布文件时不会自动修改项目描述;项目描述仍需在 CurseForge 后台更新。

> KubeJS 是可选依赖(坐标 `dev.latvian.mods:kubejs-forge`)。若依赖解析失败:
> 注释 `build.gradle` 里 `compileOnly fg.deobf(...)` 两行,删除
> `src/main/java/com/qshop/kubejs` 目录和
> `src/main/resources/META-INF/services/dev.latvian.mods.kubejs.KubeJSPlugin` 文件即可(仅失去 KubeJS 功能)。

## 配置文件(serverconfig)

所有配置在**世界存档**的 `serverconfig/qshop/` 目录下(专用服务器:
`<server>/world/serverconfig/qshop/`;单人存档:`saves/<存档>/serverconfig/qshop/`)。

```
serverconfig/qshop/
├── currencies.json        # 货币定义
└── shops/                 # 一个商店一个文件,文件名 = 商店 id
    ├── starter.json
    └── my_shop.json
```

首次启动会自动生成默认 `currencies.json` 和示例商店 `starter.json`。
修改后执行 `/qshop reload` 热加载。

### 新存档默认导入

如果服务器根目录存在 `defaultconfigs/qshop/`，QShop 会在新存档首次加载时将其中的货币和商店 JSON 自动复制到该世界的 `serverconfig/qshop/`。已有世界配置不会被覆盖。

```text
defaultconfigs/qshop/
├── currencies.json
└── shops/
    ├── sdm.json
    └── vip.json
```

## 死亡货币配置

通用配置文件为 `config/qshop-common.toml`，首次启动后自动生成。默认情况下玩家死亡不会减少货币：

```toml
[death]
loseCurrencyOnDeath = false
defaultCurrencyRetention = 0.0
currencyRetention = []
```

开启后，`defaultCurrencyRetention` 是未单独配置货币的保留比例；`0.2` 表示死亡后保留 20%。可使用 `currencyRetention` 为不同货币设置覆盖值：

```toml
[death]
loseCurrencyOnDeath = true
defaultCurrencyRetention = 0.0
currencyRetention = ["coins=0.2", "points=0.5"]
```

上例表示死亡后保留 20% 的 `coins`、50% 的 `points`，其他货币全部清空。比例范围为 `0.0` 到 `1.0`；也接受 `coins:0.2` 写法。

### currencies.json

```json
{
  "currencies": [
    { "id": "coins",  "name": "金币", "color": "#FFD700" },
    { "id": "points", "name": "点数", "color": "#55FFFF" }
  ]
}
```

### 商店文件

```json
{
  "id": "starter",
  "uuid": "自动生成,缺失时补写",
  "displayName": "新手商店",
  "currency": "coins",
  "icon": "minecraft:emerald",
  "entries": [
    {
      "type": "SELL",
      "item": { "item": "minecraft:diamond", "count": 1, "nbt": "{...}" },
      "currency": "coins",
      "price": 50.0,
      "globalLimit": -1,
      "playerLimit": -1,
      "limitReset": "NEVER",
      "commands": [
        { "command": "say %player% 卖了一颗钻石!", "op": false, "silent": true }
      ]
    }
  ]
}
```

字段说明:

| 字段 | 含义 |
| --- | --- |
| `currency` | (商店级)默认展示货币 id,主界面只显示该货币余额;留空则显示第一种货币 |
| `type` | `BUY` 玩家买(付货币得物品)/ `SELL` 玩家卖(交物品得货币)/ `BARTER` 以物换物 / `COMMAND` 指令购买(付货币执行指令,`item` 可留空作图标) |
| `displayName` | (可选)自定义标题,悬浮提示显示;留空则用物品名 |
| `description` | (可选)自定义描述,悬浮提示显示;未设置标题/描述时显示物品原本 tooltip |
| `displayItem` | (可选)展示物品,仅作显示(可用改名物品),与实际交易物品无关;留空显示实际物品 |
| `item` | BUY/SELL 的物品。三种写法:`"minecraft:diamond"`、`{"item":"minecraft:diamond","count":4,"nbt":"{...}"}`、模组保存的 base64 |
| `give` / `receive` | BARTER 的付出/获得物品列表(数组,元素写法同上) |
| `currency` | (条目级)价格货币 id(BARTER 中留空 `""` 表示无额外费用) |
| `price` | 每个交易单位的价格(整数;BARTER 为附加费用) |
| `globalLimit` | 全服限购(按物品件数),`-1` 不限;到周期自动重置 |
| `playerLimit` | 个人限购(按物品件数),`-1` 不限 |
| `limitReset` | `NEVER` / `DAILY` / `WEEKLY` / `MONTHLY` |
| `commands` | 交易完成后执行的指令,见下 |

### 购买指令(commands)

每条指令对象:

```json
{ "command": "give %player% minecraft:diamond 1", "op": true, "silent": true }
```

- `command`:指令内容,不带开头的 `/`,支持占位符
- `op`:是否以 4 级权限(控制台权限)执行;`false` 则 0 级
- `silent`:是否静默(抑制指令成功消息输出)

占位符:`%player%`、`%player_uuid%`、`%shop%`、`%shop_uuid%`、`%entry%`(条目序号)、
`%units%`(交易单位数)、`%items%`(物品件数)、`%price%`、`%currency%`、`%multiplier%`。

## 指令

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/qshop open <id或uuid>` | 无 | 打开商店 |
| `/qshop open <id或uuid> <玩家>` | op2 | 替别人打开 |
| `/qshop list` | 无 | 列出所有商店(id、uuid、条目数) |
| `/qshop balance` | 无 | 查看自己的货币余额 |
| `/qshop currency list` | 无 | 列出货币 |
| `/qshop currency give/take/set <玩家> <货币> <数量> [true\|false]` | op2 | 货币管理；最后参数控制是否触发货币变动事件，省略时为 `true` |
| `/qshop reload` | op2 | 重新加载商店/货币配置 |
| `/qshop edit <商店> add <buy\|sell\|barter\|command> [价格] [货币]` | op2 | 用手持物品添加条目(BARTER:主手=获得物,副手=付出物;COMMAND:无需物品) |
| `/qshop edit <商店> remove <序号>` | op2 | 删除条目 |
| `/qshop edit <商店> setitem <序号>` | op2 | 用主手物品替换条目物品(BARTER 同时用副手替换付出物) |
| `/qshop edit <商店> set <序号> <字段> <值>` | op2 | 修改字段:price / currency / quantity / globallimit / playerlimit / reset |
| `/qshop item` | op2 | 打印手持物品的 base64(手写配置文件时用) |

游戏内编辑 GUI(仅创造模式):管理员打开商店后,点右上角"编辑模式":
- **添加条目**:点"添加条目"按钮,类型可切换 **购买/出售/交换/指令**,字段随类型联动:
  - 购买/出售:选**交易物品**(内置物品选择器:全物品/背包双模式+搜索),填价格/货币/单次数量
  - 交换:分别选择**获得物品**与**付出物品**(不显示价格/货币)
  - 指令:选**展示图标**(可选),填价格/货币/指令文本
- **删除条目**:条目格子右上角出现垃圾桶图标,点击即删除
- **编辑条目**:点击任意条目,按类型显示对应字段(交换显示获得/付出物品而非货币);可修改标题、描述、展示物品(与实际交易物品分离,如用改名物品当展示)、交易物品(**支持修改数量与 NBT**)、价格(整数)/货币/限购/重置周期;指令支持最多 4 条,每条含指令输入框 + **提权/静默勾选框**
- 子窗口"取消"均返回主商店界面
保存后立即生效并写回配置文件。悬浮条目时:未设置标题/描述则显示物品原本 tooltip;设置了则显示自定义 tooltip(标题+描述+价格/限购)。网格滚动带平滑动画,页码实时更新。

## KubeJS

需要安装 KubeJS(1.20.1,`kubejs` 模组)。如果使用 `requiredStages`，标准依赖是
GameStages（模组 ID：`gamestages`）；QShop 也会兼容 KubeJS PlayerStages。
脚本里直接使用全局绑定 `QShop`。

> 当前公开 API 已重构为 Builder-first：交易条目使用 `QShop.entry(...).uuid(...).add()` 创建或按 UUID 覆盖，子商店使用 `QShop.tab(...).uuid(...).add()` 创建或更新。旧版 `addEntry/updateEntry/addTab/updateTab` 与 `JsonIO.of` CRUD 不再作为全局 API 暴露。对象查询使用 `QShop.getShop/getTab/getEntry`。完整说明请参阅 [`KUBEJS_WIKI.md`](KUBEJS_WIKI.md) 或 [`KUBEJS_WIKI_CN.md`](KUBEJS_WIKI_CN.md)。

### 1.1.0 当前 API

```js
QShop.createShop('vip', 'VIP 商店', 'coins')

QShop.tab('vip')
  .name('每日特惠')
  .icon({ item: 'minecraft:paper', count: 1, nbt: '{display:{Name:"每日卡片"}}' })
  .uuid('daily-offers')
  .stage('vip_unlocked')
  .add()

QShop.entry('vip', 'daily-offers')
  .buy({ item: 'minecraft:oak_log', count: 8 })
  .price(2, 'coins')
  .uuid('oak-bundle')
  .add()

const entry = QShop.getEntry('vip', 'daily-offers', 'oak-bundle')
console.log(entry.type.name(), entry.count, entry.getCount())
```

交易事件中的商店层级通过对象读取：

```js
QShopEvents.afterTrade(event => {
  const shop = event.getShop()
  const tab = event.getTab()
  const entry = event.getEntry()
  console.log(`${shop.id}/${tab.uuid}/${entry.uuid}`)
  console.log(`${entry.type.name()} x${entry.count}, paid=${event.paidPrice}`)
})
```

货币变动事件：

```js
QShopEvents.currencyChanged(event => {
  console.log(`${event.getPlayer().getGameProfile().getName()} ${event.getCurrency()}: `
    + `${event.getOldValue()} -> ${event.getNewValue()}`)
})
```

`currencyChanged` 在所有实际余额变化后触发，包括交易购买/出售、FTB 货币奖励、
FTB 货币任务消耗、KubeJS 货币方法、配置的死亡货币保留，以及
`/qshop currency give/take/set`（末尾参数省略或为 `true`）。事件提供
`getPlayer()`、`getCurrency()`、`getOldValue()`、`getNewValue()`、`getDelta()`、
`getSource()` 和 `getSourcePos()`。通过 `QShop.giveCurrency/takeCurrency/setCurrency`
修改余额也会触发此事件(余额实际发生变化时)。

相同 UUID 的 `EntryBuilder.add()` 会替换已有交易项目；相同 UUID 的 `TabBuilder.add()` 会更新子商店信息并保留已有交易项目。

## Java 附属模组 API

1.1.0 提供面向 Forge 附属模组的正式 API，入口为 `com.qshop.api.QShopAddonApi`：

```java
QShopAddonApi.currency().deposit(player, "coins", 25,
        ResourceLocation.fromNamespaceAndPath("my_mod", "auto_sell"), blockPos);

TradeResult result = QShopAddonApi.sell(
        player, itemHandler, "vip", tabIndex, entryIndex, 16,
        ResourceLocation.fromNamespaceAndPath("my_mod", "auto_sell"), blockPos);
```

`sell` 和 `buy` 支持 `IItemHandler`，会复用条目要求、限购、交易前/后事件和货币变动事件。容器附属应保存放置者 UUID；当前钱包绑定在线玩家，离线玩家不会执行需要钱包的容器交易。

Java 附属可以监听 Forge `com.qshop.api.CurrencyChangedEvent`。事件提供玩家、货币、变化前后余额、差值、来源 `ResourceLocation` 和可选的来源方块坐标。

### 历史 JSON API（1.0.4 不可用）

下面的 JSON CRUD 示例仅保留用于迁移旧脚本，不能在 1.0.4 中直接调用。新脚本请使用上面的 Builder API和中文/英文 Wiki。

本节中早期 JSON 示例仅保留作历史记录。

```js
// server_scripts 示例

// 打开商店(通过 id 或 uuid)
QShop.open('starter', event.player);
QShop.openByUuid('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx', event.player);

// 货币
QShop.giveCurrency(event.player, 'coins', 100);
QShop.takeCurrency(event.player, 'coins', 10);
QShop.setCurrency(event.player, 'points', 50);
let b = QShop.getBalance(event.player, 'coins');

// 创建商店并增删改交易项目(JsonObject 用 JsonIO.of({...}) 构造,或直接传 JS 对象)
QShop.createShop('vip', 'VIP 商店');

QShop.addEntry('vip', JsonIO.of({
  type: 'SELL',
  item: 'minecraft:diamond',
  price: 100, currency: 'coins',
  globalLimit: 100, playerLimit: 10
}));

QShop.addEntry('vip', JsonIO.of({
  type: 'BUY',
  item: { item: 'minecraft:oak_log', count: 8 },
  price: 2, currency: 'coins'
}));

QShop.addEntry('vip', JsonIO.of({
  type: 'BARTER',
  give: ['minecraft:emerald'],                       // 3 个绿宝石
  receive: [{ item: 'minecraft:diamond', count: 1 }] // 换 1 个钻石
}));

QShop.updateEntry('vip', 0, JsonIO.of({           // 替换第 0 条
  type: 'SELL', item: 'minecraft:netherite_ingot', price: 500, currency: 'coins'
}));
QShop.removeEntry('vip', 1);
QShop.removeShop('vip');                             // 删除商店及其配置文件
QShop.reload();

// 子商店(tab):推荐选项对象形式,支持全部子商店字段
QShop.addTab('vip', {
  name: '每日卡池',
  icon: 'minecraft:paper',           // 可选
  uuid: 'daily-tab-uuid',            // 可选,留空随机
  description: '每日刷新',           // 可选,悬停 tooltip
  requiredQuests: ['quest-1'],       // 可选,FTB 任务门槛
  requiredStages: ['vip']            // 可选,阶段门槛
});
QShop.updateTab('vip', 'daily-tab-uuid', {
  name: '新名字',
  description: '新描述',
  icon: null,                        // null 清除图标
  requiredQuests: [],                // 空数组清空列表
  requiredStages: ['vip']
});
// 旧式写法仍可用:QShop.addTab('vip', '武器', 'minecraft:iron_sword', 'uuid');
//           QShop.updateTab('vip', 0, '新名'); / QShop.updateTabByUuid('vip', uuid, '新名', null);

// 刷新子商店:第二个参数是 tab UUID 或从 0 开始的数字索引,不是 tab 名称
// 池条目 = 标准 ShopEntry JSON(与 addEntry 同格式,支持全部 16 个字段)+ weight(权重)
// 'daily-tab-uuid' 必须是子商店的 uuid
QShop.refreshTab('card', 'daily-tab-uuid', 10, [
  { type: 'BUY', item: { item: 'minecraft:diamond', count: 1, nbt: '{...}' },
    price: 1000000, currency: 'coins', globalLimit: 50, playerLimit: 5,
    limitReset: 'DAILY', displayName: '锋利钻石', weight: 20 },
  { type: 'BARTER', give: [{ item: 'minecraft:emerald', count: 3 }],
    receive: [{ item: 'minecraft:netherite_ingot', count: 1 }], weight: 10 },
  { type: 'COMMAND', commands: [{ command: 'give %player% minecraft:elytra 1', op: true, silent: true }],
    price: 5000, weight: 5 },
  { type: 'SELL', item: 'minecraft:iron_ingot', price: 100, weight: 30 }
]);
// 数字索引形式:0 表示第一个子商店
// 选项形式:QShop.refreshTab('card', 0, { count: 10, currency: 'coins', pool: [...] });
// 每次生成全新 uuid(限购从零开始);未知物品跳过并警告。
```

限购清理方法的 `tabRef` / `entryRef` 支持数字索引或 UUID，不支持名称；`shopRef` 支持商店 ID 或商店 UUID：

```js
QShop.clearEntryLimits('card', 0, 0) // 第一个 tab 的第一个条目
QShop.clearTabLimits('card', 0)      // 第一个 tab 的全部条目
QShop.clearShopLimits('card')
```

### 历史 JSON 字段参考（1.0.4 不可用于写入）

**交易条目(ShopEntry)共 16 个字段**,`addEntry/updateEntry/updateEntryByUuid/refreshTab` 池条目与商店 JSON 文件均用同一套 schema:

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `uuid` | string | 稳定标识,缺省自动生成;refreshTab 生成时总是新 uuid |
| `type` | BUY/SELL/BARTER/COMMAND | 默认 BUY |
| `displayName` | string | 自定义标题(留空用物品名) |
| `description` | string | 描述(悬浮提示) |
| `displayItem` | item | 仅展示用物品(可与交易物品不同) |
| `item` | item | BUY/SELL/COMMAND 的交易物品(含数量与 NBT) |
| `give` / `receive` | item[] | BARTER 付出/获得列表 |
| `currency` | string | 价格货币 id(留空=商店默认货币) |
| `price` | number | 每单位价格 |
| `globalLimit` | int | 全服限购,-1 不限 |
| `playerLimit` | int | 个人限购,-1 不限 |
| `limitReset` | NEVER/DAILY/WEEKLY/MONTHLY | 限购重置周期 |
| `commands` | `[{command,op,silent}]` | 购买后执行的指令 |
| `requiredQuests` | string[] | FTB 任务门槛 |
| `requiredStages` | string[] | 阶段门槛 |

物品写法三种:`"minecraft:diamond"`、`{"item":"minecraft:oak_log","count":8,"nbt":"{...}"}`、base64。

**子商店(ShopTab)共 7 个字段**,经 `addTab(shopId, options)` / `updateTab(shopId, tabRef, options)` 设置:

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `uuid` | string | 稳定标识,缺省自动生成 |
| `name` | string | 子商店名 |
| `icon` | item | 图标 |
| `description` | string | 描述(悬停 tooltip) |
| `entries` | entry[] | 交易条目(由条目 API 管理) |
| `requiredQuests` | string[] | FTB 任务门槛(未满足时非编辑玩家看不到该子商店) |
| `requiredStages` | string[] | 阶段门槛 |

注意:`updateTab` 只更新 options 里出现的字段;`icon: null` 清除图标,`requiredQuests: []` 清空列表。
`addTab/updateTab` 的旧式位置参数写法(名称/图标/uuid)仍然兼容。

## 限制系统说明

- 限购按**物品件数**统计:BUY/SELL 以 `item.count` 计,BARTER 以 receive 总件数计
- 全服计数保存在世界存档(`qshop_data`),个人计数保存在玩家数据
- `limitReset` 决定计数周期:`DAILY` 每天 0 点、`WEEKLY` 每周一、`MONTHLY` 每月 1 号自动清零
- 交易时若余额/库存/限额不足,会自动按可交易的最大数量成交并提示"实际完成 N 个交易单位"
- 死亡重生/切换维度不会丢失货币和个人限购计数

## 注意事项

- 商店与货币配置属于"世界",不同世界/服务器互不影响
- 服务端与客户端都装本模组即可;纯服务端安装时,玩家客户端需要同版本模组才能看到 GUI
- `price` 为 0 时表示免费(购买/出售不产生货币变动)
- **KubeJS 脚本时机**:`ServerEvents.loaded` 阶段 `ShopManager` 可能尚未初始化,
  脚本内 createShop/addTab/refreshTab 等写操作会自动取当前服务器引用自愈,可直接使用;
  更稳妥的做法是放在 `ServerEvents.tick` / 玩家事件 / 指令中执行
- **refreshTab 池条目**:必须是标准 ShopEntry JSON(`item`/`give`/`receive` 至少一项,
  COMMAND 需 `commands`),物品 id 未知/未安装模组会被跳过并打印警告;
  每次刷新生成全新 uuid,限购计数从零开始
- **KubeJS 对象参数**:JS 对象传给 `JsonObject` 形参会自动转换(如 `refreshTab` 的 options);
  传给 `Object` 形参时按原样传递(如 `addTab`/`updateTab` 的第二/三参),内部已做兼容转换,
  字符串=名称、对象=完整选项,两种写法均可
