# 项目交接摘要（给你的便签）

> 写于 2026-10-14 上午。你睡觉期间（约 22:00 - 11:00）项目从 1.0.0 迭代到 1.2.5，
> 这里是你需要知道的一切。

## 现在能直接用的

**服务器**：`F:\vibe coding工程\ModSync\mods\mfd-log-silencer-1.2.5.jar` 已就位，
重启服务器即生效。启动日志应出现：

```
[MFD-LogSilencer] v1.2.5 activated: 1 logger(s) silenced at INFO/DEBUG (WARN/ERROR pass through)
```

游戏内：`/mfdlogsilencer status`（看拦截了多少条刷屏日志）、`reload`（改配置后热重载）。

## 发布渠道（全绿）

| 渠道 | 地址 | 状态 |
|---|---|---|
| Modrinth | https://modrinth.com/mod/mfd-log-silencer | 9 版本，审核中（`processing`，官方口径 2 天~4 周，期间可正常下载） |
| GitHub | https://github.com/szkele1145/mfd-log-silencer | 仓库 + Release v1.2.5（附 jar）+ topics + 双语 README + 审查记录 |
| 本地 | `ModSync\mods\` | 1.2.5 已复制 |

## 你睡觉期间修掉的真 bug（5 个，全部实证）

1. **v1.0.0 过滤器从未生效**（致命）：`LevelRangeFilter(TRACE, INFO)` 在 log4j2
   intLevel 语义下是空区间。你原本的刷屏问题直到 v1.1.0 才真正解决。
2. 专用服务器类加载安全隐患（客户端事件类隔离）
3. 单机命令遮蔽问题（收敛到单注册路径）
4. reload 后配置中删除的 logger 过滤器残留
5. `loggers=` 留空被静默回退默认（违背"关闭屏蔽"意图）

完整清单：[docs/REVIEW-LOG.md](REVIEW-LOG.md)（8 轮审查 7 bug，含环境坑）。

## 新功能（相对最初的 1.0.0）

- `config/mfd-log-silencer.properties`：自定义屏蔽列表、按 logger 覆盖阈值、
  显式关闭屏蔽；改完 `/mfdlogsilencer reload` 热生效
- `/mfdlogsilencer status | reset | reload | about` 四个子命令
- 按 logger 的拦截统计 + 最近样例展示

## 待办（等你决定）

- **CI**：工作流已写好（`docs/github-workflow-build.yml`），但当前 GitHub token
  缺 `workflow` scope 推不上去。换一个带 workflow scope 的 token 后把文件移回
  `.github/workflows/build.yml` 即启用。
- **Modrinth 审核**：纯等待，不需要催（官方明确说催了更慢）。注意：我们做的是
  运行时 log4j2 注入，可能被恶意软件扫描器标记转人工审——代码干净（MIT、全开源），
  [REVIEW-LOG.md](REVIEW-LOG.md) 就是给审核员的自证材料。
- **Fabric 版**：没做。目标 mod 只有 NeoForge 版，Fabric 版无实际需求；要做再说。

## 构建须知（重要）

工程路径含中文，构建链路（Gradle worker argfile / NFRT javac）会因编码不一致炸掉。
标准构建姿势：

```powershell
# 一次性（已配置好）：
#   junction: C:\mfd-build -> F:\vibe coding工程\mfd-log-silencer
#   GRADLE_USER_HOME=C:\gradle-home（含代理配置）
$env:JAVA_HOME = "C:\Users\一只屑\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
$env:TEMP = "C:\mfdtmp"; $env:TMP = "C:\mfdtmp"; $env:GRADLE_USER_HOME = "C:\gradle-home"
cd C:\mfd-build
.\gradlew.bat build
```

发 Modrinth 新版本：`$env:MR_TOKEN=<token>; powershell -File tools\publish-version.ps1 -Version x.y.z`
