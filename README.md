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

## 为什么 v1.3 及以前“完全没生效”

目标 mod 的日志走 **SLF4J**，logger 名是从 Java 类 / modid 派生的；它的调试
mixin 还在 `com.pb7ap8lthh.fmdmod.*`、`dev.ryanhcode.*` 这些包里。1.x 只会挂
**精确 logger 名**的过滤器，名字对不上就等于什么都没装。1.4.0 改成
**规则制**（logger 名或 Java 包名）+ **全局 turbo filter**，并保证“规则里的 logger
名”和“实际出现的 logger 名”无法对不上——即使对不上，`/mfdlogsilencer dump`
也能一眼看出来该写什么。

> 历史注记：v1.0.0 的 `LevelRangeFilter(TRACE, INFO)` 在 log4j2 intLevel 语义
> （数值越小越严重）下是空区间；v1.1.0 修了它，但上面那个 logger 名的问题一直
> 到 1.4.0 才解决。

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
[MFD-LogSilencer] v1.4.0 activated (startup): 3 rule(s) [fixed_tweaked_controller, com.pb7ap8lthh, dev.ryanhcode.sable.Sable], keep=WARN and above
```

## 安装

把 `mfd-log-silencer-1.4.0-1.21.1-neoforge.jar` 放进 `mods/`。客户端、服务端都能用
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
