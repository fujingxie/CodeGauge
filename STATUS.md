# CodeGauge Status

Last updated: 2026-06-11

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
- 设置页前置: 设置页需要的 `/settings`、`/diagnostics`、`/devices` API 需要补入实施计划。

## 已知问题和技术债务

- `compileSdk`/`targetSdk` 已设为 36；当前本机缓存只有 Android Gradle Plugin 8.1.3，已临时加 `android.suppressUnsupportedCompileSdk=36`。
- LAN HTTP 需要明文流量，当前网络安全配置允许 cleartext；后续若支持固定 host 或本地 TLS，应收紧。
- 前台服务、通知、Glance 仅完成基础声明，尚未实现业务逻辑。
- 本机 `8765` 端口当前已有 `python3.1` 进程监听；T1 验收改用 `CODEGAUGE_PORT=18765`，默认配置仍保持 `8765`。
- `modernc.org/sqlite` 依赖通过 `GOPROXY=https://goproxy.cn,direct` 拉取；默认 `proxy.golang.org` 在本机网络下超时。
- Codex shell 的 PATH 可能看不到 nvm/.local 安装目录；运行 Companion 时如找不到 `ccusage`，需要设置 `CODEGAUGE_CCUSAGE_PATH` 为 `command -v ccusage` 的结果。
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
