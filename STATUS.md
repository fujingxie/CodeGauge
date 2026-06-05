# CodeGauge Status

Last updated: 2026-06-05

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
- T5: HookReceiver `/hooks/claude` + Watcher 待实现。
- 设置页前置: 设置页需要的 `/settings`、`/diagnostics`、`/devices` API 需要补入实施计划。

## 已知问题和技术债务

- `compileSdk`/`targetSdk` 已设为 36；当前本机缓存只有 Android Gradle Plugin 8.1.3，已临时加 `android.suppressUnsupportedCompileSdk=36`。
- LAN HTTP 需要明文流量，当前网络安全配置允许 cleartext；后续若支持固定 host 或本地 TLS，应收紧。
- 前台服务、通知、NSD、Glance 仅完成基础声明，尚未实现业务逻辑。
- 本机 `8765` 端口当前已有 `python3.1` 进程监听；T1 验收改用 `CODEGAUGE_PORT=18765`，默认配置仍保持 `8765`。
- `modernc.org/sqlite` 依赖通过 `GOPROXY=https://goproxy.cn,direct` 拉取；默认 `proxy.golang.org` 在本机网络下超时。
- Codex shell 的 PATH 可能看不到 nvm/.local 安装目录；运行 Companion 时如找不到 `ccusage`，需要设置 `CODEGAUGE_CCUSAGE_PATH` 为 `command -v ccusage` 的结果。
- `ccusage 20.0.6` 不提供 Codex 剩余额度百分比和 reset time；T3 按方案保持这些字段为 `null`，不编造数值。
- T4 配对码目前由环境变量指定或启动时生成并打印；TTL、尝试次数限制、托盘展示留到 T7/安全硬化。
- T4 token 当前按数据模型存储在 `device_pairings.token`；后续公开分发前建议改为 token hash 存储。

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
