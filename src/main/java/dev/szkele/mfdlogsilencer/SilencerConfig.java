package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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
 * config/mfd-log-silencer.properties
 *
 * <pre>
 * DISABLE=com.pb7ap8lthh            # one rule per line, comma separated
 * DISABLED_TARGETS=fixed_tweaked_controller
 * KEEP=warn
 * </pre>
 *
 * Rule keys (case-insensitive, may be repeated, values comma/space separated):
 * <ul>
 *   <li>{@code DISABLE=...}            logger name, or package / class prefix</li>
 *   <li>{@code DISABLED_TARGETS=...}    mod ids</li>
 *   <li>{@code DISABLED_PACKAGES=...}   Java package prefixes</li>
 *   <li>{@code DISABLED_LOGGERS=...}    exact logger names</li>
 *   <li>{@code KEEP=level}              levels that always pass (default {@code warn})</li>
 * </ul>
 *
 * Legacy format still supported: {@code loggers=a,b} (+ optional {@code a=WARN} lines).
 * Blank / missing file -> built-in {@link Constants#DEFAULT_RULES}.
 */
public final class SilencerConfig {
    static final int DEFAULT_KEEP = Level.WARN.intLevel();

    /** one rule: literal name (or mod id / package), plus its suffix anchors */
    static final class Rule {
        final String name;
        /** anchors: each one matches itself and every logger whose name continues with '.' */
        final String[] anchors;
        final boolean exactOnly;

        Rule(String name, String[] anchors, boolean exactOnly) {
            this.name = name;
            this.anchors = anchors;
            this.exactOnly = exactOnly;
        }
    }

    private static final Rule[] NO_RULES = new Rule[0];

    private volatile Rule[] rules = NO_RULES;
    private volatile int keepInt = DEFAULT_KEEP;
    private final Path file;
    private String raw = "";

    private SilencerConfig(Path file) {
        this.file = file;
    }

    // ------------------------------------------------------------------ loading

    public static SilencerConfig load(Path file) {
        try {
            if (!Files.exists(file)) {
                saveDefault(file);
                return parse(file, Constants.DEFAULT_RULES);
            }
            String text;
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                text = sb.toString();
            }
            return parse(file, text);
        } catch (IOException e) {
            MfdLogSilencer.LOGGER.error("failed to read {}, using built-in defaults", file, e);
            return parse(file, Constants.DEFAULT_RULES);
        }
    }

    /** Parses configuration text; also used by the standalone harness. */
    public static SilencerConfig parse(Path file, String text) {
        SilencerConfig cfg = new SilencerConfig(file);
        String effective = (text == null || text.isBlank()) ? Constants.DEFAULT_RULES : text;
        cfg.raw = effective;
        cfg.configure(effective);
        return cfg;
    }

    private void configure(String text) {
        // NOTE: java.util.Properties drops repeated keys, so every line is parsed by hand
        // (repeated "DISABLE=..." lines are the documented way to add rules).
        List<String[]> entries = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!") || eq <= 0) {
                continue;
            }
            entries.add(new String[] {t.substring(0, eq).trim(), t.substring(eq + 1).trim()});
        }
        Map<String, Boolean> names = new LinkedHashMap<>(); // name -> exactOnly
        int keep = DEFAULT_KEEP;
        boolean sawRule = false;
        boolean legacyEmpty = false;
        Map<String, Level> legacyThreshold = new LinkedHashMap<>();

        for (String[] kv : entries) {
            String propName = kv[0];
            String value = kv.length > 1 ? kv[1] : "";
            switch (propName.trim().toUpperCase(Locale.ROOT)) {
                case "DISABLE", "DISABLED", "SILENCE" -> {
                    split(value).forEach(n -> names.putIfAbsent(n, false));
                    sawRule = true;
                }
                case "DISABLED_TARGETS", "TARGETS" -> {
                    split(value).forEach(n -> names.putIfAbsent(n, false));
                    sawRule = true;
                }
                case "DISABLED_PACKAGES", "PACKAGES", "LOGGER_PACKAGES" -> {
                    split(value).forEach(n -> names.putIfAbsent(n, false));
                    sawRule = true;
                }
                case "DISABLED_LOGGERS", "LOGGERS_DISABLED" -> {
                    split(value).forEach(n -> names.putIfAbsent(n, true));
                    sawRule = true;
                }
                case "KEEP", "KEEP_LEVEL" -> {
                    Level l = parseLevel(value);
                    if (l != null) {
                        keep = l.intLevel();
                    }
                    sawRule = true;
                }
                case "LOGGERS" -> {
                    List<String> legacyNames = split(value);
                    if (legacyNames.isEmpty()) {
                        legacyEmpty = true;
                    }
                    legacyNames.forEach(n -> names.putIfAbsent(n, false));
                    sawRule = true;
                }
                default -> {
                    if (propName.contains(".")) {
                        // legacy per-logger threshold line
                        Level l = parseLevel(value);
                        legacyThreshold.put(propName.trim(), l == null ? Level.INFO : l);
                        sawRule = true;
                    }
                }
            }
        }
        // environment overrides (useful for server packs: -DMFD_DISABLE=...)
        String envDisable = System.getProperty("MFD_DISABLE");
        if (envDisable != null && !envDisable.isBlank()) {
            names.clear();
            split(envDisable).forEach(n -> names.putIfAbsent(n, false));
            sawRule = true;
        }
        String envClasses = System.getProperty("MFD_DISABLE_CLASSES");
        if (envClasses != null && !envClasses.isBlank()) {
            split(envClasses).forEach(n -> names.putIfAbsent(n, false));
            sawRule = true;
        }
        if (!sawRule) {
            // fresh or blank file: fall back to the built-in rules
            List<String> defaults = new ArrayList<>();
            for (String line : Constants.DEFAULT_RULES.split("\\R")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    defaults.add(t);
                }
            }
            configure(String.join("\n", defaults));
            return;
        }
        if (legacyEmpty && names.isEmpty()) {
            MfdLogSilencer.LOGGER.warn("config has empty 'loggers=' - silencing is disabled");
        }
        for (Map.Entry<String, Level> e : legacyThreshold.entrySet()) {
            Level l = e.getValue();
            if (l != Level.INFO) {
                MfdLogSilencer.LOGGER.warn("per-logger threshold '{}={}' is no longer supported; "
                        + "use KEEP={} to change which levels are kept", e.getKey(), l, l);
            }
        }
        this.keepInt = keep;
        Rule[] built = new Rule[names.size()];
        int i = 0;
        for (Map.Entry<String, Boolean> e : names.entrySet()) {
            built[i++] = new Rule(e.getKey(), anchorsOf(e.getKey()), e.getValue());
        }
        this.rules = built;
    }

    /**
     * Anchors for one rule. Short names (mod ids, single segments) also anchor on their
     * dotted form, because mods commonly derive their slf4j logger name from the mod id
     * either as a single segment ("fixed_tweaked_controller") or as a package-ish name
     * ("fixed_tweaked_controller.core"). Dotted names match through their package prefix.
     */
    private static String[] anchorsOf(String name) {
        String n = strip(name);
        if (n.indexOf('.') >= 0) {
            return new String[] {n};
        }
        return new String[] {n, n.replace('_', '.'), n.replace('-', '.'), n.replaceAll("[^A-Za-z0-9]", ".")};
    }

    private static String strip(String s) {
        String t = s.trim();
        while (t.startsWith(".")) {
            t = t.substring(1);
        }
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[,\\s]+")) {
            String p = strip(part);
            if (!p.isEmpty() && !p.startsWith("#")) {
                out.add(p);
            }
        }
        return out;
    }

    private static Level parseLevel(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "", "WARN", "WARNING" -> Level.WARN;
            case "TRACE", "ALL" -> Level.TRACE;
            case "DEBUG" -> Level.DEBUG;
            case "INFO" -> Level.INFO;
            case "ERROR" -> Level.ERROR;
            case "FATAL" -> Level.FATAL;
            case "OFF", "NONE" -> Level.OFF;
            default -> {
                MfdLogSilencer.LOGGER.warn("unknown level '{}', using WARN", raw);
                yield Level.WARN;
            }
        };
    }

    // ------------------------------------------------------------------ runtime

    /**
     * @return true when the event (logger + level) must be dropped
     */
    public boolean shouldDeny(String loggerName, Level level) {
        if (loggerName == null || level == null) {
            return false;
        }
        if (loggerName.isEmpty()) {
            return false;
        }
        // our own loggers always pass, so status/diagnostics stay visible
        if (loggerName.startsWith(Constants.MODID) || loggerName.startsWith(Constants.MOD_PACKAGE)) {
            return false;
        }
        Rule[] rs = rules;
        if (rs.length == 0) {
            return false;
        }
        int lvl = level.intLevel();
        // 1) anchored (package) match: full name or any parent segment
        int idx = loggerName.length();
        while (idx > 0) {
            String segment = loggerName.substring(0, idx);
            for (Rule r : rs) {
                if (r.exactOnly) {
                    continue;
                }
                for (String a : r.anchors) {
                    if (a.length() != segment.length()) {
                        continue;
                    }
                    if (a.equalsIgnoreCase(segment) && lvl >= keepInt) {
                        return true;
                    }
                }
            }
            idx = loggerName.lastIndexOf('.', idx - 1);
        }
        // 2) exact logger rules
        for (Rule r : rs) {
            if (r.exactOnly) {
                for (String a : r.anchors) {
                    if (a.equals(loggerName) || a.equalsIgnoreCase(loggerName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getKeepInt() {
        return keepInt;
    }

    /**
     * Records which loggers are active (diagnostic for /mfdlogsilencer dump). Only fed
     * for events the runtime would show anyway, so it stays off the hot path.
     */
    public void noteLogger(String loggerName) {
        if (loggerName == null || loggerName.isEmpty() || loggerName.startsWith(Constants.MOD_PACKAGE)) {
            return;
        }
        synchronized (SEEN) {
            if (SEEN.size() < SEEN_LIMIT) {
                SEEN.add(loggerName);
            }
        }
    }

    /** Loggers seen since start (or since the last reset), sorted. */
    public static List<String> seenLoggers() {
        List<String> out;
        synchronized (SEEN) {
            out = new ArrayList<>(SEEN);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public static void resetSeen() {
        synchronized (SEEN) {
            SEEN.clear();
        }
    }

    private static final java.util.Set<String> SEEN = new java.util.LinkedHashSet<>();
    private static final int SEEN_LIMIT = 400;

    public List<String> getNames() {
        List<String> out = new ArrayList<>();
        for (Rule r : rules) {
            out.add(r.name);
        }
        return out;
    }

    public Path getFile() {
        return file;
    }

    public String getRaw() {
        return raw;
    }

    private static void saveDefault(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, Constants.DEFAULT_RULES.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            MfdLogSilencer.LOGGER.error("failed to write default config", e);
        }
    }
}
