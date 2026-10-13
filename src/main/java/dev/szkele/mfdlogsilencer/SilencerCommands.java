package dev.szkele.mfdlogsilencer;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * /mfdlogsilencer status | reload | about
 *
 * Registered only via RegisterCommandsEvent (server side). In single player the
 * integrated server fires that event too, so the command works everywhere the mod
 * is loaded. We deliberately do NOT also register on RegisterClientCommandsEvent:
 * a client command with the same name shadows the server command in single player,
 * which would break the permission-checked /reload subcommand.
 */
public final class SilencerCommands {
    private SilencerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("mfdlogsilencer")
                .then(literal("status")
                        .executes(ctx -> {
                            report(ctx.getSource());
                            return 1;
                        }))
                .then(literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            SilencerStats.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    Constants.CONSOLE_PREFIX + "统计已清零"), true);
                            return 1;
                        }))
                .then(literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            SilencerConfig cfg = SilencerConfig.load(MfdLogSilencer.configPath());
                            int n = FilterInstaller.apply(cfg);
                            MfdLogSilencer.setConfig(cfg);
                            final String msg = Constants.CONSOLE_PREFIX + "已重载配置，重新安装 " + n + " 个日志过滤器";
                            src.sendSuccess(() -> Component.literal(msg), true);
                            return 1;
                        }))
                .then(literal("about")
                        .executes(ctx -> {
                            final String msg = Constants.CONSOLE_PREFIX + "MFD Log Silencer v" + MfdLogSilencer.VERSION
                                    + " - 屏蔽指定 logger 的 INFO/DEBUG 刷屏，保留 WARN/ERROR";
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            return 1;
                        })));
    }

    private static void report(CommandSourceStack src) {
        long total = SilencerStats.getTotal();
        var perLogger = SilencerStats.getPerLogger();
        var recent = SilencerStats.getRecent();

        StringBuilder sb = new StringBuilder();
        sb.append(Constants.CONSOLE_PREFIX).append("已拦截 ").append(total).append(" 条日志");
        src.sendSuccess(() -> Component.literal(sb.toString()), false);

        if (!perLogger.isEmpty()) {
            StringBuilder per = new StringBuilder(Constants.CONSOLE_PREFIX);
            boolean first = true;
            for (var e : perLogger.entrySet()) {
                if (!first) {
                    per.append(", ");
                }
                per.append(e.getKey()).append(": ").append(e.getValue());
                first = false;
            }
            src.sendSuccess(() -> Component.literal(per.toString()), false);
        }

        if (!recent.isEmpty()) {
            StringBuilder recentSb = new StringBuilder(Constants.CONSOLE_PREFIX).append("最近：");
            int shown = 0;
            for (int i = recent.size() - 1; i >= 0 && shown < 3; i--, shown++) {
                SilencerStats.Entry e = recent.get(i);
                String msg = e.message();
                if (msg.length() > 60) {
                    msg = msg.substring(0, 60) + "...";
                }
                recentSb.append(" [").append(e.level()).append("] ").append(msg);
            }
            src.sendSuccess(() -> Component.literal(recentSb.toString()), false);
        }
    }
}
