# dr

Android hello world 应用，Kotlin 编写，APK 由 GitHub Actions 云端构建

## 产物

- 应用名 dr
- 包名 com.mrkalilunx.dr
- APK 文件名 dr.apk

## 下载

反代直链（推荐，国内稳定）

https://gh-proxy.com/https://github.com/Mrkalilunx/dr/releases/download/dr-latest/dr.apk

直连（时通时不通，作备选）

https://github.com/Mrkalilunx/dr/releases/download/dr-latest/dr.apk

Actions artifact 名称 dr，需登录 GitHub 后在 Actions 页面下载

## 签名

使用仓库内固定 keystore 签名，签名稳定，可覆盖安装

从旧的 debug 自动签名 APK 首次升级需先卸载一次

## 可复现性

同一提交的多次构建产物 md5 恒定

签名与依赖信息块均已固定

## 悬浮窗

点击“显示悬浮窗”并授予悬浮窗权限后，屏幕右上角会出现动画悬浮球

悬浮球由 floatball 实现：径向渐变球体 + 呼吸缩放 + 旋转流光 + 声呐扩散环 + 轨道粒子，三个 Animator 驱动，零对象分配

悬浮球可拖动，点击后切换 HUD 画布（全屏透明叠加层）

HUD 画布由 drawcanvas 实现：纯展示层，触摸穿透，开着时不影响操作底层应用

HUD 内容：四边白色边框框住整个可用区域，左上角半透明面板显示屏幕信息（分辨率、密度、刷新率、状态栏高度、可用区、系统版本）

再次点击悬浮球关闭 HUD

停止悬浮窗可点击应用内“停止悬浮窗”按钮，或停止前台服务

## 构建

推送 master 分支即触发

本地无需 Android SDK，构建全部在 GitHub Actions 完成

## 版本

- JDK 17
- Gradle 8.5
- AGP 8.2.2
- Kotlin 1.9.22
- compileSdk 34
- minSdk 21
- targetSdk 34
