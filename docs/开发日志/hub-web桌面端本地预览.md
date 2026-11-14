# hub-web 桌面端本地预览

## 启动

先启动项目后端，再启动页面开发服务：

```bash
cd server && mvn spring-boot:run
cd hub-web && npm run dev
```

打开 Electron 桌面窗口：

```bash
cd hub-web && npm run desktop
```

`npm run desktop` 使用已运行的 `http://127.0.0.1:5174`，保留真实登录、Cookie、CSRF、IM 上传和 WebSocket 链路。

macOS 窗口使用 `titleBarStyle: hiddenInset`，红绿灯由系统绘制。顶栏空白区域可拖动窗口，搜索框和按钮保持可点击。

## SQLite 消息缓存

- 存储：Electron 主进程 `node:sqlite`，文件位于系统用户数据目录 `hub-web/messages.sqlite`。
- 在线拉取、实时消息、发送、撤回后更新缓存。
- 消息请求失败时读取当前用户、当前会话的本地缓存。
- 不保存密码、Token 或其他认证凭证；服务端仍是消息权威来源。

## 打包

```bash
cd hub-web && npm run desktop:pack
```

macOS 应用输出到 `hub-web/desktop-dist/Workforce Hub-darwin-arm64/`。

当前版本面向本地预览和联调，不含代码签名、公证、自动更新或安装包。
