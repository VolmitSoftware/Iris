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

package art.arcane.iris.modded;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ModdedPlatform implements IrisPlatform {
    private static final int ERROR_SIGNATURE_BURST = 5;
    private static final int ERROR_SIGNATURE_CAPACITY = 256;
    private static final long ERROR_SUMMARY_INTERVAL_MILLIS = 300_000L;
    private static final ConcurrentHashMap<String, ErrorThrottle> ERROR_THROTTLES = new ConcurrentHashMap<>();

    private static volatile Consumer<Throwable> ERROR_SINK = null;

    private final ModdedLoader loader;
    private final ModdedRegistries registries;
    private final ModdedScheduler scheduler;
    private final ModdedStructureHooks structureHooks;
    private final ModdedBiomeWriter biomeWriter;

    public ModdedPlatform(ModdedLoader loader) {
        this.loader = loader;
        this.registries = new ModdedRegistries(ModdedEngineBootstrap::currentServer);
        this.scheduler = new ModdedScheduler();
        this.structureHooks = new ModdedStructureHooks(ModdedEngineBootstrap::currentServer);
        this.biomeWriter = new ModdedBiomeWriter(ModdedEngineBootstrap::currentServer);
    }

    public static void errorSink(Consumer<Throwable> sink) {
        ERROR_SINK = sink;
    }

    public MinecraftServer server() {
        return ModdedEngineBootstrap.currentServer();
    }

    public ModdedScheduler moddedScheduler() {
        return scheduler;
    }

    @Override
    public String platformName() {
        return loader.platformName();
    }

    @Override
    public String minecraftVersion() {
        return loader.minecraftVersion();
    }

    @Override
    public PlatformRegistries registries() {
        return registries;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public PlatformStructureHooks structureHooks() {
        return structureHooks;
    }

    @Override
    public PlatformBiomeWriter biomeWriter() {
        return biomeWriter;
    }

    @Override
    public File dataFolder() {
        File folder = loader.configDir().resolve("iris").toFile();
        folder.mkdirs();
        return folder;
    }

    /**
     * Modded packs live under config/irisworldgen/packs, not the config/iris data folder. The
     * packsFolder overrides are the source of truth; the dataFolder/dataFile overrides below
     * re-root any path whose FIRST segment is exactly "packs" so no call site can regress onto
     * the empty config/iris/packs directory. Settings, worlds.json, parity/, cache/ and every
     * other name stay under config/iris.
     */
    @Override
    public File packsFolder(String... sub) {
        File folder = packsFolderNoCreate(sub);
        folder.mkdirs();
        return folder;
    }

    @Override
    public File packsFolderNoCreate(String... sub) {
        File root = loader.configDir().resolve("irisworldgen").resolve("packs").toFile();
        if (sub == null || sub.length == 0) {
            return root;
        }
        return new File(root, String.join(File.separator, sub));
    }

    @Override
    public File dataFolder(String... path) {
        if (isPacksPath(path)) {
            return packsFolder(stripPacksSegment(path));
        }
        return IrisPlatform.super.dataFolder(path);
    }

    @Override
    public File dataFolderNoCreate(String... path) {
        if (isPacksPath(path)) {
            return packsFolderNoCreate(stripPacksSegment(path));
        }
        return IrisPlatform.super.dataFolderNoCreate(path);
    }

    @Override
    public File dataFile(String... path) {
        if (isPacksPath(path)) {
            File file = packsFolderNoCreate(stripPacksSegment(path));
            file.getParentFile().mkdirs();
            return file;
        }
        File file = new File(dataFolder(), String.join(File.separator, path));
        file.getParentFile().mkdirs();
        return file;
    }

    private static boolean isPacksPath(String... path) {
        // Exact-segment match only: "packbenchmarks" and "packsx" must stay under config/iris.
        return path != null && path.length > 0 && "packs".equals(path[0]);
    }

    private static String[] stripPacksSegment(String... path) {
        String[] sub = new String[path.length - 1];
        System.arraycopy(path, 1, sub, 0, sub.length);
        return sub;
    }

    @Override
    public File pluginJar() {
        File jar = loader.modJar();
        return jar != null ? jar : new File(dataFolder(), "iris-" + loader.platformName() + ".jar");
    }

    @Override
    public int irisVersionNumber() {
        return parseVersion(loader.modVersion());
    }

    @Override
    public int minecraftVersionNumber() {
        int fromLoader = parseVersion(loader.minecraftVersion());
        return fromLoader > 0 ? fromLoader : parseVersion(BuildConstants.MINECRAFT_VERSION);
    }

    @Override
    public void callEvent(Object event) {
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        ModdedServerCommands.dispatch(ModdedEngineBootstrap.currentServer(), command);
    }

    @Override
    public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
        if (world == null || entityKey == null || !(world.nativeHandle() instanceof ServerLevel level)) {
            return false;
        }
        PlatformEntityType resolved = registries.entity(entityKey);
        if (resolved == null) {
            return false;
        }
        EntityType<?> type = (EntityType<?>) resolved.nativeHandle();
        BlockPos pos = BlockPos.containing(x, y, z);
        Entity entity = ModdedEntitySpawner.spawnNative(type, level, pos, EntitySpawnReason.COMMAND);
        return entity != null;
    }

    @Override
    public void log(LogLevel level, String message) {
        ModdedIrisLog.log(level, message);
    }

    @Override
    public void msg(String message) {
        ModdedIrisLog.info(message);
    }

    /**
     * Throttled per exception signature. A single broken pack rule can fail on every generated chunk, so each
     * distinct signature reports its first few occurrences and then only a periodic suppressed-count summary.
     */
    @Override
    public void reportError(Throwable error) {
        reportThrottled("Iris reported error", error);
    }

    /**
     * Contextual reports run the same throttle and emit exactly one trace through the modded
     * log; the SPI default's raw stderr copy would bypass the per-signature suppression.
     */
    @Override
    public void reportError(String context, Throwable error) {
        reportThrottled(context == null || context.isBlank() ? "Iris reported error" : context, error);
    }

    private void reportThrottled(String message, Throwable error) {
        if (error == null) {
            return;
        }
        if (!allowErrorReport(error)) {
            return;
        }
        Consumer<Throwable> sink = ERROR_SINK;
        if (sink != null) {
            sink.accept(error);
            return;
        }
        ModdedIrisLog.error(message, error);
    }

    static void resetErrorThrottles() {
        ERROR_THROTTLES.clear();
    }

    private static boolean allowErrorReport(Throwable error) {
        String signature = errorSignature(error);
        ErrorThrottle throttle = ERROR_THROTTLES.get(signature);
        if (throttle == null) {
            if (ERROR_THROTTLES.size() >= ERROR_SIGNATURE_CAPACITY) {
                evictLeastRecentlySeen();
            }
            throttle = ERROR_THROTTLES.computeIfAbsent(signature, ignored -> new ErrorThrottle());
        }
        return throttle.allow(signature);
    }

    /**
     * At the cap a new signature used to report through unthrottled for the rest of the uptime, which is the
     * failure mode the cap exists to prevent: one broken pack rule firing on every chunk with a per-chunk line
     * number produces new signatures forever. Evict the entry nothing has hit for the longest instead. O(n) on
     * an error path with n=256, and an evicted signature simply earns a fresh burst if it comes back.
     */
    private static void evictLeastRecentlySeen() {
        String oldest = null;
        long oldestSeenAt = Long.MAX_VALUE;
        for (Map.Entry<String, ErrorThrottle> entry : ERROR_THROTTLES.entrySet()) {
            long seenAt = entry.getValue().lastSeenAt();
            if (seenAt < oldestSeenAt) {
                oldestSeenAt = seenAt;
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            ERROR_THROTTLES.remove(oldest);
        }
    }

    static String errorSignature(Throwable error) {
        StackTraceElement[] trace = error.getStackTrace();
        String frame = trace.length == 0
                ? "<no-frame>"
                : trace[0].getClassName() + '.' + trace[0].getMethodName() + ':' + trace[0].getLineNumber();
        return error.getClass().getName() + '@' + frame;
    }

    private static final class ErrorThrottle {
        private final AtomicLong reported = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();
        private final AtomicLong nextSummaryAt = new AtomicLong();
        private final AtomicLong lastSeenAt = new AtomicLong(System.currentTimeMillis());

        private long lastSeenAt() {
            return lastSeenAt.get();
        }

        private boolean allow(String signature) {
            long now = System.currentTimeMillis();
            lastSeenAt.set(now);
            if (reported.get() < ERROR_SIGNATURE_BURST) {
                reported.incrementAndGet();
                return true;
            }
            long total = suppressed.incrementAndGet();
            long due = nextSummaryAt.get();
            if (now >= due && nextSummaryAt.compareAndSet(due, now + ERROR_SUMMARY_INTERVAL_MILLIS)) {
                ModdedIrisLog.warn("Iris suppressed " + total + " repeats of " + signature);
            }
            return false;
        }
    }

    private static int parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        int hyphen = raw.indexOf('-');
        String head = hyphen >= 0 ? raw.substring(0, hyphen) : raw;
        StringBuilder digits = new StringBuilder(head.length());
        for (int i = 0; i < head.length(); i++) {
            char ch = head.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits.append(ch);
            } else if (ch != '.') {
                break;
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            long value = Long.parseLong(digits.toString());
            return value > Integer.MAX_VALUE ? -1 : (int) value;
        } catch (NumberFormatException error) {
            return -1;
        }
    }
}
