# Android Release 打包

T17 目标是把 Android App 从 debug 安装推进到可长期安装的 release APK。签名密钥只保存在本机，不能提交到仓库。

## 版本

- `applicationId`: `com.codegauge`
- `versionCode`: `1`
- `versionName`: `0.1.0`
- APK 模块: `:android:app`

版本号配置在 `android/app/build.gradle.kts`。

## 首次创建签名密钥

在项目根目录执行：

```bash
mkdir -p release
keytool -genkeypair \
  -v \
  -keystore release/codegauge-release.jks \
  -alias codegauge \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

然后创建本地签名配置：

```bash
cp keystore.properties.example keystore.properties
vim keystore.properties
```

示例：

```properties
storeFile=release/codegauge-release.jks
storePassword=<你的 keystore 密码>
keyAlias=codegauge
keyPassword=<你的 key 密码>
```

`keystore.properties`、`*.jks` 和 `*.keystore` 已被 `.gitignore` 忽略。

## 构建 release APK

```bash
scripts/build-android-release.sh
```

脚本会串行执行：

```bash
./gradlew :android:app:testDebugUnitTest
./gradlew :android:app:assembleRelease
```

成功后输出：

```text
android/app/build/outputs/apk/release/app-release.apk
```

## 安装到真机

```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

如果 Android 拒绝从 debug 版覆盖安装，先卸载旧包：

```bash
adb uninstall com.codegauge
adb install android/app/build/outputs/apk/release/app-release.apk
```

卸载会清除 App 本地配对 token，需要重新配对 Companion。

## Release 验收清单

- App 可启动，未出现崩溃。
- 可自动发现或手动连接 `CodeGauge Companion`。
- 输入配对码后进入 Dashboard。
- Dashboard 显示 Claude / Codex 额度卡，首页主额度默认展示 5H。
- Activity 显示当前会话和事件流，后台进程不会显示成“未知项目”。
- Settings 可保存通知、阈值、采集间隔和首页主额度设置。
- 前台常驻通知正常出现。
- 发送 `Stop` hook 后，任务完成通知按设置开关生效。
- 小组件能添加并刷新。
- `logcat` 中无 `AndroidRuntime` / `FATAL EXCEPTION`。

常用崩溃检查：

```bash
adb logcat -c
adb shell monkey -p com.codegauge 1
sleep 3
adb logcat -d -t 300 | rg "AndroidRuntime|FATAL EXCEPTION|CodeGauge"
```
