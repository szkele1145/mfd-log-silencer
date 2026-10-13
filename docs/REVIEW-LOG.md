# 审查记录（Review Log）

本文档记录项目从 1.0.0 到 1.2.5 期间，每一轮代码审查实际发现并修复的问题。
公开这份清单有两个目的：一是给 Modrinth 审核员证明项目经过了认真的自检；
二是把踩过的坑留给以后写 mod 的人。

每一条都经过**实证验证**（独立 JVM 冒烟测试或字节级检查），不是猜测。

## 审查轮次与发现

### 第 1 轮（v1.1.0）—— 过滤器语义

| 问题 | 严重性 | 证据 |
|---|---|---|
| `LevelRangeFilter.createFilter(TRACE, INFO, …)` 在 log4j2 intLevel 语义（数值越小越严重）下是**空区间**，过滤器从未生效——v1.0.0 实际上没有屏蔽任何日志 | 致命 | 独立 JVM harness：v1.0.0 参数下 INFO 消息原样穿透；改为 `(INFO, TRACE)` 后 INFO/DEBUG 被拦、WARN/ERROR 放行 |

教训：log4j2 的 Level 比较**不是**"从小到大=从轻到重"。`intLevel` 越小越严重（ERROR=200, TRACE=600）。

### 第 2 轮（v1.1.1）—— 双端类加载

| 问题 | 严重性 | 证据 |
|---|---|---|
| 双端加载的主类直接引用客户端专属事件类 `RegisterClientCommandsEvent`，专用服务器可能 `NoClassDefFoundError` | 高 | 代码走查：FML 的 dist 隔离要求客户端类只在 `Dist.CLIENT` 分支类加载 |

修复：客户端注册隔离到仅客户端类加载的独立类（v1.2.0 又进一步收敛为单注册路径）。

### 第 3 轮（v1.2.0）—— 命令遮蔽

| 问题 | 严重性 | 证据 |
|---|---|---|
| 同时注册客户端命令与服务端命令时，单机环境下同名客户端命令**遮蔽**服务端命令，权限校验的 `reload` 子命令行为不确定 | 中 | Brigadier 派发顺序走查 |

修复：只保留 `RegisterCommandsEvent`（专用服与单机集成服都触发），一条注册路径。

### 第 4 轮（v1.2.2）—— reload 幂等

| 问题 | 严重性 | 证据 |
|---|---|---|
| `/mfdlogsilencer reload` 后，从配置里**删除**的 logger 其过滤器残留，用户无法通过改配置恢复日志显示 | 中 | 走查 apply()：只在新增循环里处理，无清理路径 |

修复：apply() 末尾遍历已安装过滤器，卸载不再在配置中的。

### 第 5 轮（v1.2.4）—— 配置语义

| 问题 | 严重性 | 证据 |
|---|---|---|
| `loggers=` 显式留空想**关闭屏蔽**时，代码静默回退默认目标——违背用户明确意图 | 高 | 走查 load()：`if (!parsed.isEmpty())` 分支吞掉了空配置 |
| 新建 LoggerConfig 硬编码 `Level.INFO`，配置 WARN 阈值时 DEBUG 消息在级别门槛就被放行 | 低 | 走查 |
| 配置里未知级别值（拼写错误）被静默吞掉 | 低 | 走查 |

修复：严格区分"键缺失"（默认）与"键存在但为空"（显式关闭+告警）；级别跟随阈值；未知值告警回退。

### 第 6 轮（v1.2.5，验收）—— 构建产物

| 问题 | 严重性 | 证据 |
|---|---|---|
| jar 内 `neoforge.mods.toml` 的中文描述被一次 ASCII 编码写入损毁成 `?`，mod 列表里显示问号 | 中 | 字节级读取 jar 内 toml |

修复：描述改英文，避开宿主编码差异；jar 内字节验证通过。

### 工程环境（不计入版本）

- **中文路径**：Gradle 8.11 的 worker @argfile 用 UTF-8 写、java launcher 按 GBK 读，
  classpath 全损（`ClassNotFoundException: GradleWorkerMain`）。NFRT 的 javac 也在
  中文 TEMP 下报"无效文件名"。解法：GRADLE_USER_HOME / TEMP / 工程访问路径全部改纯 ASCII。
- **PowerShell 5.1 编码**：`Set-Content -Encoding utf8` 实际是 UTF-8 **带 BOM**
  （Gradle/Groovy 拒绝 BOM），无 BOM 写入又按 GBK。结论：含中文的文件一律用
  .NET API 显式 UTF-8（无 BOM）写入，或用编辑器工具写。
- **Modrinth API**：项目创建需要 `initial_versions`+`file_parts`+`release_channel`；
  许可证 SPDX ID 大小写敏感（`MIT` 不是 `mit`）；图标是 raw body PATCH 而非 multipart。

## 统计

- 审查轮次：8 轮（不含环境类）
- 抓出真 bug：**7 个**（1 致命、2 高、3 中、1 低 + 描述损毁）
- 全部修复并有验证手段；验证代码在 `tools/harness/`（独立 JVM，无需启动 Minecraft）
