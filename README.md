# Bd

Bd 是一款原生 Android 下载与离线管理应用。它把链接解析、选集、画质与资源选择、后台下载、任务管理和账号登录放进统一的 Material You 图形界面，不需要命令行。

## 功能

- 识别 BV/AV、投稿视频与多 P 视频
- 支持番剧、课程、收藏夹、稍后再看、个人空间投稿、合集和视频列表
- 可视化选集，全选或逐集选择
- 8K、杜比、4K、1080P、720P 等画质优先级与自动回退
- Hi-Res、杜比、320/128/64kbps 音频优先级与自动回退
- AVC、HEVC、AV1 视频编码偏好
- 视频、音频、字幕、弹幕、封面、媒体元数据和章节信息独立开关
- ASS/XML 弹幕，支持字体、不透明度、速度与关键词过滤
- WorkManager 后台下载、通知进度、暂停、继续、重试和任务记忆
- Android 平台媒体封装，不携带大型转码运行库
- 内置浏览器登录，Cookie 由 WebView 获取并持久保存
- 默认保存到系统 `Download/Bd`，也支持存储访问框架选择目录
- 分享链接到 Bd 或直接打开受支持的网页
- 固定发布签名

## 构建

仓库使用 GitHub Actions 构建，不要求开发设备安装 Android SDK 或 Gradle。推送到 `main` 后，工作流会执行：

1. Android Lint
2. JVM 单元测试
3. Release APK 构建与固定签名
4. 非 arm64 原生库检查
5. APK 证书检查与构建产物上传

构建产物位于 Actions 运行详情的 `Bd-arm64-release` Artifact。

## 技术结构

- Kotlin + Jetpack Compose + Material 3
- WorkManager
- OkHttp
- MediaExtractor + MediaMuxer
- MediaStore + Storage Access Framework

项目不包含第三方原生二进制文件。若后续加入原生依赖，构建配置仅允许 `arm64-v8a`。

## 隐私

Cookie 仅保存在应用私有数据中，并用于向目标站点发起用户请求。应用不会把 Cookie 上传到其他服务。卸载应用或在设置中退出登录会清除应用保存的账号信息。

