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

import art.arcane.iris.spi.IrisLogging;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Writes IrisApi's pack load log, the module summary and the missing-classes warning, to Iris's log.
 * Passed to the module host rather than installed as a {@link System.LoggerFinder}, which the JDK only
 * looks up on the system class loader, where a plugin's classes are not.
 */
final class IrisApiLogger implements System.Logger {
    private final String name;

    IrisApiLogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(Level level) {
        return level != Level.OFF;
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

    private static void emit(Level level, String message) {
        switch (level) {
            case ERROR -> IrisLogging.error(message);
            case WARNING -> IrisLogging.warn(message);
            case INFO -> IrisLogging.info(message);
            default -> IrisLogging.debug(message);
        }
    }
}
