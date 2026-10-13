package dev.szkele.mfdlogsilencer;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * /mfdlogsilencer status | reload | about
 * Registered on both the server (RegisterCommandsEvent) and, on the client,
 * RegisterClientCommandsEvent so it also works in single player without LAN.
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
        var recent = SilencerStats.getRecent();
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.CONSOLE_PREFIX).append("已拦截 ").append(total).append(" 条日志");
        if (!recent.isEmpty()) {
            sb.append("，最近：");
            int shown = 0;
            for (int i = recent.size() - 1; i >= 0 && shown < 3; i--, shown++) {
                SilencerStats.Entry e = recent.get(i);
                String msg = e.message();
                if (msg.length() > 60) {
                    msg = msg.substring(0, 60) + "...";
                }
                sb.append(" [").append(e.level()).append("] ").append(msg);
            }
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
    }
}
