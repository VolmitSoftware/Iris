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

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.io.File;

/**
 * Root platform service provided by each adapter; the single entry point core uses to reach the host platform.
 * <p>
 * Implementations are shared by every Iris thread and must be thread-safe. Accessor methods
 * ({@link #registries()}, {@link #scheduler()}, {@link #structureHooks()}, {@link #biomeWriter()}, the
 * version and path methods) are called from generation threads and must not block on the server thread.
 * The mutating methods ({@link #callEvent(Object)}, {@link #dispatchConsoleCommand(String)},
 * {@link #spawnEntity(NativeWorld, String, double, double, double)}) touch live server state and are
 * expected to be invoked on the thread that owns it - the global/server thread, or the region thread
 * owning the target chunk on regionized platforms. Use {@link #scheduler()} to get there.
 * <p>
 * This interface is internal to Iris. It is not a published integration surface and changes without a
 * deprecation cycle; adapters in this repository are its only supported implementors.
 */
public interface IrisPlatform {
    /**
     * Short adapter identity, for example {@code Bukkit} or the mod loader name. Never null.
     */
    String platformName();

    /**
     * Minecraft version string reported by the host, for example {@code 26.2}. Never null.
     */
    String minecraftVersion();

    /**
     * Registry lookups for blocks, biomes, items and entity types. Never null; may be called off the
     * server thread.
     */
    PlatformRegistries registries();

    /**
     * Task dispatch onto the platform's threading model. Never null.
     */
    PlatformScheduler scheduler();

    /**
     * Structure, structure-set and configured-feature access. Never null.
     */
    PlatformStructureHooks structureHooks();

    /**
     * Biome id resolution used when injecting biomes into the mantle. Never null.
     */
    PlatformBiomeWriter biomeWriter();

    default Class<?> classifyMantleValue(Object value) {
        return value.getClass();
    }

    default boolean supportsMatterWorldIo() {
        return false;
    }

    /**
     * Root folder Iris owns for packs, settings and generated data. Created if missing. Never null.
     */
    File dataFolder();

    /**
     * {@link #dataFolder()} resolved against {@code path} segments, creating the folder and its parents.
     * A null or empty {@code path} returns {@link #dataFolder()}. Never null.
     */
    default File dataFolder(String... path) {
        if (path == null || path.length == 0) {
            return dataFolder();
        }

        File folder = new File(dataFolder(), String.join(File.separator, path));
        folder.mkdirs();
        return folder;
    }

    /**
     * Same resolution as {@link #dataFolder(String...)} without creating anything on disk. The returned
     * {@link File} may not exist. Never null.
     */
    default File dataFolderNoCreate(String... path) {
        if (path == null || path.length == 0) {
            return dataFolder();
        }

        return new File(dataFolder(), String.join(File.separator, path));
    }

    /**
     * A file inside {@link #dataFolder()}, with its parent directories created. The file itself is not
     * created. Never null.
     */
    File dataFile(String... path);

    /**
     * Root under which installed packs live, resolved against optional sub-segments and created if
     * missing. Defaults to {@code dataFolder("packs")}; platforms whose packs live outside the data
     * folder (mod loaders) override this. Never null.
     */
    default File packsFolder(String... sub) {
        if (sub == null || sub.length == 0) {
            return dataFolder("packs");
        }

        String[] path = new String[sub.length + 1];
        path[0] = "packs";
        System.arraycopy(sub, 0, path, 1, sub.length);
        return dataFolder(path);
    }

    /**
     * Same resolution as {@link #packsFolder(String...)} without creating anything on disk. Never null.
     */
    default File packsFolderNoCreate(String... sub) {
        if (sub == null || sub.length == 0) {
            return dataFolderNoCreate("packs");
        }

        String[] path = new String[sub.length + 1];
        path[0] = "packs";
        System.arraycopy(sub, 0, path, 1, sub.length);
        return dataFolderNoCreate(path);
    }

    /**
     * The Iris artifact this runtime was loaded from: the plugin jar on Bukkit, the mod jar on a mod
     * loader. The Bukkit-flavoured name is retained for source compatibility. Never null; adapters that
     * cannot locate the real artifact return a placeholder path inside {@link #dataFolder()}.
     */
    File pluginJar();

    /**
     * Iris's own version as a comparable integer, derived from the artifact version.
     */
    int irisVersionNumber();

    /**
     * The host Minecraft version as a comparable integer, derived from {@link #minecraftVersion()}.
     */
    int minecraftVersionNumber();

    /**
     * Publishes an Iris event on the host's event bus.
     * <p>
     * The parameter is untyped by design: the event object is a platform type that this module cannot
     * name. On Bukkit it must be an {@code org.bukkit.event.Event} and the adapter casts it; platforms
     * without an event bus - every mod loader adapter - ignore the call. Callers therefore must not rely
     * on delivery, and must construct events from the platform module that owns the type. {@code event} must
     * not be null and must be the type the active adapter expects; a mismatch fails inside the adapter.
     * <p>
     * Invoke on the server thread. Bukkit's event bus is not thread-safe.
     */
    void callEvent(Object event);

    /**
     * Runs {@code command} as the server console. Invoke on the server thread.
     */
    void dispatchConsoleCommand(String command);

    /**
     * Spawns a vanilla entity by namespaced key at the given block-space position.
     * <p>
     * Adapters unwrap {@link NativeWorld#nativeHandle()} to reach the host world, so {@code world} must
     * be a {@link NativeWorld} produced by the active adapter. Returns false - never throws - when
     * {@code world} or {@code entityKey} is null, the world belongs to a different adapter, the key does
     * not parse, the entity type is unknown, or the platform refuses the spawn.
     * <p>
     * Invoke on the thread owning the target chunk.
     */
    boolean spawnEntity(NativeWorld world, String entityKey, double x, double y, double z);

    /**
     * Routes a log line to the host logger at {@code level}. Safe from any thread. Prefer
     * {@link IrisLogging}, which tolerates an unbound platform.
     */
    void log(LogLevel level, String message);

    /**
     * Routes a formatted, player-facing message to the console. Safe from any thread.
     */
    void msg(String message);

    /**
     * Hands a throwable to the host's error reporting. Safe from any thread; must not rethrow.
     */
    void reportError(Throwable error);

    /**
     * Contextual variant of {@link #reportError(Throwable)}. The platform owns trace emission so
     * each adapter prints exactly one copy and its own suppression (throttles, debug gates) is
     * honored; the default preserves the historical behavior of reporting plus an unconditional
     * stack trace on stderr.
     */
    default void reportError(String context, Throwable error) {
        log(LogLevel.ERROR, context == null || context.isBlank() ? "Unhandled Iris failure." : context);
        reportError(error);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
