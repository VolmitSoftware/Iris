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

/**
 * Runs a lookup whose failure means "this optional API, plugin or server capability is not here" and answers
 * with a fallback instead of throwing.
 * <p>
 * This is the only sanctioned form of a quiet catch in Iris. A probe states the capability it was looking
 * for, and a miss is recorded at {@link LogLevel#DEBUG} with the failure's class and message, so an operator
 * running with debug on can tell a genuinely absent API apart from one that broke while being reached. Every
 * other catch - world lifecycle, world replacement, runtime control, safeguard, service enable and disable,
 * generation - reports through {@link IrisLogging#reportError(String, Throwable)} with the full trace.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public final class CapabilityProbe {
    private CapabilityProbe() {
    }

    /**
     * Resolves {@code probe}, or returns {@code unavailable} when it throws.
     *
     * @param capability  what was being looked for, for the debug record - for example
     *                    {@code "world#getChunkAtAsync"}
     * @param probe       the lookup; any {@link Throwable} it raises means the capability is absent
     * @param unavailable the value that stands for an absent capability
     */
    public static <T> T attempt(String capability, Probe<T> probe, T unavailable) {
        try {
            return probe.resolve();
        } catch (Throwable failure) {
            recordUnavailable(capability, failure);
            return unavailable;
        }
    }

    /**
     * Runs {@code attempt} and reports whether it completed, for a probe whose only result is that it worked.
     */
    public static boolean succeeds(String capability, Attempt attempt) {
        try {
            attempt.run();
            return true;
        } catch (Throwable failure) {
            recordUnavailable(capability, failure);
            return false;
        }
    }

    private static void recordUnavailable(String capability, Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        String name = capability == null || capability.isBlank() ? "unnamed" : capability;
        String message = failure.getMessage();
        IrisLogging.debug("Capability probe \"" + name + "\" is unavailable: "
                + failure.getClass().getName()
                + (message == null ? "" : ": " + message));
    }

    @FunctionalInterface
    public interface Probe<T> {
        T resolve() throws Throwable;
    }

    @FunctionalInterface
    public interface Attempt {
        void run() throws Throwable;
    }
}
