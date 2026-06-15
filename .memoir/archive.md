## 项目定位

CodeGauge 是一个局域网（LAN）Android 仪表盘和本地伴侣服务，用于监控 AI 编程配额、重置窗口和编码会话事件。它解决的核心问题是：在本地开发环境中，实时追踪 AI 编程助手的配额使用情况、会话窗口重置事件，并提供可视化仪表盘进行监控和管理。

## 技术栈与设计

- **Android 前端**：使用 Kotlin + Jetpack Compose + Gradle 构建的 Android 应用，负责展示仪表盘、活动页、设置页、小组件和前台通知
- **后端伴侣服务**：Go 语言编写的本地服务（`companion/`），包含：
  - REST API 服务器（`server/rest.go`）
  - SQLite 数据存储（`store/sqlite.go`）
  - 文件系统监控（`watcher/process.go`）
  - 数据收集器（`collector/collector.go`）
  - WebSocket 流式推送（`stream/hub.go`）
  - Claude 钩子集成（`hooks/claude.go`）
- **钩子系统**：Claude 编程助手的钩子脚本（`hooks/` 目录）

1. **局域网优先**：限定在 LAN 范围内运行，不依赖云端服务
2. **双层架构**：Android App 作为前端展示，Go 伴侣服务处理后端逻辑和数据存储
3. **SQLite 本地存储**：使用迁移脚本（`migrations.sql`）管理数据库 schema 变更
4. **WebSocket 实时推送**：通过 `stream/hub.go` 实现事件流的实时推送
5. **文件系统监控**：通过 `watcher/process.go` 监控编码会话文件变化
6. **共享 UI 设计层**：Android 高精度界面基础组件集中在 `android/app/src/main/java/com/codegauge/ui/design/CodeGaugeDesign.kt`

## 运行部署运维

**Android 应用构建**：
```bash
./gradlew :android:app:assembleDebug
```

**Go 伴侣服务开发运行**：
```bash
cd companion
go test ./...
go run ./cmd/codegauge
```

**macOS 日常安装与管理**：
```bash
scripts/install-macos.sh
~/.codegauge/bin/codegaugectl status
~/.codegauge/bin/codegaugectl logs
~/.codegauge/bin/codegaugectl pair-code
~/.codegauge/bin/codegaugectl restart
```

- Go 伴侣服务需保持运行状态
- macOS 安装器会在 `~/.codegauge/codegauge.env` 写入服务 `PATH`，确保 launchd 后台环境能找到 nvm/npm 安装的 `node` 与 `ccusage`
- Claude hooks 已提供配置片段和安装脚本；macOS 安装器默认会合并 hooks，可用 `--no-hooks` 跳过
- 数据库迁移需确保 `migrations.sql` 按顺序执行

## 待办与已知问题

1. **产品化发布**：Android debug 版本、Companion macOS LaunchAgent 安装流程已可用；后续重点是 Android release 签名包、安装体验打磨和更严格 LAN/TLS 策略
2. **集成测试覆盖**：部分模块（如 `collector_integration_test.go`）的集成测试可能需要完善
3. **文档完善**：`docs/implementation-notes.md` 包含实施笔记，但 README 仍为功能摘要

1. **构建设置**：`android/app/libs/` 目录为空，可能需要补充依赖库
2. **测试依赖**：部分 Go 模块的测试可能需要特定的运行环境或数据
3. **状态追踪**：`STATUS.md` 和 `CHANGELOG.md` 记录了项目演进，接手时需同步查阅
