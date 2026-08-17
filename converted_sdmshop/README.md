# SDMShop → QShop 转换结果

由 `D:\Minecraft\versions\Last One New\config\SDMShop\sdmshop.snbt` 转换而来,已在 QShop 服务器实测加载成功
(3 种货币 / 11 个 tab / 268 条有效条目,零解析警告)。

## 文件

| 文件 | 说明 |
|---|---|
| `shops/sdm.json` | 商店配置(id=`sdm`,默认货币 `base_money`,11 个子商店) |
| `currencies.json` | 货币表:在原 coins/points 基础上追加 `base_money`(货币) |

## 安装

1. 把 `shops/sdm.json` 放入 QShop 商店目录:
   - 单人/服务器世界: `<世界目录>/serverconfig/qshop/shops/`
   - 例如正式服: `world/serverconfig/qshop/shops/sdm.json`
2. 把 `currencies.json` 放到 `<世界目录>/serverconfig/qshop/currencies.json`
   (如果已有文件,手动把 `base_money` 条目加进去即可,别覆盖自己加的货币)
3. 开服后执行 `/qshop reload`,或 `/qshop open sdm` 打开商店。

> 也可用 KubeJS 导入:把 `sdm.json` 内容用 `JsonIO` 读入后 `QShop.reload()`。

## 转换映射

| SDMShop | QShop |
|---|---|
| `shopTabs[].title` | 子商店名(默认货币 `base_money`) |
| `tab.icon` | 子商店图标(带 NBT) |
| `entry.icon` | 条目 `displayItem`(`minecraft:barrier` 占位图标已跳过 → 显示真实物品) |
| `entryType.itemStack` + `entryCount` | `item`(id + 数量 + NBT,SNBT 已补逗号转成标准格式) |
| `isSell: 0` | `BUY`(玩家用 `base_money` 货币购买) |
| `entryPrice` | `price` |
| `moneyID` | `currency` |
| `limit` | `playerLimit`(重置周期默认 NEVER,原版无周期) |
| `entry.title` | `displayName` |
| `entry.description[]` | `description`(多行用换行连接) |
| `entryCondition.ftbquest.questID[]` | `requiredQuests`(FTB 任务 id 原样保留,需服务器装有 FTB Quests 才生效) |
| `entryCondition.gamestages[]` | `requiredStages`(原配置全部为空) |
| `commandType` | `COMMAND` 条目(`command` 去掉开头 `/`,`elevatePerms`→op,`silent`→silent,`iconPathNew`→displayItem) |

## 注意事项

1. **货币不互通**:SDMShop 的 `base_money` 余额是它自己的钱包;QShop 用**自己的钱包**。
   玩家现有 `base_money` 余额不会带过来,需要重新发放(用 `/qshop currency give <玩家> base_money <数额>` 或 KubeJS `QShop.giveCurrency`),或按原 mod 的余额数值手动补。
2. **黑市商人 tab 为空**:原配置里该 tab 全是 `minecraft:air` 占位条目(价格 0),已全部跳过;
   tab 本身保留(含图标),等你填真实条目。
3. **tab 描述被丢弃**:QShop 的子商店没有描述字段,原 tab 的 `description`(如黑市商人的说明)未转换,需要的话可以加在条目描述里。
4. **FTB 任务门槛**:带 `requiredQuests` 的条目约 46 条,`58DC8A0E71CC0EC2` / `6AB131BC9863A69F` 等 id 原样保留。
   未装 FTB Quests 的服务器上这些条目会按"条件未满足"锁定(这是 QShop 的既有行为)。
5. **限购无重置**:原 `limit` 转换成了 `playerLimit`,重置周期为 NEVER(一次性)。原配置没有周期概念。

## 数据统计

- 11 个 tab:黑市商人 / 枪械补给(23) / 枪械配件补给(39) / 职业补给(46) / 弹药补给(34) / 配饰补给(15) / 综合补给(49) / 存储升级补给(39) / 打印凭证补给(1) / 特殊商店(11) / 测试(11)
- 268 条有效条目(308 条中跳过 40 条空气占位)
- 8 条 COMMAND 指令条目(技能点 / 栏位扩展,命令为 `puffish_skills` / 槽位类)

## 重新生成

转换脚本:`tools/sdm_to_qshop.ps1`(Q-shop 项目内),用法:

```powershell
pwsh -File tools\sdm_to_qshop.ps1 -SnbtPath <sdmshop.snbt> -OutDir <输出目录>
```
