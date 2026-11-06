package com.volmit.host;

import com.volmit.ModuleLog;
import java.util.Objects;

/** The logs a host hands to modules. */
public final class ModuleLogs {
    private static final ModuleLog DISCARDING = new DiscardingLog();

    private ModuleLogs() {
    }

    /** A module's log writing to {@code logger} with the module's id in front of each message, such as
     * {@code [iris:decorant] Empty Block Data for plains}. {@link ModuleLog#debug} writes at
     * {@link System.Logger.Level#DEBUG} and only when {@code logger} is loggable at that level;
     * {@link ModuleLog#error} passes its cause to the logger, which is where the host reports it. A log keeps its
     * {@link ModuleLog#warnOnce} keys, so make one per module, not one per message.
     */
    public static ModuleLog labelled(String moduleId, System.Logger logger) {
        return new LabelledModuleLog(Objects.requireNonNull(moduleId, "moduleId"), Objects.requireNonNull(logger, "logger"));
    }

    /** A log that writes nothing, for tests and tools that run module code without a host. */
    public static ModuleLog discarding() {
        return DISCARDING;
    }

    private static final class DiscardingLog implements ModuleLog {
        @Override
        public void debug(String message) {
        }

        @Override
        public boolean debugging() {
            return false;
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warnOnce(String key, String message) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }
    }
}
