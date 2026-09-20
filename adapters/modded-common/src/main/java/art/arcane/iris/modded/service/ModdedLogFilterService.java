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

package art.arcane.iris.modded.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;

import java.util.Set;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeServerLogMessages;

public final class ModdedLogFilterService implements ModdedService, Filter {
    private static final Set<NativeServerLogMessages.Kind> FILTERS = Set.of(
            NativeServerLogMessages.Kind.IGNORED_HEIGHTMAP,
            NativeServerLogMessages.Kind.RAID_PERSISTENCE,
            NativeServerLogMessages.Kind.DUPLICATE_ENTITY);

    private boolean installed = false;

    @Override
    public void onEnable() {
        if (installed) {
            return;
        }
        ((Logger) LogManager.getRootLogger()).addFilter(this);
        installed = true;
    }

    @Override
    public void onDisable() {
        if (!installed) {
            return;
        }
        ((Logger) LogManager.getRootLogger()).get().removeFilter(this);
        installed = false;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public State getState() {
        return State.STARTED;
    }

    @Override
    public Filter.Result getOnMatch() {
        return Result.NEUTRAL;
    }

    @Override
    public Filter.Result getOnMismatch() {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(LogEvent event) {
        return check(event.getLoggerName(), event.getMessage().getFormattedMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return check(logger.getName(), String.valueOf(msg));
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return check(logger.getName(), msg.getFormattedMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        return check(logger.getName(), message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        return check(logger.getName(), message);
    }

    private Result check(String loggerName, String message) {
        return FILTERS.contains(NativeServerLogMessages.classify(loggerName, message))
                ? Result.DENY : Result.NEUTRAL;
    }
}
