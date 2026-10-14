# Changelog

All notable changes to this project are documented in this file.
格式参考 Keep a Changelog；版本号语义为 SemVer。

## [1.3.0] - 2026-10-14

### Fixed
- **真实服务器不拦截**：用户实测 1.2.5 在专用服务器上仍在刷屏（纯 log4j2 冒烟
  测试却通过）。根因方向：NeoForge/服务器初始化可能在 mod 构造之后重建/替换
  log4j2 Configuration，导致挂在 LoggerConfig 上的过滤器丢失。

### Added
- **TurboFilter 全局兜底层**：log4j2 在任何事件进入 LoggerConfig 之前先询问
  turbo filter，不受 Configuration 重建影响；配置 reload 时原子换规则集。
- 前置 `fixed_tweaked_controller` 改为 `optional`（过滤器按名字工作，
  目标 mod 不在时无需强制装它，也便于独立测试）。
- 冒烟测试 v2：新增"重装后仍拦"与"空配置正确关闭"两个场景。

### Note
- 请实测：重启服务器后刷屏应停止。若仍刷屏，把启动日志里
  `[MFD-LogSilencer]` 开头的行发给开发者。

## [1.2.5] - 2026-10-14

### Fixed
- **最终验收发现**：jar 内 `neoforge.mods.toml` 的中文描述被此前一次 ASCII 编码
  写入损毁成 `?`（mod 列表里会显示问号）。描述改为英文，彻底避开宿主编码差异。

### Changed
- 定为最终稳定版：1.0.0 → 1.2.5 共 9 个版本，核心逻辑经八轮审查全部实证修复。

## [1.2.4] - 2026-10-14

### Fixed
- **配置语义修复**：`loggers=` 显式留空现在表示"关闭屏蔽"（v1.2.3 之前会静默
  回退到默认目标，违背用户意图）；`loggers` 键缺失仍使用默认目标。
- 新建 LoggerConfig 的级别跟随配置的阈值（之前硬编码 INFO，配置 WARN 阈值时
  DEBUG 消息会先被级别门槛放行到 filter 之前——行为不完整）。
- 配置里写了未知级别（如 `=WARNING2`）时告警并回退 INFO，不再静默。

## [1.2.3] - 2026-10-14

### Changed
- 配置文件里 `loggers=` 留空时启动即告警（之前会静默不装任何过滤器）。

## [1.2.2] - 2026-10-14

### Fixed
- `/mfdlogsilencer reload` 后，从配置中删除的 logger 其过滤器不会被移除，
  导致无法通过编辑配置文件恢复日志显示。现在 reload 会清理已删除 logger 的过滤器。

### Changed
- Modrinth 项目页新增 gallery 封面与 GitHub 源码/Issues 链接（清除平台 nag）。

## [1.2.1] - 2026-10-14

### Changed
- 控制台输出改用 mod 自己的 log4j logger（mfd_log_silencer），替代 System.out，
  便于服务器日志 grep 与级别管理；安装失败会以 ERROR 级别记录堆栈。
- Modrinth 项目页面更新（英文简介 + 中文详细说明，符合 Modrinth 非英语内容规范）。

## [1.2.0] - 2026-10-14

### Changed
- 命令注册收敛到唯一的 `RegisterCommandsEvent`（专用服 + 单机集成服都会触发）。
  移除了 v1.1.x 的客户端命令注册——同名客户端命令会在单机环境遮蔽服务端命令，
  导致权限校验过的 `reload` 子命令行为不确定。
- 控制台输出改为英文（避免宿主文件编码差异引入乱码；游戏内聊天仍为中文）。

### Added
- `/mfdlogsilencer reset`：清零统计数据（OP 权限）。
- `status` 现在按 logger 细分显示拦截条数。

### Fixed
- 构建脚本 BOM 问题（PowerShell 编辑引入的 UTF-8 BOM 导致 Gradle/Java 编译失败）。

## [1.1.1] - 2026-10-14

### Fixed
- 专用服务器类加载安全：客户端专属事件类（`RegisterClientCommandsEvent`）的引用
  隔离到仅在客户端类加载的 `ClientSetup`，避免专用服出现 `NoClassDefFoundError`。

### Added
- 过滤器安装数为 0 时输出警告，提示检查配置文件。

## [1.1.0] - 2026-10-14

### Fixed
- **关键修复**：v1.0.0 的 `LevelRangeFilter(TRACE, INFO)` 在 log4j2 intLevel 语义
  （数值越小越严重）下是空区间，过滤器实际从未生效。修正为正确的区间判断，
  并用独立 JVM 冒烟测试验证：INFO/DEBUG 拦截、WARN/ERROR 放行、其他 logger
  不受影响、重复安装幂等。

### Added
- 配置文件 `config/mfd-log-silencer.properties`：自定义要屏蔽的 logger 列表，
  以及按 logger 覆盖阈值（例如连 WARN 一起屏蔽，或屏蔽其他 mod 的刷屏）。
- 游戏内命令 `/mfdlogsilencer status | reload | about`。
- 拦截统计（总数计数 + 最近被拦消息环形缓冲），通过 `status` 展示。

## [1.0.0] - 2026-10-13

### Added
- 初始版本：屏蔽 `fixed_tweaked_controller` 每秒刷屏的 INFO 日志。
- NeoForge 1.21.1，Modrinth / GitHub 发布。
