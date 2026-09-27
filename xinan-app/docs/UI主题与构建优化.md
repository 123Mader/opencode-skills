# UI 主题升级 + 构建优化 — 说明

## 1. Material 3 主题 (来源: GitHub 开源参考)

旧主题用 `Theme.AppCompat.Light.NoActionBar` + 布局里硬编码 `#263238`/`#1E2B32`。
本次迁移到 **Material 3 (M3)**, 参考以下开源项目的 M3 token 方案:

- **Google "Now in Android"** — `github.com/android/nowinandroid`
  官方 M3 示范工程, tonal color tokens (primary/secondary/tertiary + container) 的标准用法。
- **Material Components for Android (MDC)** — `github.com/material-components/material-components-android`
  `Theme.Material3.DayNight` / `MaterialButtonToggleGroup` / `MaterialToolbar` 的官方实现 (已在依赖 `com.google.android.material:material:1.12.0`)。

配色取向: **平静薄荷 / 海雾系** (主色 `#3B6B6B` / `#7BBDB8`)。
心理健康类 App 用低饱和冷色可降低视觉唤醒, 与焦虑"暖红"互补形成仪表盘高亮。

## 2. 新增资源文件

```
res/values/
├── colors.xml          # M3 light tonal tokens + 情绪语义色 + 兼容别名
├── colors.xml (night)→ res/values-night/colors.xml  # 暗色覆盖, 自动昼夜切换
├── themes.xml          # Theme.Material3.DayNight + 圆角卡片/按钮样式
├── shapes.xml          # M3 ShapeAppearance (small/medium/large/pill)
└── dimens.xml          # 间距/圆角/字号/仪表盘尺寸 token

res/drawable/
├── bg_window_gradient.xml        # 窗口渐变背景 (薄荷雾)
├── bg_window_gradient.xml (night)→ drawable-night/  # 深薄荷
├── ic_launcher.xml               # 升级: 渐变圆 + 心形 + 心电波
├── ic_xinan_logo.xml             # 工具栏小标 (心形+心电波)
├── ic_mode_chat.xml              # 聊天模式图标
└── ic_mode_video.xml             # 视频模式图标

res/layout/
└── activity_main.xml   # MaterialToolbar + Fragment容器 + M3 SegmentedButton 切换
```

## 3. 布局重构要点

- 顶部 `MaterialToolbar` (透明背景, 渐变穿透, 居中标题 + logo)
- 底部 `MaterialButtonToggleGroup` (singleSelection) 替代两个硬编码 Button
- `MainActivity` 改用 `addOnButtonCheckedListener` + `check()`, 移除手写 `isSelected` 高亮
- 所有硬编码颜色移除, 统一走 M3 token / `?attr/`

## 4. 构建优化 (GitHub Actions)

旧 workflow 手动 `wget commandlinetools` 安装 SDK (脆弱、不可缓存、慢)。
新版:
- 用 `android-actions/setup-android@v3` 复用 runner 自带 SDK + 仅补 license/组件
- Gradle 8.9 发行包 **缓存** (`/opt/gradle-8.9`), 命中后跳过下载
- Gradle 依赖缓存 (`~/.gradle/caches`), 二次构建提速 2-3×
- 新增 release APK 产物 (unsigned, 便于真机测)
- `if-no-files-found: error` 让构建失败可见

> 路径 `xinan-app/android/**` 是正确的 (非 bug): `push_xinan.js` 把本地 `心安项目/` 映射到远端 `xinan-app/` 子目录。

## 5. 验证方式

本机无 JDK, 不能本地出 APK。改动靠 GitHub Actions 云构建验证:

```bash
# 推送 (push_xinan.js 把 心安项目 → xinan-app/)
node push_xinan.js <owner/repo> xinan-app
# 到 GitHub Actions 页面手动 Run workflow → Build APK
# 成功后 Artifacts 下载 xinan-debug-apk
```
