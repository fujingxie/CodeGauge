# CodeGauge Status

Last updated: 2026-06-12

## 已上线功能

- T1: Companion Go 骨架可启动。
- T1: `GET /api/v1/health` 返回 `{ok:true,version}`。
- T2: Companion SQLite Store、数据模型和 migrations 已实现。
- T2: Store 支持 Provider、QuotaWindow、CodingSession、Event、DevicePairing、Settings 的基础读写。
- T3: Companion Collector 可通过 `ccusage` 采集 Claude/Codex 用量并写入 Store。
- T3: Collector 支持 `CollectOnce` 和周期 `Run(ctx, interval)`。
- T4: LanServer 已实现 `/status`、`/quota`、`/health`、`/pair`。
- T4: `/status` 和 `/quota` 已接入 Bearer Token 鉴权。
- T4: Companion 启动时会打开 SQLite Store、启动 Collector，并提供 LAN API。
- T5: Companion 已实现本机 Claude Hook Receiver：`POST /api/v1/hooks/claude`。
- T5: Claude Hook Receiver 支持 `SessionStart`、`Notification`、`Stop` 并写入会话和事件。
- T5: Companion 已实现进程 Watcher，可检测 `claude` / `codex` 进程并推断 running/done。
- T6: Companion 已实现鉴权 WebSocket：`GET /api/v1/stream`。
- T6: WebSocket 可推送 `quota_update`、`session_update`、`alert` 增量消息。
- T7: Companion 已实现 mDNS 广播 `_codegauge._tcp.local.`。
- T7: Companion 已实现 macOS/桌面托盘菜单，显示运行状态、监听地址、配对码、版本和退出入口。
- T8: 已提供 Claude Code hooks 配置片段和安全安装脚本。
- T8: 安装脚本会保留已有 Claude settings、写入前备份，并可重复运行不重复插入。
- T9: Android 已实现 Companion 配对页，支持 NSD 自动发现、手动 `IP:Port`、配对码提交和已配对状态展示。
- T9: Android 配对 token 已通过加密 SharedPreferences 持久化，重启 App 后保持已配对。
- T10: Android 已实现仪表盘首页，配对后可拉取 `/status` 并展示连接状态、Claude/Codex 额度卡、数据来源、reset 倒计时和当前会话摘要。
- T11: Companion 已实现鉴权 `/events`，WebSocket 已推送 `event_update`；Android 已实现 Dashboard / Activity 底部 Tab、活动页会话列表和事件流。
- T12: Android 已实现前台监听服务，持有 `/stream` WebSocket，收到额度 alert、任务完成、等待确认时发送中文通知，并支持断线自动重连。
- T13: Android 已实现 Glance 桌面小组件，显示 Claude/Codex 额度、恢复倒计时和更新时间，支持点击打开 App。
- T13: 小组件会通过 WorkManager 定时刷新，并在前台监听服务收到 `/stream` 推送时主动刷新。
- T14: Companion 已实现 Codex 额度精确化增强，通过 Codex app-server 本地协议读取 5h/weekly 使用百分比和 reset time，并把精确窗口标记为 `source=endpoint`。
- T14: 精确源失败时会记录日志并回落到 `ccusage` 数据，不影响 Companion 主采集流程。

## 进行中 / 待处理项

- T0: Monorepo 地基已建立，Android App 已迁到 `android/app`。
- T0: Android 入口已迁到 Jetpack Compose，包名统一为 `com.codegauge`。
- T0: `./gradlew :android:app:assembleDebug` 通过。
- T0: `./gradlew :android:app:testDebugUnitTest` 通过。
- T1: `go test ./...` 通过。
- T1: 使用临时端口 `18765` 验证 `GET /api/v1/health` 通过。
- T2: `go test ./...` 通过，覆盖 6 类实体和重启持久化。
- T3: `go test ./...` 通过。
- T3: 真实 `ccusage` 集成测试通过，命令为 `CODEGAUGE_REAL_CCUSAGE_PATH="$(command -v ccusage)" go test ./internal/collector -run TestRealCCUsageCollectsAtLeastOneWindow -count=1`。
- T4: `go test ./...` 通过。
- T4: 手动接口验收通过：`/health` 200、无 token `/status` 401、错误配对码 `/pair` 401、正确配对码 `/pair` 返回 token、token 可访问 `/status` 与 `/quota`。
- T5: `go test ./...` 通过。
- T5: 手动接口验收通过：`/api/v1/hooks/claude` 返回 `{"ok":true}`，`/status` 可看到 Claude session state 为 `done`，Watcher 可看到当前 Claude/Codex 进程为 `running`。
- T6: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 通过，覆盖真实 WS 握手、Hook 触发 session_update、quota_update 和阈值 alert。
- T7: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 通过。
- T7: 手动 mDNS 验收通过：`dns-sd -B _codegauge._tcp local` 可发现 `CodeGauge Companion`。
- T7: 手动托盘验收通过：菜单可见 `Status: Running`、监听地址、配对码、版本和 `Quit`；修复后点击 `Quit` 会结束托盘事件循环并退出进程。
- T8: `node --test hooks/merge-claude-settings.test.mjs` 通过。
- T8: `hooks/install-hooks.sh` 临时 settings dry run 通过，验证自定义 hook URL 和 `SessionStart` command hook。
- T8: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 通过。
- T9: `./gradlew :android:app:testDebugUnitTest` 通过，覆盖手动 endpoint 解析和配对仓库行为。
- T9: `./gradlew :android:app:assembleDebug` 通过。
- T9: 手机手动验收通过：Android App 可自动发现 `CodeGauge Companion`，也可用 `192.168.1.4:18770` 手动配对；强杀并重启 App 后直接显示 `Paired`。
- T10: `./gradlew :android:app:testDebugUnitTest` 通过，覆盖 `/status` JSON 解析、nullable quota 字段和展示文案格式化。
- T10: `./gradlew :android:app:assembleDebug` 通过。
- T10: 手机手动验收通过：首页显示 Claude/Codex 卡、`Source: ccusage`、token 用量、Claude 5h reset 倒计时、当前 Codex running session；`ccusage` 未提供的剩余百分比正确显示 `Unknown`。
- T11: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 通过，覆盖 `/events`、`event_update` 和 stream 增量。
- T11: `./gradlew :android:app:testDebugUnitTest` 通过，覆盖 `/events` 与 `/stream` 解析。
- T11: `./gradlew :android:app:assembleDebug` 通过。
- T11: 手机手动验收通过：Activity 页可实时出现 `Stop` / `Notification` 事件，等待确认 session 高亮，刷新后事件仍保留。
- T12: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 通过，覆盖 quota warning / reset alert 推送。
- T12: `./gradlew :android:app:testDebugUnitTest` 通过，覆盖通知映射和中文通知文案。
- T12: `./gradlew :android:app:assembleDebug` 通过。
- T12: 手机手动验收通过：前台常驻通知出现，`Stop` / `Notification` hook 可触发通知；通知文案已改为中文。
- T13: `./gradlew :android:app:testDebugUnitTest` 通过，覆盖小组件额度文案格式化。
- T13: `./gradlew :android:app:assembleDebug` 通过。
- T13: 手机手动验收通过：桌面添加 CodeGauge 小组件后能显示真实额度，点击可打开 App，推送触发后可刷新。
- T14: `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` 在 `companion/` 通过。
- T14: `CODEGAUGE_REAL_CODEX_PATH=/Applications/Codex.app/Contents/Resources/codex go test ./internal/collector -run TestRealCodexAppServerCollectsRateLimits -count=1` 通过。
- T14: 临时 Companion 端到端 smoke 通过：`/status` 中 Codex `5h` 与 `weekly` 窗口显示 `source=endpoint`，weekly 保留 `ccusage` token used。
- 设置页前置: 设置页需要的 `/settings`、`/diagnostics`、`/devices` API 需要补入实施计划。

## 已知问题和技术债务

- `compileSdk`/`targetSdk` 已设为 36；当前本机缓存只有 Android Gradle Plugin 8.1.3，已临时加 `android.suppressUnsupportedCompileSdk=36`。
- LAN HTTP 需要明文流量，当前网络安全配置允许 cleartext；后续若支持固定 host 或本地 TLS，应收紧。
- 本机 `8765` 端口当前已有 `python3.1` 进程监听；T1 验收改用 `CODEGAUGE_PORT=18765`，默认配置仍保持 `8765`。
- `modernc.org/sqlite` 依赖通过 `GOPROXY=https://goproxy.cn,direct` 拉取；默认 `proxy.golang.org` 在本机网络下超时。
- Codex shell 的 PATH 可能看不到 nvm/.local 安装目录；运行 Companion 时如找不到 `ccusage`，需要设置 `CODEGAUGE_CCUSAGE_PATH` 为 `command -v ccusage` 的结果。
- Codex 精确源默认优先使用 `/Applications/Codex.app/Contents/Resources/codex`；如安装位置不同，可设置 `CODEGAUGE_CODEX_PATH`。
- `ccusage 20.0.6` 不提供 Codex 剩余额度百分比和 reset time；T3 按方案保持这些字段为 `null`，不编造数值。
- T4 配对码目前由环境变量指定或启动时生成并打印；TTL、尝试次数限制、托盘展示留到 T7/安全硬化。
- T4 token 当前按数据模型存储在 `device_pairings.token`；后续公开分发前建议改为 token hash 存储。
- T5 Hook endpoint 当前挂在同一个 HTTP server 上，但只接受 loopback 请求；如果后续主服务需要绑定公网/复杂网卡，应考虑拆分为独立本地 listener。
- T5 Watcher 通过进程名推断活动状态，无法提供项目路径；真实 Claude hooks 的 session 会提供 `cwd`，优先用于精确展示。
- T6 WebSocket 当前采用内存 Hub；服务重启后客户端需重连并通过 `/status` 拉取新快照。
- T6 alert 只在单进程内比较上一条 quota window 后推送；历史去重和跨重启阈值抑制留到通知模块处理。
- T7 托盘依赖 `fyne.io/systray`，构建桌面托盘版本需要 CGO；无 GUI/自动测试环境可用 `CODEGAUGE_TRAY_ENABLED=false` 启动纯后台服务。
- T7/T9 mDNS 和 Android NSD 依赖局域网、系统 Bonjour/mDNS 环境和路由器组播支持；已通过 `dns-sd` 和手机 App 手动验收，后续仍需保留手动回归。
- T8 未自动写入真实 `~/.claude/settings.json`；需要用户手动运行 `hooks/install-hooks.sh` 完成安装。
- T8 根据 Claude Code 当前 hooks 限制，`SessionStart` 使用 `command` hook 通过 `curl --data-binary @-` 转发到本地 HTTP endpoint；`Notification` 和 `Stop` 使用 HTTP hook。
- T12 通知设置开关和自定义阈值尚未接入，因为设置页需要的 `/settings` API 仍在待办；当前使用 Companion 默认阈值 80/95。
- T13 小组件使用当前 `/status` 数据；如果 `ccusage` 未提供剩余百分比或 reset time，仍显示未知，不编造额度。
- T14 当前只对 Codex 接入稳定的本地 app-server 精确源；Claude Code 目前没有同等级稳定的本地 usage/rate-limit 协议，仍使用 `ccusage` 主路径，避免硬编码私有接口。

## 关键架构决策及原因

- 保留根目录 Gradle wrapper，使用 `:android:app` 指向 Android 模块，避免移动 wrapper/local.properties 造成额外 IDE 迁移成本。
- Android 使用 Compose 作为唯一 UI 入口，为后续按 Claude 设计稿实现仪表盘/活动/设置页做准备。
- 包名定为 `com.codegauge`，与产品代号、mDNS 服务名、方案假设保持一致。
- 先做 T0 地基，不实现业务 UI，避免把结构迁移和设计开发混在一次改动里。
- Companion T1 不引入第三方依赖；配置先用环境变量覆盖，保持骨架简单可测。
- Companion Store 使用 `modernc.org/sqlite`，保持纯 Go、无 CGO，符合跨平台单文件目标。
- SQLite 表字段避免使用 SQL 常见关键字，`QuotaWindow.Limit` 在表内落为 `limit_value`。
- Collector 使用真实 `ccusage 20.0.6` JSON 结构：Claude `blocks[].totalTokens/endTime/isActive` 和 Claude/Codex `daily[].totalTokens`。
- Weekly 窗口当前表示最近 7 天 token 使用量，因 `ccusage` 未暴露订阅周额度上限，所以 `percent_left`、`limit`、`resets_at` 保持空。
- T4 Router 通过 `server.Options` 注入 Store、配对码和生成器，方便 T7 托盘接入和测试固定 token。
- T5 Claude Hook Receiver 只处理方案要求的 `SessionStart`、`Notification`、`Stop`，其它 Claude Code hook 事件暂时忽略，避免过早扩展事件模型。
- T5 Watcher 使用可注入 `ProcessLister`，业务行为用 fake lister 测试，真实实现用 `ps`/`tasklist` 读取系统进程。
- T6 使用 `internal/stream.NotifyingStore` 包装 SQLite Store，把 Collector/Watcher/Hook 的写入统一转换为 WebSocket 增量消息，避免在各调用方重复推送逻辑。
- T6 WebSocket 使用 `github.com/gorilla/websocket`，保持 server 代码简洁并覆盖真实握手测试。
- T7 mDNS 广播通过 `internal/discovery.Advertiser` 封装，便于用 fake registrar 测试注册参数和 shutdown。
- T7 托盘通过 `internal/tray.Controller` 封装菜单模型，真实 `systray.Run` 保持在 main goroutine，避免 macOS 托盘事件循环异常。
- T8 JSON 合并逻辑放在 `hooks/merge-claude-settings.mjs`，`install-hooks.sh` 只负责定位 settings/snippet 并调用 Node，便于测试和避免 shell 里手写 JSON 合并。
- T9 Android 网络层先使用 OkHttp + `org.json`，避免在早期工程里引入 Ktor/serialization 插件；API 面扩大后再统一抽 client。
- T9 Android 配对状态通过 `PairingRepository` + `PairingStore` 注入，生产使用 `EncryptedSharedPreferences`，测试使用内存 store。
- T10 Android 仪表盘先用 REST `/status` 全量快照和手动刷新；WebSocket 实时更新留到 T12 前台服务统一处理。
- T10 对 `percent_left`、`limit`、`resets_at` 的 `null` 值不做推断，UI 显示 `Unknown` 或 `Reset time unknown`，避免编造 quota。
- T11 后端复用 SQLite Event Store 暴露 `/events`，同时让 `NotifyingStore.AddEvent` 发布 `event_update`，避免 Android 活动页轮询。
- T12 Android 前台服务从加密配对存储读取 token，不通过 Intent 传递敏感信息；通知面向中文用户，标题和正文统一使用中文。
- T13 小组件使用 SharedPreferences 缓存最后一次快照，Glance 渲染只读本地缓存；网络刷新集中在 `CodeGaugeWidgetUpdater`，避免 widget 渲染阶段触网。
- T13 使用 WorkManager 15 分钟周期任务兜底刷新，同时在配对状态变化和前台 `/stream` 收到消息时主动刷新，让桌面小组件保持接近实时但不额外常驻后台。
- T14 精确源被设计为 Collector 的可选 `PreciseSource`，在 `ccusage` 采集之后运行；精确源只覆盖百分比、reset time 和 source，缺失的 used/limit 会从已有窗口合并，保证回退数据仍完整。
- T14 Codex 精确源通过 `codex app-server --stdio` 调用 `account/rateLimits/read`，不读取或输出本地 token，不直接拼接私有 HTTP endpoint。
