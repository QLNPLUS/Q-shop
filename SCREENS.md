# QShop 界面命名表

**用途**：和 AI 对话时用一句话点明要改哪个界面，避免"那个交易界面"产生歧义。
说 **中文名** 或 **稳定 ID** 都可以，两者一一对应。

**这份文件在三条分支上内容完全相同**，改动界面后请同步更新（见文末「维护约定」）。

---

## 界面清单

| 中文名（对话用） | 稳定 ID | Java 类 | 源文件 | 说明 |
|---|---|---|---|---|
| 商店主界面 | `SHOP` | `ShopScreen` | `client/ShopScreen.java` | 左侧子商店 tab 栏 + 7x3 / 8x4 交易格子网格；支持平滑滚动、搜索、编辑模式拖拽排序 |
| 交易悬浮窗 | `TRADE_POPUP` | `ShopScreen` 内部浮层 | `client/ShopScreen.java`（字段 `:110-131`、`openTrade():1574`、`drawTradePanel():1923`） | 点格子弹出的数量确认小窗：数量输入框、滑块、x1/x10/x100/x1000 步长按钮、确认/取消 |
| 交易条目设置 | `TRADE_SETTINGS` | `ShopEditDialog`（改）/ `ShopAddDialog`（增） | `client/ShopEditDialog.java` / `client/ShopAddDialog.java` | 编辑或新增一条交易：类型、展示物品、玩家付出/商店获得、价格、限额、任务/阶段要求、指令 |
| 物品浏览器 | `ITEM_PICKER` | `ItemPickerScreen` | `client/ItemPickerScreen.java` | 全部物品 / 背包物品切换 + 搜索 + 分页网格，选一个物品返回 |
| 子商店设置 | `TAB_SETTINGS` | `TabEditDialog` | `client/TabEditDialog.java` | 编辑一个子商店(tab)：名称、图标、描述、任务/阶段要求、未满足时是否显示 |
| 商店信息 | `SHOP_INFO` | `ShopInfoDialog` | `client/ShopInfoDialog.java` | 编辑商店本身：商店名称、图标、默认货币 |
| 物品 NBT 编辑器 | `ITEM_NBT` | `ItemNbtScreen` | `client/ItemNbtScreen.java` | 编辑单个物品的数量与 NBT(SNBT 文本框) |

> `TRADE_SETTINGS` 对应**两个**类：新增走 `ShopAddDialog`，编辑走 `ShopEditDialog`。两者布局几乎相同，
> 说"交易条目设置"时请一并说明是**新增**还是**编辑**。

### 全局共享控件（改它们会同时影响所有界面）

说"按钮文字"、"勾选框"、"滑块"这类问题时，命中的是下面这几个类，会一次性影响上表**全部**界面：

| 控件 | 类 | 文件 |
|---|---|---|
| 按钮 | `QButton` | `client/QButton.java` |
| 图标按钮（关闭/布局/加号/编辑/搜索） | `QIconButton` | `client/QIconButton.java` |
| 勾选框 | `QCheckbox` | `client/QCheckbox.java` |
| 滑块 | `QSlider` | `client/QSlider.java` |
| 带 § 的输入框 | `QEditBox` | `client/QEditBox.java` |
| 多行文本框 | `MultilineTextBox` | `client/MultilineTextBox.java` |
| 全部 GUI 材质 | `ShopTextures` | `client/ShopTextures.java` |
| 界面基类（局部缩放、坐标换算、滚轮缩放） | `QShopScreen` / `QShopScreenInput` | `client/QShopScreen.java` / `client/QShopScreenInput.java` |

---

## 代码里的同一套名字

`client/ShopLayoutDebug.java` 的 `DebugScreen` 枚举登记了同样的标识符，供 F8 布局调试器显示：

```java
SHOP, TRADE_POPUP, TRADE_SETTINGS, ITEM_PICKER, TAB_SETTINGS, SHOP_INFO, ITEM_NBT
```

例外一处：`TRADE_POPUP` **不会**传给 `ShopLayoutDebug.beginScreen(...)`。它是 `ShopScreen` 内部的浮层而不是独立
`Screen`，浮层开合时改写 `activeScreen` 会污染宿主界面的偏移表。它登记在枚举里只为让代码名与上表对得上。

---

## 维护约定

- **新增界面**：往本表加一行（中文名 + 稳定 ID + 类 + 文件），并在 `DebugScreen` 枚举里加同名常量。
- **重命名 / 删除界面**：本表与枚举同步改；本表在**三条分支上必须逐字节相同**，否则各分支上的 AI 会读到不同约定。
- **稳定 ID 一旦被对话引用过就不要改**，改名等于让之前所有对话记录失去指向。中文名可以调整措辞。
- 界面内的**局部区域**（如交易悬浮窗里的"数量输入框"、"滑块"）不单独登记 ID，直接用中文描述局部位置即可。
