/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.ModuleLog;
import com.volmit.host.ModuleLogs;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes IrisApi's pack load log, the module summary and the missing-classes warning, to Iris's log.
 * Passed to the module host rather than installed as a {@link System.LoggerFinder}, which the JDK only
 * looks up on the system class loader, where a plugin's classes are not.
 */
final class IrisApiLogger implements System.Logger {
    private static final String HOST_MODULE_LABEL = "iris";
    private static final ModuleLog HOST_LOG = ModuleLogs.labelled(HOST_MODULE_LABEL, new IrisApiLogger(HOST_MODULE_LABEL));

    private final String name;
    private final Set<String> shownMessages;

    IrisApiLogger(String name) {
        this(name, null);
    }

    private IrisApiLogger(String name, Set<String> shownMessages) {
        this.name = name;
        this.shownMessages = shownMessages;
    }

    /**
     * A log that shows each distinct message once and later copies only as debug lines. Iris loads the same pack many
     * times while starting, and its module summary only says something new when the modules change.
     */
    static IrisApiLogger showingEachMessageOnce(String name) {
        return new IrisApiLogger(name, ConcurrentHashMap.newKeySet());
    }

    /** The log of Iris itself, for the world code that is not any module's. */
    static ModuleLog hostLog() {
        return HOST_LOG;
    }

    @Override
    public String getName() {
        return name;
    }

    /** Modules' debug lines follow Iris's debug setting, as Iris's own debug lines do. */
    @Override
    public boolean isLoggable(Level level) {
        if (level == Level.OFF) {
            return false;
        }
        if (level == Level.DEBUG || level == Level.TRACE) {
            return isDebugEnabled();
        }
        return true;
    }

    private static boolean isDebugEnabled() {
        boolean settingsAvailable = IrisSettings.settings != null || IrisPlatforms.isBound();
        return settingsAvailable && IrisSettings.get().getGeneral().isDebug();
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
        if (thrown != null) {
            IrisLogging.reportError(message, thrown);
            return;
        }
        emit(level, message);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... parameters) {
        emit(level, parameters == null || parameters.length == 0 ? format : MessageFormat.format(format, parameters));
    }

    private void emit(Level level, String message) {
        if (isRepeat(message)) {
            IrisLogging.debug(message);
            return;
        }
        switch (level) {
            case ERROR -> IrisLogging.error(message);
            case WARNING -> IrisLogging.warn(message);
            case INFO -> IrisLogging.info(message);
            default -> IrisLogging.debug(message);
        }
    }

    private boolean isRepeat(String message) {
        boolean reachesPlatformLog = IrisPlatforms.isBound();
        return shownMessages != null && reachesPlatformLog && !shownMessages.add(message);
    }
}
