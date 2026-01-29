package com.example.aibuild.util;

import java.util.logging.Logger;

/**
 * Utility for timing operations and logging performance metrics
 * Only logs when debug mode is enabled
 */
public class DebugTimer {
    private final Logger logger;
    private final boolean enabled;
    private final String operation;
    private final long startTime;

    private DebugTimer(Logger logger, boolean enabled, String operation) {
        this.logger = logger;
        this.enabled = enabled;
        this.operation = operation;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Start timing an operation
     */
    public static DebugTimer start(Logger logger, boolean debugEnabled, String operation) {
        return new DebugTimer(logger, debugEnabled, operation);
    }

    /**
     * Stop timing and log the duration if debug is enabled
     */
    public void stop() {
        if (enabled) {
            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("[DEBUG] %s took %dms", operation, duration));
        }
    }

    /**
     * Stop timing and log with additional info
     */
    public void stop(String additionalInfo) {
        if (enabled) {
            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("[DEBUG] %s took %dms - %s", operation, duration, additionalInfo));
        }
    }

    /**
     * Get elapsed time without stopping the timer
     */
    public long elapsed() {
        return System.currentTimeMillis() - startTime;
    }
}
