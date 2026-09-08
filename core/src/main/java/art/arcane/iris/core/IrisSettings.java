/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core;

import com.google.gson.Gson;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONException;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.util.common.misc.getHardware;
import art.arcane.iris.util.common.plugin.VolmitSender;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;

@Data
public class IrisSettings {
    private static final Object SETTINGS_LOCK = new Object();
    private static final IrisSettings BOOTSTRAP_DEFAULTS = new IrisSettings();
    public static volatile IrisSettings settings;
    private IrisSettingsGeneral general = new IrisSettingsGeneral();
    private IrisSettingsWorld world = new IrisSettingsWorld();
    private IrisSettingsGUI gui = new IrisSettingsGUI();
    private IrisSettingsAutoconfiguration autoConfiguration = new IrisSettingsAutoconfiguration();
    private IrisSettingsGenerator generator = new IrisSettingsGenerator();
    private transient IrisSettingsConcurrency concurrency = new IrisSettingsConcurrency();
    private IrisSettingsStudio studio = new IrisSettingsStudio();
    private IrisSettingsPerformance performance = new IrisSettingsPerformance();
    private IrisSettingsPregen pregen = new IrisSettingsPregen();
    private IrisSettingsTreeFeller treeFeller = new IrisSettingsTreeFeller();

    public static int getThreadCount(int c) {
        return Math.max(switch (c) {
            case -1, -2, -4 -> Runtime.getRuntime().availableProcessors() / -c;
            default -> Math.max(c, 2);
        }, 1);
    }

    public static IrisSettings get() {
        IrisSettings current = settings;

        if (current != null) {
            return current;
        }

        if (Thread.holdsLock(SETTINGS_LOCK)) {
            // read() logs and does IO, and the logging path calls back into get().
            // Serve defaults instead of recursing into another disk read.
            return BOOTSTRAP_DEFAULTS;
        }

        synchronized (SETTINGS_LOCK) {
            current = settings;

            if (current != null) {
                return current;
            }

            current = read();
            settings = current;
            return current;
        }
    }

    private static IrisSettings read() {
        IrisSettings loaded = new IrisSettings();
        File s = IrisPlatforms.get().dataFile("iris.json");

        if (!s.exists()) {
            try {
                IO.writeAll(s, new JSONObject(new Gson().toJson(loaded)).toString(4));
            } catch (JSONException | IOException e) {
                IrisLogging.reportError(e);
            }

            return loaded;
        }

        try {
            String ss = IO.readAll(s);
            IrisSettings parsed = new Gson().fromJson(ss, IrisSettings.class);

            if (parsed != null) {
                loaded = parsed;
            }

            try {
                IO.writeAll(s, new JSONObject(new Gson().toJson(loaded)).toString(4));
            } catch (IOException e) {
                IrisLogging.reportError("Could not rewrite " + s.getAbsolutePath()
                        + "; settings changes made in game will not survive a restart.", e);
            }
        } catch (Throwable ee) {
            // IrisLogging.reportError(ee); causes a self-reference & stackoverflow
            IrisLogging.error("Configuration Error in iris.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
        }

        return loaded;
    }

    public static void invalidate() {
        synchronized (SETTINGS_LOCK) {
            settings = null;
        }
    }

    public static IrisSettings installHotloadSnapshot(String rawJson) {
        IrisSettings parsed = parseHotloadSnapshot(rawJson);
        synchronized (SETTINGS_LOCK) {
            settings = parsed;
        }
        return parsed;
    }

    public static boolean applyHotloadSnapshot(String rawJson, Predicate<IrisSettings> candidateActivation) {
        Objects.requireNonNull(candidateActivation, "candidateActivation");
        IrisSettings parsed = parseHotloadSnapshot(rawJson);
        if (!candidateActivation.test(parsed)) {
            return false;
        }
        synchronized (SETTINGS_LOCK) {
            settings = parsed;
        }
        return true;
    }

    public static IrisSettings parseHotloadSnapshot(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Iris settings snapshot is empty");
        }

        IrisSettings parsed;
        try {
            parsed = new Gson().fromJson(rawJson, IrisSettings.class);
            if (parsed == null) {
                throw new IllegalArgumentException("Iris settings snapshot did not contain an object");
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Iris settings snapshot is invalid", failure);
        }

        parsed.fillMissingSections();
        return parsed;
    }

    public void forceSave() {
        File s = IrisPlatforms.get().dataFile("iris.json");

        try {
            IO.writeAll(s, new JSONObject(new Gson().toJson(this)).toString(4));
        } catch (JSONException | IOException e) {
            IrisLogging.reportError(e);
        }
    }

    private void fillMissingSections() {
        general = general == null ? new IrisSettingsGeneral() : general;
        world = world == null ? new IrisSettingsWorld() : world;
        gui = gui == null ? new IrisSettingsGUI() : gui;
        autoConfiguration = autoConfiguration == null ? new IrisSettingsAutoconfiguration() : autoConfiguration;
        generator = generator == null ? new IrisSettingsGenerator() : generator;
        concurrency = concurrency == null ? new IrisSettingsConcurrency() : concurrency;
        studio = studio == null ? new IrisSettingsStudio() : studio;
        performance = performance == null ? new IrisSettingsPerformance() : performance;
        pregen = pregen == null ? new IrisSettingsPregen() : pregen;
        treeFeller = treeFeller == null ? new IrisSettingsTreeFeller() : treeFeller;
    }

    @Data
    public static class IrisSettingsAutoconfiguration {
        public boolean configureSpigotTimeoutTime = true;
        public boolean configurePaperWatchdogDelay = true;
    }

    @Data
    public static class IrisSettingsWorld {
        public boolean postLoadBlockUpdates = true;
        public boolean forcePersistEntities = true;
        public boolean ambientEntitySpawningSystem = true;
        public long asyncTickIntervalMS = 700;
        public double targetSpawnEntitiesPerChunk = 0.95;
        public boolean markerEntitySpawningSystem = true;
        public boolean effectSystem = true;
        public boolean globalPregenCache = false;
    }

    @Data
    public static class IrisSettingsConcurrency {
        public int getParallelism() {
            return Math.max(2, Runtime.getRuntime().availableProcessors());
        }

        public int getIoParallelism() {
            return Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        }

        public int getWorldGenThreads() {
            return Math.max(2, Runtime.getRuntime().availableProcessors());
        }
    }

    @Data
    public static class IrisSettingsPregen {
        private static final int REFERENCE_WORLD_HEIGHT = 384;
        private static final int MIN_RESIDENT_TECTONIC_PLATES = 16;
        private static final double MANTLE_HEAP_FRACTION = 0.6D;
        private static final int REFERENCE_PLATE_MEGABYTES = 48;
        public IrisRuntimeSchedulerMode runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;
        public IrisPaperLikeBackendMode paperLikeBackendMode = IrisPaperLikeBackendMode.AUTO;
        public int chunkLoadTimeoutSeconds = 15;
        public int timeoutWarnIntervalMs = 500;
        public int saveIntervalMs = 30_000;
        public int maxResidentTectonicPlates = 96;
        public int mantleBackpressureWaitMs = 25;
        public int mantleBackpressureTimeoutMs = 60_000;
        public int moddedPregenInFlight = 0;

        public int getChunkLoadTimeoutSeconds() {
            return Math.max(5, Math.min(chunkLoadTimeoutSeconds, 120));
        }

        public int getModdedPregenInFlight() {
            if (moddedPregenInFlight > 0) {
                return Math.min(512, moddedPregenInFlight);
            }

            int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
            return Math.max(16, Math.min(48, cpu * 2));
        }

        public int getMaxResidentTectonicPlates() {
            return Math.max(16, maxResidentTectonicPlates);
        }

        public int getEffectiveResidentTectonicPlates(int worldHeight) {
            int baseCap = getMaxResidentTectonicPlates();
            int normalizedHeight = Math.max(1, worldHeight);
            int heightScaledCap = (int) Math.round((double) baseCap * REFERENCE_WORLD_HEIGHT / (double) normalizedHeight);
            long maxHeapMegabytes = getHardware.getProcessMemory();
            double plateMegabytes = (double) REFERENCE_PLATE_MEGABYTES * (double) normalizedHeight / (double) REFERENCE_WORLD_HEIGHT;
            int byteBudgetCap = (int) Math.floor(MANTLE_HEAP_FRACTION * (double) maxHeapMegabytes / plateMegabytes);
            int effective = Math.min(heightScaledCap, byteBudgetCap);
            return Math.max(MIN_RESIDENT_TECTONIC_PLATES, Math.min(baseCap, effective));
        }

        public int getMantleBackpressureWaitMs() {
            return Math.max(5, Math.min(mantleBackpressureWaitMs, 1_000));
        }

        public int getMantleBackpressureTimeoutMs() {
            return Math.max(5_000, Math.min(mantleBackpressureTimeoutMs, 600_000));
        }

        public int getTimeoutWarnIntervalMs() {
            return Math.max(timeoutWarnIntervalMs, 250);
        }

        public IrisPaperLikeBackendMode getPaperLikeBackendMode() {
            if (paperLikeBackendMode == null) {
                return IrisPaperLikeBackendMode.AUTO;
            }

            return paperLikeBackendMode;
        }

        public int getSaveIntervalMs() {
            return Math.max(5_000, Math.min(saveIntervalMs, 900_000));
        }
    }

    @Data
    public static class IrisSettingsPerformance {
        private IrisSettingsEngineSVC engineSVC = new IrisSettingsEngineSVC();
        public boolean trimMantleInStudio = false; 
        public int mantleKeepAlive = 30;
        public int noiseCacheSize = 1_024;
        public int resourceLoaderCacheSize = 1_024;
        public int objectLoaderCacheSize = 4_096;
        public int mantleCleanupDelay = 200;
        public boolean simdKernels = true;
    }

    @Data
    public static class IrisSettingsGeneral {
        public String language = "en_US";
        public boolean metrics = true;
        public boolean commandSounds = true;
        public boolean debug = false;
        public boolean dumpMantleOnError = false;
        public boolean disableNMS = false;
        public boolean splashLogoStartup = true;
        public boolean useConsoleCustomColors = true;
        public boolean useCustomColorsIngame = true;
        /**
         * Boss bars for jobs, Studio opens, chunk jobs, and pack downloads. Ordinary
         * world creation uses only its action-bar lifecycle meter; creation-time
         * pregeneration retains its dedicated long-running boss bar.
         */
        public boolean progressBossBar = true;
        public boolean adjustVanillaHeight = false;
        public boolean autoIngestDatapacks = true;
        /**
         * Converting every registered datapack structure into editable Iris resources writes
         * thousands of objects/pools/pieces into the pack folder. Native generation and
         * nativeStructures placements never need those copies, so this stays opt-in; run
         * /iris structure import &lt;dimension&gt; when you actually want editable copies.
         */
        public boolean autoImportDatapackStructures = false;
        /** Unresolved pack content keys and bad block-state properties become blocking pack errors. -Diris.strictContent overrides. */
        public boolean strictContentKeys = false;
        public int spinh = -20;
        public int spins = 7;
        public int spinb = 8;


        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean canUseCustomColors(VolmitSender volmitSender) {
            return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
        }
    }

    @Data
    public static class IrisSettingsGUI {
        public boolean useServerLaunchedGuis = true;
        public boolean maximumPregenGuiFPS = false;
        public boolean colorMode = true;
    }

    @Data
    public static class IrisSettingsGenerator {
        public String defaultWorldType = "overworld";
        public boolean preventLeafDecay = true;
        public int generationTransitionWidthBlocks = 256;

        public int getGenerationTransitionWidthBlocks() {
            return Math.max(16, Math.min(generationTransitionWidthBlocks, 8_192));
        }
    }

    @Data
    public static class IrisSettingsTreeFeller {
        public boolean enabled = false;
        public int durabilityPreservationChance = 0;

        public int getDurabilityPreservationChance() {
            return Math.max(0, Math.min(durabilityPreservationChance, 100));
        }
    }

    @Data
    public static class IrisSettingsStudio {
        public boolean openVSCode = true;
        public boolean disableTimeAndWeather = true;
        public boolean entitySpawning = true;
        public boolean autoStartDefaultStudio = false;
    }

    @Data
    public static class IrisSettingsEngineSVC {
        public boolean useVirtualThreads = true;
        public boolean forceMulticoreWrite = false;
        public int priority = Thread.NORM_PRIORITY;
        public int parallelism = -1;

        public int getPriority() {
            return Math.max(Math.min(priority, Thread.MAX_PRIORITY), Thread.MIN_PRIORITY);
        }

        public int getParallelism() {
            int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
            if (parallelism > 0) {
                int maximumParallelism = processors > Integer.MAX_VALUE / 2
                        ? Integer.MAX_VALUE
                        : processors * 2;
                return Math.min(parallelism, maximumParallelism);
            }

            return Math.max(1, (int) Math.ceil(Math.sqrt(processors)));
        }
    }
}
