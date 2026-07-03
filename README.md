# IPv6 HTTP 文件共享服务器 (Android APK)

一个简单的 Android 应用，可以将手机某个文件夹通过 HTTP 协议共享出来，支持 IPv6 地址。

## 功能特点

- 选择手机上任意文件夹（使用现代 SAF 存储访问框架）
- 指定端口号
- 使用 IPv6 地址进行 HTTP 文件共享
- 简单的 Web 界面，其他手机/设备可以通过浏览器访问并下载文件
- 前台服务，支持后台运行
- GitHub Actions 自动编译生成 APK

## 快速开始

1. 在 GitHub 上点击 **Actions** 栏目
2. 选择 **Build APK** workflow
3. 点击 **Run workflow** 或者 push 代码自动触发
4. 等待编译完成，在 Artifacts 中下载 `app-debug.apk`
5. 安装到 Android 手机（需要开启“来源不明应用”）

## 使用方法

1. 打开 App
2. 点击“选择共享文件夹”，选择你想共享的文件夹
3. 输入端口号（默认 8080）
4. 点击“启动服务器”
5. App 会显示 IPv6 地址和 Web 链接，例如：
   `http://[2001:db8::1234]:8080/`
6. 在同一局域网的其他设备上用浏览器打开该链接，即可浏览并下载文件

## 注意事项

- **IPv6** ：手机需要有 IPv6 地址（WiFi 或移动数据支持 IPv6）
- **安全** ：当前版本无密码保护，建议在只有信任的局域网使用
- **后台运行** ：需要允许应用后台运行和通知权限
- 支持 Android 8.0+ (API 26+)

## 技术栈

- Kotlin + AndroidX
- NanoHTTPD 轻量级 HTTP 服务器
- Storage Access Framework (SAF) 选择文件夹
- GitHub Actions 自动化构建

## 自定义开发

欢迎提交 PR 或发发 issue 改进功能！
