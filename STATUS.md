# CodeGauge Status

Last updated: 2026-06-05

## 已上线功能

- 暂无。当前处于 T0 地基整理阶段。

## 进行中 / 待处理项

- T0: Monorepo 地基已建立，Android App 已迁到 `android/app`。
- T0: Android 入口已迁到 Jetpack Compose，包名统一为 `com.codegauge`。
- T0: `./gradlew :android:app:assembleDebug` 通过。
- T0: `./gradlew :android:app:testDebugUnitTest` 通过。
- T1: Companion Go 骨架待实现。
- T4 前置: 设置页需要的 `/settings`、`/diagnostics`、`/devices` API 需要补入实施计划。

## 已知问题和技术债务

- 当前目录不是 git 仓库，无法按方案执行“每个任务一次提交粒度”。
- `compileSdk`/`targetSdk` 已设为 36；当前本机缓存只有 Android Gradle Plugin 8.1.3，已临时加 `android.suppressUnsupportedCompileSdk=36`。
- LAN HTTP 需要明文流量，当前网络安全配置允许 cleartext；后续若支持固定 host 或本地 TLS，应收紧。
- 前台服务、通知、NSD、Glance 仅完成基础声明，尚未实现业务逻辑。
- `ccusage`/CLI 输出字段必须在 T3 按本机实际输出确认，不能预设字段名。

## 关键架构决策及原因

- 保留根目录 Gradle wrapper，使用 `:android:app` 指向 Android 模块，避免移动 wrapper/local.properties 造成额外 IDE 迁移成本。
- Android 使用 Compose 作为唯一 UI 入口，为后续按 Claude 设计稿实现仪表盘/活动/设置页做准备。
- 包名定为 `com.codegauge`，与产品代号、mDNS 服务名、方案假设保持一致。
- 先做 T0 地基，不实现业务 UI，避免把结构迁移和设计开发混在一次改动里。
