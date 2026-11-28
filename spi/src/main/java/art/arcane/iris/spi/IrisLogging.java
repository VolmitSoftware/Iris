/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.spi;

import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Logging front door for core: routes through the bound {@link IrisPlatform} when there is one and falls back
 * to {@code System.out}/{@code System.err} when there is not, so code that runs before adapter startup, in
 * tests, or in the standalone probe still logs.
 * <p>
 * Every method is safe from any thread and swallows its own failures - logging never throws. Formatting is
 * lenient: a malformed format string is emitted verbatim rather than raising
 * {@link java.util.IllegalFormatException}, and a null message renders as {@code "null"}.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public final class IrisLogging {
    static final int MAX_ONCE_KEYS = 512;
    private static final Map<String, Boolean> ONCE_KEYS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_ONCE_KEYS;
                }
            });
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("(?i)</?(?:reset|bold|b|italic|i|underlined?|u|strikethrough|st|obfuscated?|obf|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|gradient|font|hover|click|rainbow)(?::[^>\\n]{0,96})?>|<#[0-9a-f]{6}>");

    private IrisLogging() {
    }

    /**
     * Logs at {@link LogLevel#INFO}, applying {@link #format(String, Object...)} to the arguments.
     */
    public static void info(String format, Object... args) {
        emit(LogLevel.INFO, format(format, args));
    }

    /**
     * Logs a literal message at {@link LogLevel#DEBUG}. Takes no format arguments, so a message containing
     * {@code %} needs no escaping.
     */
    public static void debug(String message) {
        emit(LogLevel.DEBUG, message);
    }

    public static void debug(String format, Object... args) {
        emit(LogLevel.DEBUG, format(format, args));
    }

    /**
     * Logs at {@link LogLevel#NOTICE}, applying {@link #format(String, Object...)} to the arguments. For the
     * few lifecycle milestones per boot that have to survive into the server's own log file.
     */
    public static void notice(String format, Object... args) {
        emit(LogLevel.NOTICE, format(format, args));
    }

    /**
     * Logs at {@link LogLevel#WARN}, applying {@link #format(String, Object...)} to the arguments.
     */
    public static void warn(String format, Object... args) {
        emit(LogLevel.WARN, format(format, args));
    }

    /**
     * Logs at {@link LogLevel#ERROR}, applying {@link #format(String, Object...)} to the arguments.
     */
    public static void error(String format, Object... args) {
        emit(LogLevel.ERROR, format(format, args));
    }

    /**
     * Logs at {@link LogLevel#WARN} the first time {@code key} is seen and at {@link LogLevel#DEBUG} every
     * time after, for a condition that is worth stating once but is reached per block, per sample or per
     * chunk. Returns true when the warning was the one that got stated, so a caller can attach a stack trace
     * or other one-off detail to it. At most {@link #MAX_ONCE_KEYS} keys are held; past that the least recently
     * seen key is dropped and warns again the next time it comes up.
     *
     * @see #resetOnceKeys()
     */
    public static boolean warnOnce(String key, String format, Object... args) {
        return once(LogLevel.WARN, key, format, args);
    }

    /**
     * {@link #warnOnce(String, String, Object...)} for a message that is costly to build: {@code message} is
     * only called when the warning is stated, and a repeat logs nothing.
     */
    public static boolean warnOnce(String key, Supplier<String> message) {
        if (isRepeat(key)) {
            return false;
        }

        emit(LogLevel.WARN, message == null ? "null" : String.valueOf(message.get()));
        return true;
    }

    /**
     * {@link #warnOnce(String, String, Object...)} at {@link LogLevel#ERROR}.
     */
    public static boolean errorOnce(String key, String format, Object... args) {
        return once(LogLevel.ERROR, key, format, args);
    }

    /**
     * Emits a player-facing message to the console, preserving colour markup when a platform is bound and
     * stripping it via {@link #clean(String)} when one is not.
     */
    public static void msg(String message) {
        if (IrisPlatforms.isBound()) {
            IrisPlatforms.get().msg(message);
            return;
        }

        System.out.println("[Iris] " + clean(message));
    }

    /**
     * Logs {@code context} at {@link LogLevel#ERROR} and then reports {@code error}. A null or blank
     * {@code context} gets a generic description; a null {@code error} is replaced with a synthetic
     * {@link IllegalStateException} so the report is never empty.
     */
    public static void reportError(String context, Throwable error) {
        Throwable cause = error == null ? new IllegalStateException("Unknown Iris failure") : error;
        String message = context == null || context.isBlank() ? "Unhandled Iris failure." : context;

        if (IrisPlatforms.isBound()) {
            try {
                IrisPlatforms.get().reportError(message, cause);
            } catch (Throwable inner) {
                System.err.println("[Iris/ERROR] " + clean(message));
                inner.printStackTrace(System.err);
                cause.printStackTrace(System.err);
            }
            return;
        }

        System.err.println("[Iris/ERROR] " + clean(message));
        cause.printStackTrace(System.err);
    }

    /**
     * Hands {@code error} to the platform's error reporting, or prints it to {@code System.err} when no
     * platform is bound. A null {@code error} is ignored.
     */
    public static void reportError(Throwable error) {
        if (IrisPlatforms.isBound()) {
            IrisPlatforms.get().reportError(error);
            return;
        }

        if (error != null) {
            System.err.println("[Iris/ERROR] " + error.getClass().getName() + (error.getMessage() == null ? "" : ": " + error.getMessage()));
            error.printStackTrace(System.err);
        }
    }

    /**
     * {@link String#format(String, Object...)} that cannot throw: a null format renders as {@code "null"},
     * no arguments returns {@code format} unchanged, and a mismatched format string is returned verbatim.
     * Never returns null.
     */
    public static String format(String format, Object... args) {
        if (format == null) {
            return "null";
        }

        if (args == null || args.length == 0) {
            return format;
        }

        try {
            return String.format(format, args);
        } catch (IllegalFormatException ignored) {
            return format;
        }
    }

    /**
     * Strips legacy section-sign colour codes and MiniMessage tags, for sinks that render neither. A null
     * message renders as {@code "null"}. Never returns null.
     */
    public static String clean(String message) {
        if (message == null) {
            return "null";
        }

        String legacyStripped = LEGACY_COLOR.matcher(message).replaceAll("");
        return MINI_MESSAGE_TAG.matcher(legacyStripped).replaceAll("");
    }

    /**
     * Forgets every key {@link #warnOnce(String, String, Object...)} and {@link #errorOnce} have claimed, so
     * the next occurrence is stated again. Called when the platform unbinds: an operator who fixes a pack and
     * reloads has to be able to see whether the fix took.
     */
    static void resetOnceKeys() {
        ONCE_KEYS.clear();
    }

    /**
     * Forgets only the once-keys that start with {@code prefix}, so one scope (a pack that was just
     * hotloaded) can warn again while every other scope stays claimed. A null or empty prefix is ignored
     * rather than clearing everything; {@link #resetOnceKeys()} is the global reset.
     */
    public static void forgetOnceKeys(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        synchronized (ONCE_KEYS) {
            ONCE_KEYS.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    private static boolean once(LogLevel level, String key, String format, Object... args) {
        String message = format(format, args);
        if (isRepeat(key)) {
            emit(LogLevel.DEBUG, message);
            return false;
        }

        emit(level, message);
        return true;
    }

    /**
     * The cap is what keeps a key space nobody bounded - a block name, a biome name, a mod id - from becoming
     * a leak. Eviction is least recently seen, so a condition that keeps recurring stays claimed while one
     * that stopped makes room for new ones.
     */
    private static boolean isRepeat(String key) {
        return ONCE_KEYS.put(key == null ? "null" : key, Boolean.TRUE) != null;
    }

    /**
     * Without a bound platform the debug setting cannot be read, so debug lines are dropped as if it were off:
     * the Paper bootstrap runs pack loading before the plugin binds, and would otherwise print every loader
     * line to the console.
     */
    private static void emit(LogLevel level, String message) {
        LogLevel target = level == null ? LogLevel.INFO : level;
        if (IrisPlatforms.isBound()) {
            IrisPlatforms.get().log(target, message);
            return;
        }

        if (target == LogLevel.DEBUG) {
            return;
        }

        String output = clean(message);
        if (target == LogLevel.WARN || target == LogLevel.ERROR) {
            System.err.println("[Iris/" + target + "] " + output);
            return;
        }

        System.out.println("[Iris/" + target + "] " + output);
    }
}
