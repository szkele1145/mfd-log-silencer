package dev.szkele.mfdlogsilencer;

/**
 * Shared constants for MFD Log Silencer.
 */
public final class Constants {
    public static final String MODID = "mfd_log_silencer";
    public static final String DEFAULT_TARGET_LOGGER = "fixed_tweaked_controller";
    /** Prefix used for all console feedback lines. */
    public static final String CONSOLE_PREFIX = "[MFD-LogSilencer] ";
    /** Max entries kept in the recent-blocked ring buffer for the status command. */
    public static final int RECENT_BUFFER_SIZE = 10;

    private Constants() {
    }
}
