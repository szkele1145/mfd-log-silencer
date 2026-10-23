# MFD Log Silencer

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/mfd-log-silencer?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/mfd-log-silencer)
[![Modrinth Version](https://img.shields.io/modrinth/v/mfd-log-silencer?logo=modrinth)](https://modrinth.com/mod/mfd-log-silencer)
[![GitHub Release](https://img.shields.io/github/v/release/szkele1145/mfd-log-silencer?logo=github)](https://github.com/szkele1145/mfd-log-silencer/releases)
[![License](https://img.shields.io/github/license/szkele1145/mfd-log-silencer)](LICENSE)

一个 NeoForge 1.21.1 的小型工具 mod：屏蔽 `fixed_tweaked_controller`
（Fixed Tweaked Controller）每秒刷屏的服务端 INFO 日志：

```
[Server thread/INFO] [fixed_tweaked_controller/]: [MFD-FixedController] signals pos=BlockPos{x=...} linked=false values={}
```

## 为什么 v1.4.0 及以前“完全没生效”

**v1.1.1 ~ v1.4.0 的根因：mod 主类丢了 `@Mod` 注解。** NeoForge 靠这个注解发现并
实例化 mod 主类；没有它，mod 构造器**一次都不会执行**，过滤器也就一次都没装上。

迷惑之处在于：mod 列表里**照样显示** `MFD Log Silencer 1.4.0`（那是读
`neoforge.mods.toml` 得到的，与注解无关），但日志里**没有**
`[MFD-LogSilencer] activated`、`config/mfd-log-silencer.properties` 不会生成、
`/mfdlogsilencer` 命令也不存在。

- 引入点：v1.1.1 的“专用服务器安全性修复”（把客户端事件类隔离到 `ClientSetup`）
  误删了 `@Mod(MfdLogSilencer.MODID)` 这行入口注解。
- v1.4.0 修的是“规则匹配”（logger 名 / Java 包名），这是次要问题——没有
  `@Mod` 注解，那个修复根本轮不到执行。

v1.4.1 起：

- 补回 `@Mod` 注解；
- 加**构建护栏** `verifyModEntryPoint`：源码里没有 `@Mod` 注解时直接让构建失败，
  杜绝“mod 在列表里但什么都不做”再次发生；
- 规则匹配也保留并加强：目标 mod 的日志走 **SLF4J**，logger 名由 Java 类 / modid
  派生（`fixed_tweaked_controller`，调试 mixin 在 `com.pb7ap8lthh.fmdmod.*`、
  `dev.ryanhcode.*`），所以规则支持 **logger 名或 Java 包名**。

> 历史注记：v1.0.0 的 `LevelRangeFilter(TRACE, INFO)` 在 log4j2 intLevel 语义
> （数值越小越严重）下是空区间；v1.1.0 修了它，但上面那个 `@Mod` 的问题一直
> 到 1.4.1 才解决。

**如果你的服务器上还是刷屏**，按顺序检查：

1. 启动日志里有没有 `[MFD-LogSilencer] v… activated`？没有 → mod 没被加载
   （jar 放错目录 / 装的是旧版本）。
2. 有 `activated` 但仍在刷 → 看 `activated` 那行列出的规则名，用
   `/mfdlogsilencer dump` 看实际出现的 logger 名，照着加进 `DISABLE=`。

## 原理

在 log4j2 里装**一个全局 turbo filter**（log4j2 在每个事件进入任何
`LoggerConfig` 之前都会先问 turbo filter）：

- 规则命中 → `DENY`，并记一次统计
- 其余一切（含本 mod 自己的 logger、原版、其他 mod）→ `NEUTRAL`，原样放行
- 默认只拦 `INFO` 及更低级别，`WARN`/`ERROR` 永远放行（`KEEP=` 可改）
- 包规则会自动覆盖子包：`DISABLE=com.pb7ap8lthh` 同时命中
  `com.pb7ap8lthh.fmdmod.MFDMOD`、`com.pb7ap8lthh.fmdmod.mixin.*`
- mod 构造时装一次，服务器启动完成后（`ServerStartedEvent`）再挂一次，
  `/mfdlogsilencer reload` 随时热重载

## 配置

首次启动生成 `config/mfd-log-silencer.properties`：

```properties
# 每个 DISABLE 行可以写一个 logger 名或 Java 包，逗号分隔多个；可以写多行
DISABLE=fixed_tweaked_controller
DISABLE=com.pb7ap8lthh
DISABLE=dev.ryanhcode.sable.Sable

# 哪些级别始终放行：warn（默认）/ error / debug / info / trace
KEEP=warn
```

| 键 | 含义 |
|---|---|
| `DISABLE=` | logger 名或 Java 包/类，命中它以及它下面的一切 |
| `KEEP=` | 始终放行的最低级别，默认 `warn`（只拦 INFO/DEBUG/TRACE） |
| `loggers=a,b` | 1.x 旧写法，仍然兼容 |

环境变量覆盖（适合整合包/开服脚本，不用改文件）：

```
-DMFD_DISABLE=mod_a,mod_b          # 替换 DISABLE 规则
-DMFD_DISABLE_CLASSES=pkg1,pkg2    # 追加包规则
```

不知道某个 mod 的 logger 叫什么？先跑一轮，进游戏执行
`/mfdlogsilencer dump`，它会列出运行期出现过的所有 logger 名，照着写进
`DISABLE=` 即可。

## 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/mfdlogsilencer status` | 所有人 | 查看当前规则、已拦截总数、按 logger 细分计数与最近样例 |
| `/mfdlogsilencer dump` | 所有人 | 列出运行期出现过的 logger 名（配规则用） |
| `/mfdlogsilencer reset` | OP (2) | 清零统计 |
| `/mfdlogsilencer reload` | OP (2) | 重读配置并热重载 |
| `/mfdlogsilencer about` | 所有人 | 版本信息 |

启动日志里应出现（证明已生效）：

```
[MFD-LogSilencer] v1.4.1 activated (startup): 3 rule(s) [fixed_tweaked_controller, com.pb7ap8lthh, dev.ryanhcode.sable.Sable], keep=WARN and above
```

## 安装

把 `mfd-log-silencer-1.4.1-1.21.1-neoforge.jar` 放进 `mods/`。客户端、服务端都能用
（纯日志屏蔽，装哪边都行）。

> **前置 mod：[fixed Tweaked Controller](https://modrinth.com/mod/fixed-tweaked-controller)**
> ——本 mod 就是为它做的（依赖声明为 optional，没装也能跑）。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.0+

## 构建

```
gradlew build
```

产物：`build/libs/mfd-log-silencer-<版本>-1.21.1-neoforge.jar`

构建机注意（本仓库的开发机环境）：

- 工程路径含非 ASCII 字符时，Gradle worker 的 classpath argfile 编码会坏，
  用 `mklink /J C:\mfd-build <本目录>` 在纯 ASCII 路径下构建
- 需要 **JDK 21** 且用作 Gradle 自身 JVM（NeoForm 重编译要 `javax.tools`，
  只有 JRE 21 不够）；`JAVA_HOME` 指向 JDK 21 即可
- 冒烟测试：`tools/harness/Slf4jSilenceTest.java`（log4j 2.22.1 + SLF4J，
  7 个场景覆盖拦截/放行/热重载/空配置回退）

## 许可

MIT
