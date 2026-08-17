# LM Studio 视觉助手（记忆/使用说明）

本目录把「看图能力」做成可复用、可持久化的本地工具，供任何会话在需要分析图片时调用。

## 何时使用

- 当任务需要「看懂一张图片」（截图、论文图、生信图、图表、表格截图、图标等）。
- **主对话模型（deepseek）本身不支持图片输入**；真正看图靠本机的 LM Studio 视觉模型（qwen3.5-9b）。

## 架构（重要，务必按此执行）

```
[deepseek 主模型，无视觉]  ->  探测可达地址  ->  调用 LM Studio qwen3.5-9b(VL) 看图  ->  拿文字描述回去分析
```

- **主模型**：负责理解任务、编排、处理返回的文字。
- **视觉模型**：`qwen/qwen3.5-9b` 支持图片输入，负责把图片转成文字描述。
- **看图请求发给哪个地址不固定**：取决于当前网络状态，必须**先探测**。

## 三个候选地址（按网络状态共享同一 LM Studio）

| 地址 | 适用网络状态 |
|---|---|
| `http://192.168.1.3:1234` | 电脑接入本地网络（当前通常最快） |
| `http://10.0.0.1:1234` | 使用 astral 时 |
| `http://www.arknightsendfield.top:1234` | 通过域名访问 |

编辑 `endpoints.json` 可增删地址、改 apiKey / 模型。

## 执行步骤（推理时必须遵守）

1. **先探测，后调用**。用 `probe.py` 自动找到当前最快可达的端点，不要写死某个地址。
2. 请用户提供**本地图片的绝对路径**（harness 沙箱只能读到 workspace 与用户显式给出的路径）。
3. 用探测结果里**可达的 base URL** 发起看图请求。

### 探测（只找地址，不调图片）

```
python "C:\Users\QLN\deepseek-harness\workspace\lm-studio\probe.py"
```

### 探测 + 描述图片（自然语言中文）

```
python "C:\Users\QLN\deepseek-harness\workspace\lm-studio\probe.py" "C:\Users\QLN\Pictures\新建文件夹\icon_4_2.png"
```

### 结构化输出（JSON，便于程序化处理）

```
python "C:\Users\QLN\deepseek-harness\workspace\lm-studio\probe.py" "C:\path\to\image.png" --json
```

## 工具说明

- `endpoints.json` — 候选地址、apiKey、视觉模型名、超时配置。
- `probe.py` — 纯标准库（urllib），并行短超时探测三个地址，挑最快可达者，再调视觉模型描述图片。
- **安全**：脚本不会主动打印 apiKey；请勿把它提交到任何仓库或分享。

## 已知观察

- qwen3.5-9b 在纯中文 prompt 下可能倾向用英文回复；需要精确结果时可用 `--json` 结构化为 Chinese JSON，或改用英文 prompt。
- 401/403 响应也算服务可达（服务存在、需要正确鉴权）；只有连接失败 / 超时才判为不可达。
