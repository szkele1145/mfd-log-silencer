package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Config file support: config/mfd-log-silencer.properties
 *
 * Format (Java properties):
 * <pre>
 * # comma separated logger names to silence (INFO/DEBUG/TRACE denied, WARN/ERROR kept)
 * loggers=fixed_tweaked_controller
 * # optional per-logger level threshold override, e.g. fixed_tweaked_controller=WARN
 * </pre>
 *
 * Defaults are written on first run so users can edit and /mfdlogsilencer reload.
 */
public final class SilencerConfig {
    /** Ordered map: logger name -> threshold level (deny at and below this level... inclusive). */
    private final Map<String, Level> targets = new LinkedHashMap<>();
    private final Path file;

    public SilencerConfig(Path file) {
        this.file = file;
        targets.put(Constants.DEFAULT_TARGET_LOGGER, Level.INFO);
    }

    public static SilencerConfig load(Path file) {
        SilencerConfig cfg = new SilencerConfig(file);
        try {
            if (!Files.exists(file)) {
                cfg.saveDefaults();
                return cfg;
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
            }
            String loggers = props.getProperty("loggers", Constants.DEFAULT_TARGET_LOGGER);
            Map<String, Level> parsed = new LinkedHashMap<>();
            for (String raw : loggers.split(",")) {
                String name = raw.trim();
                if (name.isEmpty()) {
                    continue;
                }
                Level lvl = parseLevel(props.getProperty(name, "INFO"));
                parsed.put(name, lvl);
            }
            if (!parsed.isEmpty()) {
                cfg.targets.clear();
                cfg.targets.putAll(parsed);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println(Constants.CONSOLE_PREFIX + "配置读取失败，使用默认值: " + e);
        }
        return cfg;
    }

    private static Level parseLevel(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "TRACE" -> Level.TRACE;
            case "DEBUG" -> Level.DEBUG;
            case "INFO" -> Level.INFO;
            case "WARN", "WARNING" -> Level.WARN;
            case "ERROR" -> Level.ERROR;
            default -> Level.INFO;
        };
    }

    private void saveDefaults() {
        List<String> lines = new ArrayList<>();
        lines.add("# MFD Log Silencer configuration");
        lines.add("# loggers: comma separated logger names whose INFO/DEBUG/TRACE messages are hidden.");
        lines.add("# WARN/ERROR always pass through. Add more loggers (comma separated) to silence other mods.");
        lines.add("# Per-logger threshold override (optional): <loggerName>=INFO|WARN|ERROR");
        lines.add("# After editing run /mfdlogsilencer reload in-game (or restart).");
        lines.add("loggers=" + Constants.DEFAULT_TARGET_LOGGER);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println(Constants.CONSOLE_PREFIX + "默认配置写入失败: " + e);
        }
    }

    /** Read-only view of logger name -> deny-at level. */
    public Map<String, Level> getTargets() {
        return Map.copyOf(targets);
    }

    public Path getFile() {
        return file;
    }
}
