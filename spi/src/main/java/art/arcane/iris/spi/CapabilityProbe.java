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

public final class CapabilityProbe {
    private CapabilityProbe() {
    }

    public static <T> T attempt(String capability, Probe<T> probe, T unavailable) {
        try {
            return probe.resolve();
        } catch (Throwable failure) {
            recordUnavailable(capability, failure);
            return unavailable;
        }
    }

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
