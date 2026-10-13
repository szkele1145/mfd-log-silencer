package dev.szkele.mfdlogsilencer;

import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;

/**
 * 屏蔽 fixed_tweaked_controller 刷屏的 INFO 日志（signals pos=... ...）。
 *
 * 做法：拿到目标 logger 所在的 log4j2 LoggerConfig，给它的 filter 链头部插入一个
 * LevelRangeFilter：只对【该 logger】的 DEBUG/INFO/TRACE 拒绝，WARN/ERROR 放行。
 * 其他 mod 的 logger 完全不受影响。
 */
@Mod(MfdLogSilencer.MODID)
public final class MfdLogSilencer {
    public static final String MODID = "mfd_log_silencer";
    /** 要屏蔽的模组 logger 名（与 @Mod 注解的 modid 一致，FML 自动以它命名 logger）。 */
    public static final String TARGET_LOGGER = "fixed_tweaked_controller";

    public MfdLogSilencer() {
        apply();
    }

    private static void apply() {
        try {
            var ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            var loggerConfig = config.getLoggerConfig(TARGET_LOGGER);

            // 该 logger 可能没有专属 LoggerConfig（继承 root），先声明专属配置
            if (!TARGET_LOGGER.equals(loggerConfig.getName())) {
                loggerConfig = new org.apache.logging.log4j.core.config.LoggerConfig(
                        TARGET_LOGGER, Level.INFO, true /* additivity: 仍向 root 传递 */);
                loggerConfig.setLevel(Level.INFO);
                config.addLogger(TARGET_LOGGER, loggerConfig);
            }

            LevelRangeFilter filter = LevelRangeFilter.createFilter(
                    Level.TRACE,   // min：从这个级别开始
                    Level.INFO,    // max：到这个级别为止 —— 即拦截 TRACE~INFO
                    org.apache.logging.log4j.core.Filter.Result.DENY,    // 命中区间 -> 拒绝
                    org.apache.logging.log4j.core.Filter.Result.NEUTRAL); // 未命中 -> 放行

            // 插到链头，优先于其他 filter 生效
            loggerConfig.addFilter(filter);

            ctx.updateLoggers();
            System.out.println("[MFD-LogSilencer] 已屏蔽 " + TARGET_LOGGER
                    + " 的 INFO/DEBUG 日志（WARN/ERROR 不受影响）");
        } catch (Throwable t) {
            System.out.println("[MFD-LogSilencer] 日志过滤安装失败: " + t);
        }
    }
}
