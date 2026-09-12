# QShop — 多版本开发约定

本仓库是**一个 git 仓库、三条版本分支**，每条分支各有独立 worktree。改动默认为"先落一条分支、测试通过后再迁移到其他分支"。

**本文件在三条分支上内容完全相同。** 修改时必须三条分支同步提交同一内容，否则各分支上的 AI 会读到不同约定。

## 分支矩阵

| 分支 | worktree 路径 | 加载器 | MC | JDK | Gradle | 构建插件 |
|---|---|---|---|---|---|---|
| `forge-1.20.1` | `D:\projects\q_shop\forge-1.20.1` | Forge | 1.20.1 | **17** | 8.1.1 | ForgeGradle 6.0.54 |
| `neoforge-1.21.1` | `D:\projects\q_shop\neoforge-1.21.1` | NeoForge | 1.21.1 | **21** | 8.8 | ModDevGradle 2.0.141 |
| `neoforge-1.26.1.2` | `D:\projects\q_shop\neoforge-1.26.1.2` | NeoForge | 26.1.2 | **25** | 9.2.1 | ModDevGradle 2.0.146 |

- 主工作树（持有 `.git` **目录**）是 `D:\projects\q_shop\forge-1.20.1`；另两条是它的 linked worktree（`.git` 是**文件**，指向 `forge-1.20.1\.git\worktrees\...`）。三者共享同一个对象库，因此在一个 worktree 里 commit 的提交，可直接在另一个 worktree 里 `cherry-pick`，无需 fetch。
- 全部 tracking `origin`（`https://github.com/QLNPLUS/Q-shop.git`）。本仓库**没有 fork 远程**，推送目标就是 `origin`。
- **本地分支名与远程分支名有一处不一致**：本地 `forge-1.20.1` 跟踪的是 `origin/master`。`origin/HEAD` 也仍指向 `origin/master`。因此推送这条分支要显式写 `git push origin forge-1.20.1:master`，**不要**用裸 `git push`。
- 目录名与分支名现已一致。但仍建议用 `git rev-parse --abbrev-ref HEAD` 判定当前分支，不要相信目录名。
- 三条分支均已漂移：`forge-1.20.1` 与 `neoforge-1.21.1` 在 `f5e55e4 (1.2.3)` 分家，`neoforge-1.26.1.2` 在 `4a903d6 (1.5.0)` 从 1.21.1 分出。

## 工作流：加新功能（默认流程，不必每次询问）

1. **只改一条分支。** 默认 `forge-1.20.1`；用户指定了别的分支就用指定的那条。
2. **改动必须先提交（commit）**，再谈迁移。
3. **触发迁移的说法**：用户说"测试通过 / 可以了 / 同步到其他版本 / 另外两个版本也加上"等，即为迁移信号 —— 此时**立即**对其余分支执行 `git cherry-pick -x <sha>`，不要等用户再次点名 `cherry-pick`。
4. **迁移前逐分支判定适用性，并明确说明结论**（见下节）。
5. **禁止把一个改动在另一条分支上手工重写一遍。**

## 迁移纪律（硬规则）

**禁止手工重写。** 跨版本搬运只能用：

```powershell
git -C D:\projects\q_shop\<目标worktree> cherry-pick -x <源分支SHA>
```

`-x` 会在提交信息里记录来源 SHA，建立可追溯链接。手工重写的后果不是"多打一遍字"，而是产出**互不相关、无法追溯**的提交 —— 本仓库 1.6.2 在三条分支上是三个不同 SHA、三个不同父提交、同一个标题，正是这样来的。

**冲突是信息，不是麻烦。** cherry-pick 冲突明确指给你"这里已与源分支分叉"，那正是需要知道的位置。**不要通过重新实现来"解决"冲突** —— 那等于丢掉这条信息。

**缺陷修复是双向的。** 若在非默认分支上定位并修好了 bug，要**先 cherry-pick 回默认分支**，再流向其他分支；否则该修复只存在于一条分支，又出现两个真相源。

**首次迁移会有冲突。** 这些冲突是既有漂移的暴露，不是 cherry-pick 的缺陷。第一次建议挑边界清晰的小改动，不要一上来就搬运 1.3.0 那种 20 文件 / 1713 行的大改动。

## 迁移前的适用性判定

对每个目标分支给出结论，三类之一：

- **适用**（与加载器无关的逻辑改动）→ `git cherry-pick -x`
- **不适用**（平台相关：能力系统、GameStages、loader metadata、`mods.toml` / `neoforge.mods.toml`）→ **跳过并说明原因**，不要为了"保持一致"硬塞
- **需适配**（API 改名，如 26.1.2 的 `GuiGraphicsExtractor` / `Identifier` / `.text()`）→ **先 cherry-pick，再显式处理冲突**；绝不预先重写

## 已知平台鸿沟（真实差异，不要试图消除）

| | Forge 1.20.1 | NeoForge | 仅 26.1.2 |
|---|---|---|---|
| 网络 | `SimpleChannel` / `NetworkEvent` | `CustomPacketPayload` + `PayloadRegistrar` | — |
| 玩家数据 | `Capability` + `LazyOptional` | `AttachmentType` | — |
| 配置 | `ForgeConfigSpec` | NeoForge config | — |
| 元数据 | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | — |
| 渲染 | `GuiGraphics` | `GuiGraphics` | `GuiGraphicsExtractor` |
| 标识符 | `ResourceLocation` | `ResourceLocation` | `Identifier` |
| 文字 | `drawString` | `drawString` | `.text()` |
| 贴图 | `.blit(tex, ...)` | `.blit(tex, ...)` | `.blit(RenderPipelines.GUI_TEXTURED, tex, ...)` |
| 矩阵栈 | `pose().pushPose()` | `pose().pushPose()` | `pose().pushMatrix()` |
| tooltip | `renderTooltip` | `renderTooltip` | `setTooltipForNextFrame` |

搬运调用点时**逐个核对目标版本签名，不要直接复制** —— 这些错误能编译通过但运行时画面错。26.1.2 的 `blit` 参数顺序、六位 ARGB 文字色（alpha=0 → 不可见）、局部缩放下的 tooltip 坐标，见 `forge-gui-layering` skill。

## Release Tag

格式：**`v<version>-<loader>-<mcversion>`**，前缀统一用 `v`：

```
v1.6.3-forge-1.20.1
v1.6.3-neoforge-1.21.1
v1.6.3-neoforge-1.26.1.2
```

git tag 是仓库级唯一的，而本仓库是锁步发布 —— 只打一个 `v1.6.3` 无法指认是哪个版本/加载器。

**已存在的无版本维度 tag（`v1.6.2`、`1.2.4`、`v1.2.4` 等）不追溯改名**，保持现状，只对后续新 tag 应用本格式。

## 构建

- **JDK 必须对上目标版本**（17 / 21 / 25），否则出现 `Unsupported class file major version`。本机路径：`C:\Program Files\Java\jdk-17`、`jdk-21`、`jdk-25.0.4.1`。
- 三棵树**串行**构建，不要并行 —— 会争用 Gradle 缓存与内存。
- 使用各自的 Gradle wrapper（`.\gradlew.bat`），不要用系统 gradle。
- 发布产物名：`qshop-forge-1.20.1-<ver>.jar` / `qshop-neoforge-1.21.1-<ver>.jar` / `qshop-neoforge-26.1.2-<ver>.jar`。
- 发布后在仓库根产出 `build/local-repo` 之类的临时目录不要提交（见 `.gitignore`）。

## CI

`.github/workflows/curseforge-publish.yml`（三条分支各一份，内容相同）按 ref 分别 checkout 三条分支：

- Forge 任务：`github.event.release.tag_name || inputs.tag || github.ref`（即当前分支）
- NeoForge 任务：`inputs.neoforge_ref`，默认 `neoforge-1.21.1`
- NeoForge 26.1.2 任务：`inputs.neoforge_126_ref`，默认 `neoforge-1.26.1.2`

因此**分支名是 CI 的契约**：若将来把 `origin/master` 改名为 `origin/forge-1.20.1`，workflow 里 Forge 任务的默认行为与 `origin/HEAD` 需要同步更新。

## 推送

本仓库只有 `origin`，没有 fork 远程。**未经用户明确要求不要推送**，尤其不要 force-push 或推送分支改名。

## 沙箱注意

在 linked worktree（`neoforge-1.21.1`、`neoforge-1.26.1.2`）里工作时，项目根的判定是**该 worktree 自身** —— 不会上溯到父目录。因此 `AGENTS.md` 必须**每条分支各提交一份**，放在 `D:\projects\q_shop\` 根目录（那里没有 `.git` 标记）是读不到的。
