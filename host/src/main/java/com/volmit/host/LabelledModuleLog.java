package com.volmit.host;

import com.volmit.ModuleLog;
import java.lang.System.Logger.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A module's log on the host's {@link System.Logger}; see {@link ModuleLogs#labelled}. */
final class LabelledModuleLog implements ModuleLog {
    /** What keeps a key space nobody bounded, such as block names, from growing without end. */
    private static final int MAX_ONCE_KEYS = 512;

    private final String label;
    private final System.Logger logger;
    private final Set<String> claimedKeys = ConcurrentHashMap.newKeySet();

    LabelledModuleLog(String moduleId, System.Logger logger) {
        this.label = "[" + moduleId + "] ";
        this.logger = logger;
    }

    @Override
    public void debug(String message) {
        if (!logger.isLoggable(Level.DEBUG)) {
            return;
        }
        logger.log(Level.DEBUG, label + message);
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, label + message);
    }

    @Override
    public void warn(String message) {
        logger.log(Level.WARNING, label + message);
    }

    @Override
    public void warnOnce(String key, String message) {
        if (!claim(key)) {
            debug(message);
            return;
        }
        warn(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        logger.log(Level.ERROR, label + message, cause);
    }

    private boolean claim(String key) {
        if (claimedKeys.size() >= MAX_ONCE_KEYS) {
            return false;
        }
        return claimedKeys.add(String.valueOf(key));
    }
}
