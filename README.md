# DSH 启动器（DSH Launcher）— 基于 Termux 的 Android 启动器

[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**面向 Android 系统的 Termux 生态启动器**：在手机端通过 Termux 一键启动 `dsh web` 服务，并以全屏 WebView 打开 DeepSeek Harness Web 界面。无需打开终端、无需手动执行命令，点击图标即可开始对话。

> provided by Justin Jee

## 功能特性

- **一键启动**：通过 Termux 的 `RUN_COMMAND` 机制后台拉起 `dsh web`，自动检测服务就绪（最长等待 60 秒）
- **全屏 WebView**：沉浸式全屏、刘海屏适配（内容延伸到显示缺口区域）
- **桌面比例渲染**：1280px 桌面宽度渲染 + 自动缩放，手机上也获得与电脑端一致的比例，支持双指放大
- **双模式**：桌面模式（桌面 UA）/ 手机模式（移动 UA）
- **状态面板**：实时显示 Termux 环境、命令权限、DSH Web 服务状态
- **一键停止**：停止 `dsh web` 服务并自动清理 Termux 后台进程
- **浅色模式**：强制关闭系统深色模式对网页的变暗处理

## 工作原理

```
点击启动器 → App 通过 Termux RUN_COMMAND 执行启动脚本 start-dsh.sh
          → 脚本用 node 直接执行 dsh web（绕过 Android 上 /usr/bin/env 缺失问题）
          → App 每 3 秒检测 127.0.0.1:3080 直到就绪
          → 打开全屏 WebView 加载 http://127.0.0.1:3080
```

## 环境要求

- Android 7.0+（minSdk 24，targetSdk 34）
- [Termux](https://f-droid.org/packages/com.termux/) ≥ 0.117（支持 `RUN_COMMAND`）
- Termux 中已安装并配置好 DSH（`dsh web` 可正常运行）

### Termux 侧一次性配置

1. **开启外部应用控制**（`~/.termux/termux.properties`）：

   ```properties
   allow-external-apps = true
   ```

   然后执行 `termux-reload-settings`。

2. **部署启动脚本**到 `~/bin/start-dsh.sh` 与 `~/bin/stop-dsh.sh`（见 `termux-scripts/` 目录），并 `chmod +x`。

3. 首次打开 App 时授予「运行 Termux 命令」权限（`com.termux.permission.RUN_COMMAND`，系统设置 → 应用 → 附加权限）。

## 构建

本项目是**纯 Java、无 AndroidX 依赖**的极简工程，可在 Termux 内直接构建（无需 Android Studio）：

```bash
# 依赖（Termux）：
pkg install openjdk-17 aapt2 d8 apksigner
# 需要 android.jar（Android SDK platform，如 android-34）

# 构建：
./build.sh
# 产物：dist/dsh-launcher-v1.0.apk
```

或使用 Android Studio 打开工程（需提供 `local.properties` 指向 SDK）。

> 注意：`keystore.jks` 为本地测试签名密钥，**不会也不应提交到仓库**（见 `.gitignore`）。发布正式版时请自行创建密钥。

## 安装与使用

1. 安装 `dist/dsh-launcher-v1.0.apk`
2. 打开「DSH 启动器」→ 点「启动 / 重启 DSH Web」
3. 等待状态变「✓ 运行中」→ 点「以桌面网页打开」

## 目录结构

```
dsh-launcher/
├── AndroidManifest.xml        # 应用清单（权限、Activity）
├── java/com/dsh/launcher/
│   ├── MainActivity.java      # 控制台界面（状态检测/启动/停止）
│   └── DsWebActivity.java     # 全屏 WebView（桌面比例渲染）
├── res/                       # 图标与资源
├── termux-scripts/            # Termux 侧启动/停止脚本
├── tools/gen_icon.py          # 图标生成脚本（纯标准库）
└── build.sh                   # 一键构建脚本（aapt2 + javac + d8 + apksigner）
```

## License

[MIT](LICENSE) © 2026 Justin Jee
