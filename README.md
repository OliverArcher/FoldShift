# FoldShift

> 专为 **Samsung Galaxy Z Fold8（SM-F9710）** 开发的折叠态自动桌面切换工具。

## 这是什么

FoldShift 监听折叠状态，在外屏 / 内屏之间自动切换默认桌面 launcher，并在默认桌面变化后把对应 launcher 拉到前台（不打扰第三方前台应用）。

- 展开（OPENED）→ 切换到「内屏桌面」（默认 One UI Home）
- 合盖（CLOSED）→ 切换到「外屏桌面」（默认 Niagara Launcher）
- 抖动场景已去重，物理折叠瞬间显示系统来回切换不会重复触发
- 合盖→外屏 launcher（已活）体感比展开→内屏 launcher（冷启动）快很多，是 launcher 进程生命周期的差异

## 运行要求

- **Shizuku** 或 **Stellar（兼容模式）**：执行 `cmd package set-home-activity` 这一步需要 shell 身份
- **Android 12+ 后台 Activity 启动（BAL）合规**：派发 HOME 走无障碍 `GLOBAL_ACTION_HOME` 主路径，避免被 BAL 拦截
- 可选：系统设置 → 辅助功能 → 启用「FoldShift 折叠切换」服务，桌面拉起的延迟会更低（不授权也能跑，只是走 `input keyevent` 备路径）

## 安装

1. 从 [Releases](../../releases) 下载 `FoldShift-1.0-release.apk`
2. 在系统设置启用「未知来源应用」后安装
3. 打开 FoldShift，在设置页选好「内屏桌面」「外屏桌面」对应的 launcher
4. 打开服务开关
5. （可选）按设置页提示把 FoldShift 加入电池白名单 + 启用无障碍

## 工作原理

折叠状态由 `DisplayManager.DisplayListener` 监听，从两个内置屏幕的尺寸判定：

| 屏幕 | 尺寸 | 长宽比判定 |
|---|---|---|
| 内屏 inner | 2448 × 1848（landscape） | width > height → **OPENED** |
| 外屏 cover | 1248 × 1972（portrait） | width < height → **CLOSED** |

切换 pipeline：

1. `cmd package set-home-activity` 改默认桌面
2. 如果前台是 launcher（不是第三方 app）：派发 HOME 把新桌面拉到前台
3. 派发优先级：`GLOBAL_ACTION_HOME`（无障碍）→ `input keyevent HOME`（shizuku shell）→ `am start -n <component>`（兜底）
4. 每次派发后做 `topResumedActivity` 校验再返回结果，避开「Status: ok 但 BAL 拦了」的假成功

## 已知问题

- 内置屏幕的非 `LAUNCHER` 类别注册：OneUI Home 用的是 Samsung 自定义的 `LAUNCHER_APP`，不是标准 `LAUNCHER`。FoldShift 的 launcher 选择器已同时认这两类，但第三方 launcher 如果既没 `LAUNCHER` 也没 `LAUNCHER_APP` 又没在 manifest 暴露 HOME intent，会收不到。
- 冷启动 vs 热启动非对称：合盖→外屏 launcher（已活）派发链路上比展开→内屏 launcher（冷启动）多花 ~130ms，但屏幕真换那一刻反而合盖先到位，是 task bring-forward vs cold launch 的本质差异。
- 极少数 ROM 的 HOME intent filter 缓存可能让首次切换后还需手动按 Home 一次。

## 隐私

- 不收集遥测，不联网
- 只用本机 Shizuku / 无障碍通道执行切换命令
- 没有第三方分析 SDK，没有广告 SDK

## 构建

```powershell
# 从仓库根目录（PowerShell）
.\release-build.ps1
```

脚本会自动：

1. 从 1Password（Agent vault，`Fishking Release Signing`）拉取 keystore 路径 + 三个密码字段
2. 生成临时的 `projects\fold-shift\gradle.properties`（gitignored）
3. 跑 `./gradlew assembleRelease`
4. build 完成后删除生成的 `gradle.properties`——本地不留任何明文密码

前置条件：

- 1Password CLI 配好 Agent service-account token（默认位置 `D:\OH-WorkSpace\Agent service account.txt`）
- Keystore 文件（默认 `D:\OH-WorkSpace\fishking-release.jks`）在本机存在

构建产物：`projects\fold-shift\app\build\outputs\apk\release\app-release.apk`

要手动填（不用 1Password）也可以：

```powershell
cp projects\fold-shift\gradle.properties.template projects\fold-shift\gradle.properties
# 编辑生成的 gradle.properties，填上 FOLDSHIFT_STORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
cd projects\fold-shift
.\gradlew.bat assembleRelease
```

Debug APK（`assembleDebug`）不需要签名，可直接构建。

## 分发

**APK 只通过 GitHub Release 分发，不入库。** `.gitignore` 把所有 `*.apk` 都挡掉了，`release-build.ps1` 出来的产物也只写本地 `app/build/outputs/...`，唯一上传渠道是 release page 的 asset。下载的人去 release 页拿 APK，看 source 的人看 git tree，互不混淆。

## 版本

**1.0** · 2026-09-30

首次公开 release。三处本轮（feature complete 之前）的修复都包含：
- BAL_BLOCK 根因定位 + 三段 fallback 派发路径
- 折叠抖动去重（`FoldStateDetector.lastEmitted`）
- launcher 选择器对 OneUI Home 的 `LAUNCHER_APP` 兼容
- ShizukuCard「已授权」改用 `primaryContainer`（深色低饱和）
- adaptive icon safe zone 缩放包了一层 `<group scale="0.422">`

## 开发者

[Oliver Archer](https://github.com/OliverArcher)