package dev.szkele.mfdlogsilencer;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * /mfdlogsilencer status | reset | reload | dump | about
 *
 * Registered only via RegisterCommandsEvent (server side). In single player the
 * integrated server fires that event too, so there is one code path and no
 * client/server command shadowing.
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
                            SilencerConfig.resetSeen();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    Constants.CONSOLE_PREFIX + "统计已清零"), true);
                            return 1;
                        }))
                .then(literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            SilencerConfig cfg = SilencerConfig.load(MfdLogSilencer.configPath());
                            boolean active = FilterInstaller.apply(cfg);
                            MfdLogSilencer.setConfig(cfg);
                            final String msg = Constants.CONSOLE_PREFIX + "已重载配置: "
                                    + cfg.getNames().size() + " rule(s) "
                                    + cfg.getNames() + ", keep="
                                    + MfdLogSilencer.levelName(cfg.getKeepInt())
                                    + (active ? "" : " (no rules - nothing is silenced)");
                            src.sendSuccess(() -> Component.literal(msg), true);
                            return 1;
                        }))
                .then(literal("dump")
                        .executes(ctx -> {
                            var seen = SilencerConfig.seenLoggers();
                            String list = seen.size() > 25 ? seen.subList(0, 25) + " ..." : seen.toString();
                            final String msg = Constants.CONSOLE_PREFIX + "loggers seen so far ("
                                    + seen.size() + "): " + list;
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            return 1;
                        }))
                .then(literal("about")
                        .executes(ctx -> {
                            final String msg = Constants.CONSOLE_PREFIX + "MFD Log Silencer v"
                                    + MfdLogSilencer.VERSION
                                    + " - hides INFO level spam of the configured loggers/packages,"
                                    + " keeps WARN/ERROR";
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            return 1;
                        })));
    }

    private static void report(CommandSourceStack src) {
        SilencerConfig cfg = MfdLogSilencer.getConfig();
        long total = SilencerStats.getTotal();
        var perLogger = SilencerStats.getPerLogger();
        var recent = SilencerStats.getRecent();

        String headline = Constants.CONSOLE_PREFIX + "v" + MfdLogSilencer.VERSION + " blocked "
                + total + " message(s); rules=" + cfg.getNames() + ", keep="
                + MfdLogSilencer.levelName(cfg.getKeepInt());
        src.sendSuccess(() -> Component.literal(headline), false);

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
            StringBuilder recentSb = new StringBuilder(Constants.CONSOLE_PREFIX).append("recent: ");
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
