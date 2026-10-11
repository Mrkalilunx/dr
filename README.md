# dr

Android hello world 应用，Kotlin 编写，APK 由 GitHub Actions 云端构建

## 产物

- 应用名 dr
- 包名 com.mrkalilunx.dr
- APK 文件名 dr.apk

## 下载

Release 直链

https://github.com/Mrkalilunx/dr/releases/download/dr-latest/dr.apk

国内可用反代前缀

https://gh-proxy.com/https://github.com/Mrkalilunx/dr/releases/download/dr-latest/dr.apk

Actions artifact 名称 dr，需登录 GitHub 后在 Actions 页面下载

## 构建

推送 master 分支即触发

本地无需 Android SDK，构建全部在 GitHub Actions 完成

## 签名

release 使用 debug keystore 签名，可直接安装

## 版本

- JDK 17
- Gradle 8.5
- AGP 8.2.2
- Kotlin 1.9.22
- compileSdk 34
- minSdk 21
- targetSdk 34
