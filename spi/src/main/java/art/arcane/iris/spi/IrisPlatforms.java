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
 * Static holder binding the active platform adapter for the lifetime of the runtime.
 * <p>
 * Exactly one adapter binds itself during startup; core reaches it through {@link #get()} from every thread.
 * The binding is volatile, so reads are safe from any thread and see the bind that happened before them;
 * {@link #bind(IrisPlatform)} and {@link #unbind()} serialize against each other.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public final class IrisPlatforms {
    private static volatile IrisPlatform platform;

    private IrisPlatforms() {
    }

    /**
     * Binds {@code p} as the active platform. Rebinding the same instance is a no-op.
     *
     * @throws IllegalStateException if a different platform is already bound
     */
    public static synchronized void bind(IrisPlatform p) {
        if (platform != null && platform != p) {
            throw new IllegalStateException("Iris platform is already bound to a different instance");
        }
        platform = p;
    }

    /**
     * Clears the binding. Safe to call when nothing is bound.
     * <p>
     * Also forgets the keys {@link IrisLogging#warnOnce(String, String, Object...)} has claimed. Unbinding is
     * what a reload does, and an operator who has just fixed a pack has to see whether the condition that was
     * stated once is still there.
     */
    public static synchronized void unbind() {
        platform = null;
        IrisLogging.resetOnceKeys();
    }

    /**
     * The bound platform. Never returns null.
     *
     * @throws IllegalStateException if no platform is bound, which means Iris was reached before adapter
     *                               startup or after shutdown
     */
    public static IrisPlatform get() {
        IrisPlatform bound = platform;
        if (bound == null) {
            throw new IllegalStateException("No Iris platform is bound");
        }
        return bound;
    }

    /**
     * Whether a platform is bound. Use before {@link #get()} on paths that must tolerate a bare JVM, such as
     * logging during startup or in tests.
     */
    public static boolean isBound() {
        return platform != null;
    }

    public static IrisPlatform getOrNull() {
        return platform;
    }
}
