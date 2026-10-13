# MFD Log Silencer

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/mfd-log-silencer?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/mfd-log-silencer)
[![Modrinth Version](https://img.shields.io/modrinth/v/mfd-log-silencer?logo=modrinth)](https://modrinth.com/mod/mfd-log-silencer)
[![GitHub Release](https://img.shields.io/github/v/release/szkele1145/mfd-log-silencer?logo=github)](https://github.com/szkele1145/mfd-log-silencer/releases)
[![License](https://img.shields.io/github/license/szkele1145/mfd-log-silencer)](LICENSE)

一个 NeoForge 1.21.1 的小型工具 mod：屏蔽 `fixed_tweaked_controller`
（Fixed Tweaked Controller / MFD-FixedController）每秒刷屏的服务端 INFO 日志：

```
[Server thread/INFO] [fixed_tweaked_controller/]: [MFD-FixedController] signals pos=BlockPos{x=...} linked=false values={}
```

## 原理

在 mod 构造阶段给 log4j2 的目标 logger 挂一个自定义的 `AbstractFilter`：

- **拦截**：该 logger 的 `INFO` 及更低级别（`DEBUG` / `TRACE`）
- **放行**：`WARN` / `ERROR`（真正的报错仍然可见）
- **不影响**：其他任何 mod 与原版日志

> 版本注记：v1.0.0 使用的 `LevelRangeFilter(TRACE, INFO)` 参数顺序在 log4j2 的
> intLevel 语义（数值越小越严重）下是空区间，等于没装过滤器。v1.1.0 修复了
> 该问题，并经过独立 JVM 冒烟测试验证（拦截/放行/幂等均符合预期）。

## 配置

首次启动生成 `config/mfd-log-silencer.properties`：

```properties
# 逗号分隔的 logger 名，其 INFO/DEBUG/TRACE 日志会被隐藏
loggers=fixed_tweaked_controller
# 可选：按 logger 覆盖阈值（屏蔽该级别及更低级别）
# fixed_tweaked_controller=INFO
```

- 想屏蔽其他 mod 的刷屏？把它的 logger 名（通常就是它的 modid）加进 `loggers`，逗号分隔
- 想连 WARN 也屏蔽？加一行 `fixed_tweaked_controller=WARN`
- 改完在游戏里执行 `/mfdlogsilencer reload` 即时生效，无需重启

**配置语义**（1.2.4 起）：

| 情况 | 行为 |
|---|---|
| `loggers` 键缺失 | 使用默认目标 `fixed_tweaked_controller` |
| `loggers=` 显式留空 | **关闭屏蔽**（并输出告警） |
| 未知级别值（如 `=WARN2`） | 告警并回退 `INFO` |
| 每行 `<loggerName>=<LEVEL>` | 覆盖该 logger 的阈值（INFO/DEBUG/WARN/ERROR/TRACE） |

## 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/mfdlogsilencer status` | 所有人 | 查看拦截总数、按 logger 细分计数与最近样例 |
| `/mfdlogsilencer reset` | OP (2) | 清零统计数据 |
| `/mfdlogsilencer reload` | OP (2) | 重读配置文件并重装过滤器 |
| `/mfdlogsilencer about` | 所有人 | 版本信息 |

命令只在服务端注册（单机的集成服同样触发），客户端/服务端一套逻辑，无遮蔽问题。

## 安装

把 `build/libs/mfd-log-silencer-1.2.4.jar` 放进 `mods/` 目录即可，
客户端、服务端都能用（纯服务端日志屏蔽，客户端装不装都不影响功能）。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.0+
- [fixed Tweaked Controller](https://modrinth.com/mod/fixed-tweaked-controller) 1.4.0+（要屏蔽的就是它）

## 构建

```
gradlew build
```

（需要在纯 ASCII 路径下构建：本项目的 Gradle/NFRT 工具链在含非 ASCII 字符的
路径上会出现 argfile 编码问题。Windows 下可用 `mklink /J C:\mfd-build <本目录>` 后
在 `C:\mfd-build` 中构建。）

## 许可

MIT
