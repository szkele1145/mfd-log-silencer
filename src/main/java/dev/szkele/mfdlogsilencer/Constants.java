package dev.szkele.mfdlogsilencer;

/**
 * Shared constants for MFD Log Silencer.
 */
public final class Constants {
    public static final String MODID = "mfd_log_silencer";
    /** Our own Java package: never silenced, so status output stays visible. */
    public static final String MOD_PACKAGE = "dev.szkele.mfdlogsilencer";
    /** Legacy default target of 1.x configurations. */
    public static final String DEFAULT_TARGET_LOGGER = "fixed_tweaked_controller";
    /** Prefix used for all console feedback lines. */
    public static final String CONSOLE_PREFIX = "[MFD-LogSilencer] ";
    /** Max entries kept in the recent-blocked ring buffer for the status command. */
    public static final int RECENT_BUFFER_SIZE = 10;

    /**
     * Built-in rules, written to config/mfd-log-silencer.properties on first run.
     *
     * Why these: fixed Tweaked Controller logs through SLF4J and names its logger
     * "fixed_tweaked_controller" (mod id style), while its debug mixins live in the
     * class packages "com.pb7ap8lthh.fmdmod.*" and "dev.ryanhcode.*". 1.x only knew
     * about the mod id, which is why "nothing happened" for class-name loggers.
     * MFD_DISABLE / MFD_DISABLE_CLASSES are read from the JVM system properties, so
     * a server pack can switch rules without editing this file.
     */
    public static final String DEFAULT_RULES = """
            # MFD Log Silencer - which loggers get hidden, and at which level
            #
            # DISABLE = a logger name or a Java package/class. A package also covers
            #           everything below it. Repeat the line or comma separate entries.
            # KEEP    = levels that always pass through:
            #           warn (default) / error / debug / info / trace
            #
            # fixed Tweaked Controller writes its spam through SLF4J under the mod id
            # "fixed_tweaked_controller", and its diagnostic mixins live in the classes
            # com.pb7ap8lthh.fmdmod.* / dev.ryanhcode.* - both are listed below.
            # Add any other spammy mod the same way.
            #
            # Environment override (no file edit needed at startup):
            #   -DMFD_DISABLE=mod_a,mod_b        replaces DISABLE
            #   -DMFD_DISABLE_CLASSES=pkg1,pkg2  appended as class/package rules

            DISABLE=fixed_tweaked_controller
            DISABLE=com.pb7ap8lthh
            DISABLE=dev.ryanhcode.sable.Sable

            KEEP=warn
            """;

    private Constants() {
    }
}
