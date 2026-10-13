# MFD Log Silencer

一个 NeoForge 1.21.1 的小型工具 mod：屏蔽 `fixed_tweaked_controller`
（Fixed Tweaked Controller / MFD-FixedController）每秒刷屏的服务端 INFO 日志：

```
[Server thread/INFO] [fixed_tweaked_controller/]: [MFD-FixedController] signals pos=BlockPos{x=...} linked=false values={}
```

## 原理

在 mod 构造阶段给 log4j2 的 `fixed_tweaked_controller` logger 挂一个
`LevelRangeFilter`：

- **拦截**：该 logger 的 `TRACE` / `DEBUG` / `INFO` 日志
- **放行**：`WARN` / `ERROR`（真正的报错仍然可见）
- **不影响**：其他任何 mod 与原版日志

同时通过 `Configurator.setLevel` 把该 logger 的有效级别压到 `WARN`，
即使过滤器未生效（例如其他日志后端）也不会刷屏。

## 安装

把 `build/libs/mfd-log-silencer-1.0.0.jar` 放进 `mods/` 目录即可，
客户端、服务端都能用（纯服务端日志屏蔽，客户端装不装都不影响功能）。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.0+
- [fixed Tweaked Controller](https://modrinth.com/mod/fixed-tweaked-controller) 1.4.0+（要屏蔽的就是它）

## 构建

```
gradlew build
```

## 许可

MIT
