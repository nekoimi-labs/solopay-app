# 合并上游 SmsForwarder 时请检查的差异

本 fork 在**尽量不改动上游业务源码**的前提下接入了 SoloPay。合并 `pppscn/SmsForwarder` 后请重点人工检查：

## 1. 通知桥接（唯一推荐改动的 Kotlin 点）

- 文件：`app/src/main/kotlin/cn/ppps/forwarder/service/NotificationService.kt`
- 在 `onNotificationPosted` 的 `try` 开头增加了：

  `SoloPayNotificationBridge.onNotificationPosted(applicationContext, sbn)`

若上游修改了该方法签名或结构，请把上述**一行**按新结构合并回去。

## 2. 启动入口

- 文件：`app/src/main/AndroidManifest.xml`
- `SplashActivity` 已去掉 `MAIN` / `LAUNCHER`，避免与 SoloPay 双图标；默认桌面入口为 **SoloPay**（来自 `:solopay` 模块 manifest 合并）。
- 用户仍可从 SoloPay「设备绑定」页进入 **原 MainActivity**（高级）。

## 3. Gradle

- `settings.gradle`：`include ':solopay'`
- `app/build.gradle`：`implementation project(':solopay')`

## 4. 模块 `:solopay`

- 所有 SoloPay 产品逻辑（绑定、HMAC、解析、队列、UI）位于 `solopay/` 目录，合并上游时通常**无需**触碰；仅需解决与本模块无关的 Gradle/Manifest 冲突即可。
