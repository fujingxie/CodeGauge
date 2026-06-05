# CodeGauge 产品架构与实施方案

> **代号 CodeGauge**（代码油表，可自由改名）——盯着你 AI 编程订阅"还剩多少油 + 啥时候满血 + 现在在跑啥"的安卓仪表盘。
> **文档版本**：v1.0
> **产品形态**：本地常驻程序（Companion）+ Android App（同一 WiFi 直连，无云、无账号、无后端）
> **目标用户**：用 Claude Code（Pro/Max 订阅）+ Codex CLI（ChatGPT 订阅）做开发的重度个人用户（即你自己）

---

## 〇、核心约束（所有方案的前提，coding agent 不得改动）

1. **无云、无后端、无账号系统**。数据源在用户本地电脑，App 与 Companion **同一局域网直连**。
2. **跨平台 Companion**：必须同时支持 macOS / Windows / Linux。
3. **数据源降级策略**：精确额度优先用现成工具（`ccusage`）/本地文件，逆向官方接口只作可选增强，坏了不影响主功能。
4. **已知边界（产品设计已接受，非 bug）**：
   - 不在同一 WiFi 时收不到实时数据/通知（这是 LAN-only 的代价；二期可加云中转）。
   - Codex 的实时"coding 状态"弱于 Claude（Codex 的 hook 机制不成熟）。
   - 逆向 usage 接口属非官方、ToS 灰色地带，可能随 CLI 更新失效——故仅作增强项。

---

# 一、开发方案（给 coding agent，已全部拍板）

## 1.1 技术选型（不接受"可以用 A 或 B"，以下为最终决定）

| 层 | 选型 | 理由 |
|----|------|------|
| Companion 语言 | **Go** | 一套代码编译出 Mac/Win/Linux 单文件静态二进制；做常驻守护进程、文件监听、HTTP/WebSocket、mDNS 都成熟；内存占用低；可打包托盘程序 |
| Companion 托盘 | **`fyne.io/systray`** | 跨平台托盘图标，显示运行状态、配对码、退出 |
| Companion 本地存储 | **SQLite（`modernc.org/sqlite`，纯 Go 无 CGO）** | 存历史事件与快照，免装驱动，跨平台编译无障碍 |
| 局域网发现 | **mDNS / DNS-SD（`github.com/grandcat/zeroconf`）** | App 自动发现 Companion，服务类型 `_codegauge._tcp` |
| Android 语言/UI | **Kotlin + Jetpack Compose** | 现代安卓标准，声明式 UI 开发快 |
| Android 桌面小组件 | **Jetpack Glance** | 官方 Compose 风格 widget 方案，显示额度+倒计时 |
| Android 网络 | **Ktor Client + kotlinx.serialization** | 轻量，REST + WebSocket 同一套，原生协程 |
| Android 后台保活 | **前台服务（Foreground Service）持有 WebSocket** | LAN-only 下唯一能做到准实时通知的方式；用持久通知保活 |
| Android 最低版本 | **minSdk 26（Android 8.0）**，targetSdk 最新 | 覆盖绝大多数设备，支持 Glance 与通知渠道 |

## 1.2 系统架构（模块职责）

```
┌─────────────────────── 用户的开发电脑 ───────────────────────┐
│                                                              │
│  Claude Code / Codex CLI                                     │
│        │  (1) Claude hooks 以 HTTP 方式 POST 事件             │
│        ▼                                                     │
│  ┌──────────────── Companion (Go) ─────────────────┐         │
│  │ Collector   : 定时采额度 (ccusage / 本地文件 /    │         │
│  │               可选 endpoint 增强)                 │         │
│  │ HookReceiver: 收 Claude Code 钩子事件             │         │
│  │ Watcher     : 探测 claude/codex 进程是否在跑       │         │
│  │ Store       : SQLite 存快照与事件                 │         │
│  │ LanServer   : REST + WebSocket + 配对             │         │
│  │ Discovery   : mDNS 广播                           │         │
│  │ Tray        : 托盘图标 + 配对码                   │         │
│  └──────────────────────────────────────────────────┘        │
│        │  (2) REST 拉取 + (3) WebSocket 推送（同一 WiFi）       │
└────────┼─────────────────────────────────────────────────────┘
         ▼
   ┌──────────── Android App ────────────┐
   │ Repo        : 拉快照 + 订阅事件流     │
   │ Discovery   : NSD 发现 Companion      │
   │ Listener Svc: 前台服务保活 WebSocket  │
   │ UI (Compose): 仪表盘 / 活动 / 设置    │
   │ Widget(Glance): 桌面额度+倒计时       │
   │ Notifications: 预警/满血/任务完成     │
   └───────────────────────────────────────┘
```

**Companion 模块职责**
- **Collector**：每 N 秒（默认 60s）采集 Claude、Codex 两家额度。MVP 主路径：调用 `ccusage` 输出 JSON 解析 token 用量与 5 小时区块；解析 `claude` `/usage`、`codex` `/status` 拿到的"剩余%/reset 时间"（如可程序化获取）。增强路径（P2）：复用 CLI 登录态调用其 usage 接口拿精确值。
- **HookReceiver**：暴露 `POST /api/v1/hooks/claude`，接收 Claude Code 的 `Stop`（任务完成）、`Notification`（需要你确认/输入）、`SessionStart`/`PreToolUse` 等事件，转成 CodingSession 状态变更与 Event。
- **Watcher**：轮询系统进程，发现 `claude`/`codex` 进程 → 标记会话"活跃"，记录最近活动时间；进程消失且无 Stop 事件 → 标记"已结束（推断）"。
- **Store**：SQLite，存最新快照 + 事件历史（用于 App 的活动流，不做长期趋势分析）。
- **LanServer**：REST + WebSocket，绑定局域网网卡；除 `/pair` 外所有接口需 Bearer Token。
- **Discovery**：mDNS 广播 `_codegauge._tcp`，TXT 记录含端口与版本。
- **Tray**：显示"运行中 / 监听 IP:Port / 6 位配对码 / 退出"。

**Android 模块职责**
- **Discovery**：用 Android NSD 发现 Companion；找不到则手动输入 `IP:Port + 配对码`。
- **Repo**：首屏 REST 拉快照，之后 WebSocket 增量更新；断线指数退避重连。
- **Listener Service**：前台服务持有 WebSocket，收到事件 → 发系统通知 + 更新 widget。
- **UI**：仪表盘 / 活动 / 设置三页。
- **Widget**：Glance，显示两家剩余% + 最近恢复倒计时，点击进 App。

## 1.3 数据模型（Companion 端 SQLite）

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| **Provider** | `id`(claude/codex), `name`, `plan_tier`, `available`(bool) | 服务商 + 当前订阅档，`available` 标记是否取到数据 |
| **QuotaWindow** | `id`, `provider_id`(FK), `window_type`(`5h`/`weekly`), `percent_left`(0-100/nullable), `used`, `limit`, `resets_at`(ISO8601/nullable), `source`(`ccusage`/`cli`/`endpoint`), `updated_at` | 每个 Provider 有多条窗口记录；`source` 标明数据来源可信度 |
| **CodingSession** | `id`, `provider_id`(FK), `project_path`, `state`(`running`/`waiting`/`done`/`error`/`unknown`), `started_at`, `last_activity_at`, `last_event_type` | `waiting` 来自 Claude `Notification` 钩子，`done` 来自 `Stop` |
| **Event** | `id`, `provider_id`(FK/nullable), `type`(`session_start`/`session_done`/`session_waiting`/`limit_warn`/`limit_critical`/`quota_reset`/`error`), `payload`(JSON), `created_at` | 事件流，供 App 活动页与通知；保留最近 N 条（默认 500） |
| **DevicePairing** | `device_id`, `name`, `token`, `paired_at`, `last_seen_at` | 配对成功的手机；`token` 用于鉴权 |
| **Settings** | `key`, `value` | 采集间隔、预警阈值（默认 80/95）、监听端口等 |

关系：`Provider 1—N QuotaWindow`、`Provider 1—N CodingSession`、`Provider 1—N Event`。

## 1.4 接口设计（Companion LAN API，前缀 `/api/v1`）

> 除 `POST /pair` 外，所有接口需请求头 `Authorization: Bearer <token>`。

| 方法 | 路径 | 入参 | 出参 | 说明 |
|------|------|------|------|------|
| GET | `/status` | — | `{ providers:[{id,name,plan_tier,available,windows:[{window_type,percent_left,used,limit,resets_at,source,updated_at}]}], sessions:[{provider_id,project_path,state,last_activity_at}], server_time }` | 首屏全量快照 |
| GET | `/quota` | — | `{ providers:[...windows...] }` | 仅额度 |
| GET | `/sessions` | — | `{ sessions:[...] }` | 当前/最近会话 |
| GET | `/events?limit=50` | `limit` | `{ events:[{id,type,provider_id,payload,created_at}] }` | 活动历史，倒序 |
| GET | `/stream` (WebSocket) | — | 推送 `{event_type, data}`：`quota_update`/`session_update`/`alert` | 实时增量 |
| POST | `/hooks/claude` | Claude Code 钩子原始 JSON | `200 {}` | 由 Claude hooks 调用，无需 Bearer（仅监听 127.0.0.1，外网不可达） |
| POST | `/pair` | `{ pair_code, device_name }` | `{ token, server_name }` | 用托盘显示的 6 位码换 token |
| GET | `/health` | — | `{ ok:true, version }` | 探活，供 mDNS 校验 |

## 1.5 目录结构

```
codegauge/
├── companion/                      # Go
│   ├── cmd/codegauge/main.go        # 入口：启动 server/collector/watcher/tray
│   ├── internal/collector/
│   │   ├── collector.go             # 调度
│   │   ├── claude.go                # ccusage/JSONL/usage 解析
│   │   └── codex.go                 # ccusage/status 解析
│   ├── internal/server/
│   │   ├── rest.go  ws.go  hooks.go  pairing.go  auth.go
│   ├── internal/watcher/process.go  # 进程探测
│   ├── internal/store/
│   │   ├── sqlite.go  models.go  migrations.sql
│   ├── internal/discovery/mdns.go
│   ├── internal/tray/tray.go
│   ├── internal/config/config.go    # 端口/间隔/阈值
│   └── go.mod
├── android/
│   ├── app/src/main/java/com/codegauge/
│   │   ├── data/ (api/ApiClient.kt, ws/EventSocket.kt, discovery/NsdHelper.kt, repo/CodeGaugeRepository.kt, model/*.kt)
│   │   ├── service/ListenerForegroundService.kt
│   │   ├── ui/ (dashboard/, activity/, settings/, pairing/, theme/)
│   │   ├── widget/ (CodeGaugeWidget.kt, WidgetReceiver.kt)
│   │   ├── notify/Notifier.kt
│   │   └── MainActivity.kt
│   └── build.gradle.kts
├── hooks/
│   ├── claude-settings.snippet.json # 供用户合并进 ~/.claude/settings.json
│   └── install-hooks.sh             # 自动写入/合并钩子配置
└── README.md
```

## 1.6 开发任务拆解（按依赖顺序，每个任务一次提交粒度）

> 完整可验收版见【第四部分：给 coding agent 的实施指令】。此处仅列顺序与依赖。

1. **T1** 仓库脚手架 + Companion 骨架（config、main、空 server）
2. **T2** SQLite Store + 数据模型 + migrations（依赖 T1）
3. **T3** Collector：`ccusage` 解析 Claude/Codex 额度，写入 Store（依赖 T2）
4. **T4** LanServer：`/status` `/quota` `/health` + Bearer 鉴权 + `/pair`（依赖 T2）
5. **T5** HookReceiver `/hooks/claude` + Watcher 进程探测 → CodingSession/Event（依赖 T2）
6. **T6** WebSocket `/stream` 推送增量（依赖 T4、T5）
7. **T7** mDNS 广播 + Tray（依赖 T4）
8. **T8** hooks 配置片段 + 安装脚本（依赖 T5）
9. **T9** Android 脚手架 + 配对页（NSD 发现 / 手动输入 + `/pair`）（依赖 T4）
10. **T10** Android 仪表盘（拉 `/status` 展示两家额度卡 + 倒计时）（依赖 T9）
11. **T11** Android 活动页（会话状态 + 事件流）（依赖 T9、T6）
12. **T12** Listener 前台服务 + 通知（订阅 `/stream`，预警/满血/完成）（依赖 T6、T10）
13. **T13** Glance 桌面小组件（额度+倒计时）（依赖 T10）
14. **T14**（P2）额度精确化：复用 CLI 登录态调用 usage 接口作增强源（依赖 T3）

---

# 二、设计方案

## 2.1 功能架构图（文字版）

```
CodeGauge
├── 仪表盘（首页）
│   ├── Claude 额度卡（5h 剩余% + 周剩余% + 恢复倒计时）
│   ├── Codex 额度卡（同上）
│   └── 当前会话摘要条（在跑/等确认/空闲）
├── 活动
│   ├── 当前会话列表（项目路径 + 状态）
│   └── 事件流（完成/需确认/预警/满血/报错，倒序）
├── 设置
│   ├── 配对管理（连接的 Companion、重新配对）
│   ├── 通知开关与阈值（80%/95%/满血/任务完成）
│   └── 采集间隔、关于、连接诊断
└── 桌面小组件（Widget）
    └── 两家剩余% + 最近恢复倒计时
```

## 2.2 页面层级总览

**一级页面**（底部 Tab）：仪表盘 / 活动 / 设置
**二级页面**：配对页、Provider 详情页（点额度卡进入）、通知设置页
**三级/弹窗**：二维码/IP 输入弹窗、断连提示弹窗、关于页

## 2.3 一级页面详细说明

### 仪表盘（首页）
**页面功能**：3 秒内看懂"现在两家各还剩多少油、多久满血、有没有任务在跑"。
**布局**：顶部连接状态条（绿点=已连/灰=断连）→ 两张额度卡纵向排列 → 底部当前会话摘要条。
**核心 UI 元素**：

| 元素 | 类型 | 说明 |
|------|------|------|
| 连接状态条 | 顶部条 | 显示 Companion 名称 + 在线状态；断连时整条变灰并可点击诊断 |
| 额度卡 ×2 | 卡片 | 每卡含两条环形/线性进度（5h、周）+ 剩余% 大字 + "约 2h13m 后恢复"倒计时 |
| 数据来源标记 | 小标签 | 精确(endpoint)/估算(ccusage)，让你知道可信度 |
| 会话摘要条 | 横条 | "Claude 正在跑 · myapp" / "Codex 空闲"，点击进活动页 |

**交互逻辑**：进入即 REST 拉快照渲染；WebSocket 增量刷新数字与倒计时（倒计时本地每秒走）；下拉手动刷新；点额度卡 → Provider 详情页。
**跳转**：点额度卡 → Provider 详情；点会话条 → 活动页；点连接条（断连时）→ 连接诊断弹窗。

### 活动
**页面功能**：看正在跑什么、要不要去确认、历史发生了什么。
**布局**：上半"当前会话"卡列表，下半"事件流"时间线。
**核心 UI 元素**：会话卡（项目名/路径、状态徽章 `在跑/等确认/已完成/报错`、最近活动时间）；事件项（图标+文案+时间，如"⚠️ Claude 5h 额度剩 5%""✅ 任务完成 · myapp""🔔 Codex 等待你确认"）。
**交互逻辑**：实时随 WebSocket 插入新事件并轻提示；"等确认"会话高亮。
**跳转**：无强跳转（LAN-only 不做远程控制）。

### 设置
**页面功能**：配对、通知偏好、诊断。
**核心 UI 元素**：已连 Companion 信息 + "重新配对"；通知总开关 + 阈值滑块（默认 80/95）+ 满血提醒/任务完成开关；采集间隔；连接诊断（显示发现到的 mDNS 服务、最后心跳）。
**跳转**：重新配对 → 配对页；通知项 → 通知设置页。

## 2.4 二级 / 三级页面

- **配对页**：优先 NSD 自动发现，列出局域网内 Companion → 点选后输入托盘上的 6 位码 → 调 `/pair` 拿 token；失败可手动输 `IP:Port`。
- **Provider 详情页**：该服务 5h/周窗口的 used/limit 明细、精确恢复时刻、数据来源、最近相关事件。
- **通知设置页**：逐类开关 + 阈值。
- **断连提示弹窗**：检测不到心跳时弹出，给出"检查是否同一 WiFi / Companion 是否在运行"指引。

## 2.5 核心用户旅程

**旅程 1 · 首次配对**
1. 电脑装 Companion → 托盘显示"监听 192.168.1.20:8765 · 配对码 481920"。
2. 手机开 App → 配对页自动发现该 Companion → 点选 → 输入 481920 → 配对成功。
3. 进仪表盘，看到两家额度。

**旅程 2 · 日常瞥一眼（最高频）**
1. 想发个大任务前，看手机桌面 widget：Claude 5h 剩 12%、约 40 分钟恢复；Codex 周额度剩 70%。
2. 一眼决定：这单丢给 Codex 跑，别撞 Claude 的墙。

**旅程 3 · 满血/完成提醒**
1. 上次撞了限额，App 在额度恢复时推送"Claude 已满血 🎉"。
2. 长任务跑完，Claude `Stop` 钩子 → 推送"✅ 任务完成 · myapp"；若中途需要确认，推送"🔔 Claude 等待你确认"。

---

# 三、运营方案

> 诚实定位：小众、面向特定订阅用户的工具，天花板不高。建议当**开源个人作品**运营，而非创业项目。

- **冷启动**：你自己是首个用户，先打磨到自己每天用。随后开源到 GitHub（Companion + App + 一键 hook 安装脚本），README 放一张 widget + 通知的实拍 GIF。
- **获客渠道**：开发者社区精准投放——r/ClaudeAI、r/ChatGPTCoding、Hacker News（Show HN）、X/Twitter（带 widget 截图）；在 `ccusage`、Claude Code、Codex 相关 issue/discussion 里作为"手机端伴侣"被提及。定位话术："ccusage 在终端看，CodeGauge 在手机上瞥一眼 + 满血提醒。"
- **留存机制**：① 桌面 **widget**（每天解锁手机就被动看到，是最强留存钩子）；② **满血/预警推送**把人拉回；③ 把"决定任务丢给哪家"做成肌肉记忆。
- **商业化**：MVP **不商业化**（免费/开源，作为作品集与社区信誉）。若后续要变现，做 Pro 增强（多设备同步、用量历史趋势、多工具 Gemini/Copilot、云中转远程通知），一次性买断为宜，避免订阅维护负担。**不建议**为此投入重资源。
- **关键指标**：
  - 北极星：**每周"有效避墙查看"次数**（打开 App/widget 后据此调整了任务投放或避免了撞限）。
  - 核心指标：WAU、widget 安装率、推送点击率、配对成功率、Companion 连续运行崩溃率（稳定性是这类工具的命门）。

---

# 四、给 coding agent 的实施指令

> **执行规则**：以下技术选择全部已拍板，不得再做产品/选型决策。按任务编号顺序实现，遵守依赖关系。每个任务完成需满足其"验收标准"方可进入下一个。优先级：P0 必做、P1 重要、P2 增强。

### 全局约定
- 语言/框架严格按【1.1 技术选型】，不得替换。
- API 严格按【1.4】路径与字段实现。
- 所有时间用 ISO8601 + 时区；倒计时由客户端本地推算。
- `ccusage` 的确切 CLI 参数/JSON 字段在实现时以本机实际 `ccusage --help` 输出为准（不要臆造字段名）；若某服务商 `ccusage` 不支持，则降级为解析对应 CLI 命令输出，并在 `QuotaWindow.source` 标记来源。
- 凡无法稳定获取的字段（如精确 `percent_left`/`resets_at`），返回 `null`，前端显示"估算/不可用"，**不得编造数值**。

---

### T1 · 仓库与 Companion 骨架 — **P0**，依赖：无
建 monorepo（结构见 1.5）；Companion 可启动，读 `config`（端口默认 8765、采集间隔 60s、阈值 80/95），起一个空 HTTP server。
**验收**：`go run ./cmd/codegauge` 启动无报错；`GET /api/v1/health` 返回 `{ok:true,version}`。

### T2 · Store 与数据模型 — **P0**，依赖：T1
按【1.3】建 SQLite 表与 migrations，实现各实体的读写。
**验收**：单测能创建/读取 Provider、QuotaWindow、CodingSession、Event、DevicePairing、Settings；重启后数据保留。

### T3 · Collector（额度采集，MVP 主路径）— **P0**，依赖：T2
定时调用 `ccusage`（JSON 输出）解析 Claude 与 Codex 的 token 用量与 5 小时区块；尽力解析 `claude /usage`、`codex /status` 拿剩余%/reset；写入 QuotaWindow（标 `source`）。取不到的字段置 `null`。
**验收**：本机至少一家服务商能产出非空 QuotaWindow；`GET /api/v1/quota` 返回结构正确；服务商不可用时 `available=false` 而非崩溃。

### T4 · LanServer 基础接口 + 鉴权 + 配对 — **P0**，依赖：T2
实现 `/status` `/quota` `/health` `/pair`；除 `/pair` `/health` 外校验 Bearer Token；`/pair` 校验托盘 6 位码并签发 token 存 DevicePairing。
**验收**：未带 token 访问 `/status` 返 401；用正确配对码 `/pair` 拿到 token 后可访问 `/status`。

### T5 · HookReceiver + Watcher — **P0**，依赖：T2
`POST /hooks/claude`（仅绑 127.0.0.1）解析 Claude Code 钩子：`Stop→session_done`、`Notification→session_waiting`、`SessionStart→session_start`，更新 CodingSession 与 Event。Watcher 轮询进程，维护会话"活跃/推断结束"。
**验收**：手动 `curl` 模拟一条 `Stop` 钩子 JSON → 产生 `session_done` 事件且会话状态更新；启动 `claude` 进程后 Watcher 标记其活跃。

### T6 · WebSocket `/stream` — **P0**，依赖：T4、T5
鉴权后建立 WS；额度变化、会话状态变化、越过阈值时推送 `quota_update`/`session_update`/`alert`。
**验收**：连上 WS 后，触发一次额度更新与一条钩子事件，客户端都能即时收到对应消息。

### T7 · mDNS + Tray — **P0**，依赖：T4
mDNS 广播 `_codegauge._tcp`（TXT 含端口/版本）；托盘显示运行状态、监听地址、6 位配对码、退出。
**验收**：同网另一设备能用 mDNS 浏览到服务；托盘菜单可见配对码并能退出进程。

### T8 · Claude hooks 配置与安装脚本 — **P0**，依赖：T5
产出 `hooks/claude-settings.snippet.json`（含 `Stop`/`Notification`/`SessionStart` 的 HTTP 钩子，指向 `http://127.0.0.1:8765/api/v1/hooks/claude`）与 `install-hooks.sh`（安全合并进用户 `~/.claude/settings.json`，不覆盖既有配置）。
**验收**：运行脚本后 `~/.claude/settings.json` 含新钩子且原有内容完好；实际跑一次 Claude Code 任务能在 Companion 收到事件。

### T9 · Android 脚手架 + 配对页 — **P0**，依赖：T4
Compose 工程；配对页用 NSD 发现 Companion，列表选择后输入配对码调 `/pair` 存 token（加密存储）；支持手动 `IP:Port` 兜底。
**验收**：App 能发现本网 Companion，完成配对并持久化 token，重启 App 仍保持已配对。

### T10 · 仪表盘 — **P0**，依赖：T9
拉 `/status` 渲染两家额度卡（5h/周进度 + 剩余% + 本地倒计时）+ 会话摘要条 + 连接状态条 + 数据来源标记；下拉刷新。
**验收**：真实数据下两张卡正确显示百分比与倒计时（每秒走动）；服务商不可用时卡片显示"数据不可用"而非崩溃或假数据。

### T11 · 活动页 — **P0**，依赖：T9、T6
展示当前会话列表（状态徽章）+ 事件流（拉 `/events` 初始化，WS 增量插入）；"等确认"会话高亮。
**验收**：触发 `Stop`/`Notification` 事件，活动页实时出现对应条目且状态正确。

### T12 · 前台服务 + 通知 — **P0**，依赖：T6、T10
前台服务持有 WS 保活；收到 `alert`（额度过 80/95、满血）与会话 `session_done`/`session_waiting` 时发对应渠道通知；遵守设置页开关与阈值。
**验收**：模拟额度跌破阈值、额度恢复、任务完成三类事件，手机分别收到正确通知；杀掉前台再回 WiFi 能自动重连。

### T13 · Glance 桌面小组件 — **P1**，依赖：T10
Widget 显示两家剩余% + 最近恢复倒计时，定时（WorkManager）+ 服务推送时刷新；点击进 App。
**验收**：桌面添加 widget 后显示真实额度；额度变化后数分钟内或收到推送时刷新。

### T14 · 额度精确化增强 — **P2**，依赖：T3
在 ccusage/CLI 之外，复用 Claude Code / Codex 本地登录态调用其 usage 接口，拿精确 `percent_left`/`resets_at`，写入并把 `source` 标为 `endpoint`；接口失败时自动回落到 P0 来源，**绝不影响主功能**。
**验收**：成功时仪表盘"数据来源"显示"精确"；人为令接口失败时自动回落且无崩溃。

### 其他（P2 / 二期，本期不实现，仅登记）
- 多工具扩展（Gemini CLI / Copilot CLI）；用量历史趋势图；云中转以支持"不在家也收通知"；手机端远程批准/触发任务。

---

## 五、待你确认的假设
1. 产品代号 **CodeGauge**，可随时改名（影响包名 `com.codegauge`、mDNS 服务名）。
2. 默认监听端口 **8765**、采集间隔 **60s**、预警阈值 **80%/95%**——可改。
3. MVP 只做 **Android**（你明确要 Android）；未规划 iOS。
4. 假设你愿意在 `~/.claude/settings.json` 里加 hook 配置（脚本会自动合并）——这是拿到 Claude 实时"coding 状态"的前提。
5. Codex 实时状态做"基础版"（进程活跃 + 最近活动 + 完成提醒），不与 Claude 等同——已在风险里说明。
