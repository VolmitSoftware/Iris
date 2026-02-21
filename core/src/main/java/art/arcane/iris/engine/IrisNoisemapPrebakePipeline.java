package art.arcane.iris.engine;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.util.common.misc.ServerProperties;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class IrisNoisemapPrebakePipeline {
    private static final long[] NO_SEEDS = new long[0];
    private static final long STARTUP_PROGRESS_INTERVAL_MS = Long.getLong("iris.prebake.progress.interval", 30000L);
    private static final int STATE_VERSION = 1;
    private static final String STATE_FILE = "noisemap-prebake.state";
    private static final AtomicBoolean STARTUP_PREBAKE_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicBoolean STARTUP_PREBAKE_FAILURE_REPORTED = new AtomicBoolean(false);
    private static final AtomicInteger STARTUP_WORKER_SEQUENCE = new AtomicInteger();
    private static final AtomicReference<CompletableFuture<Void>> STARTUP_PREBAKE_COMPLETION = new AtomicReference<>();
    private static final ConcurrentHashMap<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> SKIP_ONCE = new ConcurrentHashMap<>();
    private static final Set<String> PREBAKE_LOADERS = Set.of(
            "dimensions",
            "regions",
            "biomes",
            "generators",
            "caves",
            "ravines",
            "mods",
            "expressions"
    );
    private static final Set<String> PREBAKE_STATE_FOLDERS = Set.of(
            "dimensions",
            "regions",
            "biomes",
            "generators",
            "caves",
            "ravines",
            "mods",
            "expressions",
            "scripts",
            "images",
            "snippet"
    );

    private IrisNoisemapPrebakePipeline() {
    }

    public static void scheduleInstalledPacksPrebakeAsync() {
        if (!IrisSettings.get().getPregen().isStartupNoisemapPrebake()) {
            return;
        }

        if (!STARTUP_PREBAKE_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        STARTUP_PREBAKE_COMPLETION.set(completion);
        Thread thread = new Thread(() -> {
            try {
                prebakeInstalledPacksAtStartup();
                completion.complete(null);
            } catch (Throwable throwable) {
                completion.completeExceptionally(throwable);
                if (STARTUP_PREBAKE_FAILURE_REPORTED.compareAndSet(false, true)) {
                    Iris.warn("Startup noisemap pre-bake failed.");
                    Iris.reportError(throwable);
                }
            }
        }, "Iris-StartupNoisemapPrebake");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean awaitInstalledPacksPrebakeForStudio() {
        if (!IrisSettings.get().getPregen().isStartupNoisemapPrebake()) {
            return false;
        }

        scheduleInstalledPacksPrebakeAsync();
        CompletableFuture<Void> completion = STARTUP_PREBAKE_COMPLETION.get();
        if (completion == null) {
            return false;
        }

        try {
            completion.join();
            return true;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (STARTUP_PREBAKE_FAILURE_REPORTED.compareAndSet(false, true)) {
                Iris.warn("Startup noisemap pre-bake failed.");
                Iris.reportError(cause);
            }
            return false;
        } catch (Throwable throwable) {
            if (STARTUP_PREBAKE_FAILURE_REPORTED.compareAndSet(false, true)) {
                Iris.warn("Startup noisemap pre-bake failed.");
                Iris.reportError(throwable);
            }
            return false;
        }
    }

    public static void prebakeInstalledPacksAtStartup() {
        List<PrebakeTarget> targets = collectStartupTargets();
        if (targets.isEmpty()) {
            Iris.info("Startup noisemap pre-bake skipped (no installed or self-contained packs found).");
            return;
        }

        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        long startupSeed = dynamicStartupSeed();
        SeedManager seedManager = new SeedManager(startupSeed);
        int targetCount = targets.size();
        int workerCount = Math.min(targetCount, Math.max(1, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())));
        ExecutorService workers = Executors.newFixedThreadPool(workerCount, new StartupPrebakeThreadFactory());
        CompletionService<StartupTargetResult> completion = new ExecutorCompletionService<>(workers);
        StartupProgress progress = new StartupProgress(targetCount);
        Iris.info("Startup pack noisemap pre-bake running in background targets=" + targetCount + " workers=" + workerCount + " seed=" + startupSeed);

        for (PrebakeTarget target : targets) {
            completion.submit(() -> new StartupTargetResult(target.label,
                    prebake(IrisData.get(target.folder), seedManager, "startup", target.label, () -> false, false, false, progress)));
        }

        int completedTargets = 0;
        long lastProgress = System.currentTimeMillis();
        while (completedTargets < targetCount) {
            try {
                Future<StartupTargetResult> future = completion.poll(2, TimeUnit.SECONDS);
                if (future != null) {
                    StartupTargetResult result = future.get();
                    completedTargets++;
                    progress.onTargetCompleted(result.result);
                }
            } catch (Throwable throwable) {
                completedTargets++;
                progress.onTargetFailed();
                Iris.reportError(throwable);
            }

            long now = System.currentTimeMillis();
            if (completedTargets < targetCount && now - lastProgress >= STARTUP_PROGRESS_INTERVAL_MS) {
                logStartupProgress(progress, stopwatch);
                lastProgress = now;
            }
        }

        workers.shutdownNow();

        Iris.info("Startup pack noisemap pre-bake scan completed targets="
                + targetCount
                + " executed="
                + progress.executedTargets.get()
                + " unchanged="
                + progress.unchangedTargets.get()
                + " failed="
                + progress.failedTargets.get()
                + " styles="
                + progress.stylesFinished.get()
                + "/"
                + progress.stylesDiscovered.get()
                + " seed="
                + startupSeed
                + " in "
                + Form.duration(stopwatch.getMilliseconds(), 2));
    }

    public static int clearInstalledPackPrebakeStates() {
        int cleared = 0;
        List<PrebakeTarget> targets = collectStartupTargets();
        for (PrebakeTarget target : targets) {
            File state = stateFile(target.folder);
            if (state.exists() && state.delete()) {
                cleared++;
            }
        }

        return cleared;
    }

    private static void logStartupProgress(StartupProgress progress, PrecisionStopwatch stopwatch) {
        int targetCount = progress.targetTotal;
        if (targetCount <= 0) {
            return;
        }

        int completedTargets = progress.targetsCompleted.get();
        int discoveredStyles = progress.stylesDiscovered.get();
        int finishedStyles = progress.stylesFinished.get();
        int executedTargets = progress.executedTargets.get();
        int unchangedTargets = progress.unchangedTargets.get();
        int failedTargets = progress.failedTargets.get();
        double elapsed = stopwatch.getMilliseconds();
        if (discoveredStyles > 0) {
            int remainingStyles = Math.max(0, discoveredStyles - finishedStyles);
            int percent = (int) Math.round((finishedStyles * 100D) / discoveredStyles);
            String eta = "estimating";
            if (finishedStyles > 0) {
                long etaMillis = Math.max(0L, Math.round((elapsed / finishedStyles) * remainingStyles));
                eta = Form.duration(etaMillis, 2);
            }

            Iris.info("Startup noisemap pre-bake progress "
                    + percent
                    + "% styles="
                    + finishedStyles
                    + "/"
                    + discoveredStyles
                    + " targets="
                    + completedTargets
                    + "/"
                    + targetCount
                    + " remaining="
                    + remainingStyles
                    + " executed="
                    + executedTargets
                    + " unchanged="
                    + unchangedTargets
                    + " failed="
                    + failedTargets
                    + " elapsed="
                    + Form.duration(elapsed, 2)
                    + " eta="
                    + eta);
            return;
        }

        int remainingTargets = Math.max(0, targetCount - completedTargets);
        int percent = (int) Math.round((completedTargets * 100D) / targetCount);
        String eta = "estimating";
        if (completedTargets > 0) {
            long etaMillis = Math.max(0L, Math.round((elapsed / completedTargets) * remainingTargets));
            eta = Form.duration(etaMillis, 2);
        }

        Iris.info("Startup noisemap pre-bake progress "
                + percent
                + "% targets="
                + completedTargets
                + "/"
                + targetCount
                + " remaining="
                + remainingTargets
                + " executed="
                + executedTargets
                + " unchanged="
                + unchangedTargets
                + " failed="
                + failedTargets
                + " elapsed="
                + Form.duration(elapsed, 2)
                + " eta="
                + eta);
    }

    public static long dynamicStartupSeed() {
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSeed();
        }

        String configuredSeed = ServerProperties.DATA.getProperty("level-seed", "").trim();
        if (!configuredSeed.isEmpty()) {
            try {
                return Long.parseLong(configuredSeed);
            } catch (NumberFormatException ignored) {
                return mixSeed(0x9E3779B97F4A7C15L, configuredSeed.hashCode());
            }
        }

        return mixSeed(0x94D049BB133111EBL, ServerProperties.LEVEL_NAME.hashCode());
    }

    public static void prebake(Engine engine) {
        if (engine == null || engine.isClosed()) {
            return;
        }

        if (!IrisSettings.get().getPregen().isStartupNoisemapPrebake()) {
            return;
        }

        prebake(engine.getData(),
                engine.getSeedManager(),
                engine.getWorld().name(),
                engine.getDimension().getLoadKey(),
                engine::isClosed,
                false,
                true,
                null);
    }

    public static void prebake(IrisData data, SeedManager seedManager, String worldName, String dimensionKey) {
        if (!IrisSettings.get().getPregen().isStartupNoisemapPrebake()) {
            return;
        }

        prebake(data, seedManager, worldName, dimensionKey, () -> false, true, true, null);
    }

    public static void prebakeForced(IrisData data, SeedManager seedManager, String worldName, String dimensionKey) {
        prebake(data, seedManager, worldName, dimensionKey, () -> false, false, true, null);
    }

    private static PrebakeRunResult prebake(IrisData data,
                                            SeedManager seedManager,
                                            String worldName,
                                            String dimensionKey,
                                            BooleanSupplier shouldAbort,
                                            boolean primeSkipOnce,
                                            boolean logResult,
                                            StartupProgress progress) {
        if (data == null || seedManager == null) {
            return PrebakeRunResult.SKIPPED;
        }

        if (shouldAbort != null && shouldAbort.getAsBoolean()) {
            return PrebakeRunResult.SKIPPED;
        }

        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        String safeWorldName = worldName == null ? "unknown" : worldName;
        String safeDimensionKey = dimensionKey == null ? "unknown" : dimensionKey;
        boolean exhaustive = dynamicExhaustivePrebakeMode();
        int fallbackCacheSize = dynamicFallbackCacheSize();
        String key = prebakeKey(data, seedManager, safeDimensionKey, exhaustive, fallbackCacheSize);

        if (SKIP_ONCE.remove(key) != null) {
            if (logResult) {
                Iris.info("Startup noisemap pre-bake skipped for " + safeWorldName + "/" + safeDimensionKey + " (already pre-baked before engine init).");
            }
            return PrebakeRunResult.SKIPPED;
        }

        String stateToken = prebakeStateToken(data, seedManager, exhaustive, fallbackCacheSize);
        if (isCurrentState(data, stateToken)) {
            if (primeSkipOnce) {
                SKIP_ONCE.put(key, Boolean.TRUE);
            }
            if (logResult) {
                Iris.info("Startup noisemap pre-bake skipped for " + safeWorldName + "/" + safeDimensionKey + " (unchanged pack state).");
            }
            return PrebakeRunResult.UNCHANGED;
        }

        List<StyleReference> styles = collectStyles(data, fallbackCacheSize, progress);
        int styleCount = styles.size();

        if (styleCount == 0) {
            writeCurrentState(data, stateToken);
            if (primeSkipOnce) {
                SKIP_ONCE.put(key, Boolean.TRUE);
            }
            if (logResult) {
                Iris.info("Startup noisemap pre-bake skipped for " + safeWorldName + "/" + safeDimensionKey + " (no cacheable styles found).");
            }
            return PrebakeRunResult.SKIPPED;
        }

        long[] domainSeeds = collectDomainSeeds(seedManager);
        if (domainSeeds.length == 0) {
            if (logResult) {
                Iris.warn("Startup noisemap pre-bake skipped for " + safeWorldName + "/" + safeDimensionKey + " (no seed domains).");
            }
            return PrebakeRunResult.SKIPPED;
        }

        AtomicInteger prebakedStyles = new AtomicInteger();
        AtomicInteger prebakedVariants = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        BurstExecutor burst = MultiBurst.burst.burst(styleCount);

        for (StyleReference reference : styles) {
            burst.queue(() -> {
                if (shouldAbort != null && shouldAbort.getAsBoolean()) {
                    if (progress != null) {
                        progress.onStyleFinished();
                    }
                    return;
                }

                try {
                    int baked = prebakeStyle(reference, data, domainSeeds, exhaustive, fallbackCacheSize);
                    if (baked <= 0) {
                        return;
                    }

                    prebakedStyles.incrementAndGet();
                    prebakedVariants.addAndGet(baked);
                } catch (Throwable throwable) {
                    failures.incrementAndGet();
                    Iris.reportError(throwable);
                } finally {
                    if (progress != null) {
                        progress.onStyleFinished();
                    }
                }
            });
        }

        burst.complete();

        if (failures.get() == 0) {
            writeCurrentState(data, stateToken);
            if (primeSkipOnce) {
                SKIP_ONCE.put(key, Boolean.TRUE);
            }
        }

        if (logResult) {
            Iris.info("Startup noisemap pre-bake completed for "
                    + safeWorldName
                    + "/"
                    + safeDimensionKey
                    + " styles="
                    + styleCount
                    + " prebaked="
                    + prebakedStyles.get()
                    + " variants="
                    + prebakedVariants.get()
                    + " failures="
                    + failures.get()
                    + " mode="
                    + (exhaustive ? "exhaustive" : "targeted")
                    + " fallback="
                    + fallbackCacheSize
                    + " in "
                    + Form.duration(stopwatch.getMilliseconds(), 2));
        }
        return new PrebakeRunResult(true, false, failures.get());
    }

    private static boolean dynamicExhaustivePrebakeMode() {
        int cores = Runtime.getRuntime().availableProcessors();
        long memoryGiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L);
        return cores >= 24 && memoryGiB >= 64;
    }

    private static int dynamicFallbackCacheSize() {
        int cores = Runtime.getRuntime().availableProcessors();
        long memoryGiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L);

        if (memoryGiB >= 24 && cores >= 12) {
            return 64;
        }

        if (memoryGiB >= 16 && cores >= 8) {
            return 48;
        }

        if (memoryGiB >= 8 && cores >= 6) {
            return 40;
        }

        if (memoryGiB >= 4 && cores >= 4) {
            return 32;
        }

        return 24;
    }

    private static String prebakeStateToken(IrisData data, SeedManager seedManager, boolean exhaustive, int fallbackCacheSize) {
        File[] roots = prebakeStateRoots(data);
        long stateHash = roots.length == 0 ? 0L : IO.hashRecursive(roots);
        long seedHash = hashSeeds(collectDomainSeeds(seedManager));
        return STATE_VERSION
                + "|"
                + Long.toUnsignedString(stateHash)
                + "|"
                + Long.toUnsignedString(seedHash)
                + "|"
                + (exhaustive ? "X" : "T")
                + "|"
                + fallbackCacheSize;
    }

    private static File[] prebakeStateRoots(IrisData data) {
        File dataFolder = data.getDataFolder();
        List<File> roots = new ArrayList<>();

        for (String folder : PREBAKE_STATE_FOLDERS) {
            File candidate = new File(dataFolder, folder);
            if (candidate.exists()) {
                roots.add(candidate);
            }
        }

        roots.sort(Comparator.comparing(File::getName));
        return roots.toArray(new File[0]);
    }

    private static long hashSeeds(long[] seeds) {
        long hash = 1125899906842597L;
        for (long seed : seeds) {
            hash = (hash * 31L) ^ seed;
        }
        return hash;
    }

    private static File stateFile(IrisData data) {
        return stateFile(data.getDataFolder());
    }

    private static File stateFile(File dataFolder) {
        return new File(new File(dataFolder, ".cache"), STATE_FILE);
    }

    private static boolean isCurrentState(IrisData data, String token) {
        File stateFile = stateFile(data);
        if (!stateFile.exists()) {
            return false;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(stateFile)) {
            properties.load(input);
        } catch (IOException e) {
            return false;
        }

        String previous = properties.getProperty("token");
        return token.equals(previous);
    }

    private static void writeCurrentState(IrisData data, String token) {
        File stateFile = stateFile(data);
        File parent = stateFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("token", token);
        properties.setProperty("updated", Long.toString(System.currentTimeMillis()));

        try (FileOutputStream output = new FileOutputStream(stateFile)) {
            properties.store(output, "Iris noisemap prebake state");
        } catch (IOException ignored) {
        }
    }

    private static String prebakeKey(IrisData data, SeedManager seedManager, String dimensionKey, boolean exhaustive, int fallbackCacheSize) {
        return data.getDataFolder().getAbsolutePath()
                + "|"
                + seedManager.getSeed()
                + "|"
                + dimensionKey
                + "|"
                + fallbackCacheSize
                + "|"
                + (exhaustive ? "X" : "T");
    }

    private static int prebakeStyle(StyleReference reference, IrisData data, long[] domainSeeds, boolean exhaustive, int fallbackCacheSize) {
        IrisGeneratorStyle style = reference.style;
        if (!isCacheable(style, fallbackCacheSize)) {
            return 0;
        }

        int styleHash = reference.hash;

        if (exhaustive) {
            int variants = 0;

            for (int i = 0; i < domainSeeds.length; i++) {
                long mixedSeed = mixSeed(domainSeeds[i], styleHash + (i * 131));
                CNG baked = style.createForPrebake(new RNG(mixedSeed), data, fallbackCacheSize);
                if (baked != null) {
                    variants++;
                }
            }

            return variants;
        }

        int index = Math.floorMod(styleHash, domainSeeds.length);
        long primarySeed = mixSeed(domainSeeds[index], styleHash);
        int variants = 0;

        CNG primary = style.createForPrebake(new RNG(primarySeed), data, fallbackCacheSize);
        if (primary != null) {
            variants++;
        }

        return variants;
    }

    private static boolean isCacheable(IrisGeneratorStyle style, int fallbackCacheSize) {
        if (style == null) {
            return false;
        }
        return style.getCacheSize() > 0 || fallbackCacheSize > 0;
    }

    private static List<StyleReference> collectStyles(IrisData data, int fallbackCacheSize, StartupProgress progress) {
        LinkedHashMap<Integer, StyleReference> styles = new LinkedHashMap<>();
        Collection<ResourceLoader<? extends IrisRegistrant>> loaders = data.getLoaders().values();

        for (ResourceLoader<? extends IrisRegistrant> loader : loaders) {
            if (!PREBAKE_LOADERS.contains(loader.getFolderName())) {
                continue;
            }

            String[] keys = loader.getPossibleKeys();

            for (String key : keys) {
                IrisRegistrant registrant = loader.load(key, false);
                if (registrant == null) {
                    continue;
                }

                String rootPath = loader.getFolderName() + ":" + key;
                collectFromObject(registrant, rootPath, styles, fallbackCacheSize, progress);
            }
        }

        return new ArrayList<>(styles.values());
    }

    private static void collectFromObject(Object root, String rootPath, LinkedHashMap<Integer, StyleReference> styles, int fallbackCacheSize, StartupProgress progress) {
        if (root == null) {
            return;
        }

        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root, rootPath));

        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            Object value = node.value;
            if (value == null) {
                continue;
            }

            if (value instanceof IrisGeneratorStyle style) {
                if (isCacheable(style, fallbackCacheSize)) {
                    int styleSignature = style.prebakeSignature();
                    if (styles.putIfAbsent(styleSignature, new StyleReference(style, styleSignature)) == null && progress != null) {
                        progress.onStylesDiscovered(1);
                    }
                }
            }

            Class<?> type = value.getClass();
            if (isLeafType(type)) {
                continue;
            }

            if (visited.put(value, Boolean.TRUE) != null) {
                continue;
            }

            if (type.isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(value, i);
                    queue.addLast(new Node(element, node.path + "[" + i + "]"));
                }
                continue;
            }

            if (value instanceof Iterable<?> iterable) {
                int index = 0;
                for (Object element : iterable) {
                    queue.addLast(new Node(element, node.path + "[" + index + "]"));
                    index++;
                }
                continue;
            }

            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object entryValue = entry.getValue();
                    if (key != null) {
                        queue.addLast(new Node(key, node.path + "{k}"));
                    }
                    if (entryValue != null) {
                        queue.addLast(new Node(entryValue, node.path + "{v}"));
                    }
                }
                continue;
            }

            Field[] fields = fieldsOf(type);
            for (Field field : fields) {
                if (skipField(field)) {
                    continue;
                }

                Object fieldValue;
                try {
                    if (!field.canAccess(value)) {
                        field.setAccessible(true);
                    }
                    fieldValue = field.get(value);
                } catch (Throwable ignored) {
                    continue;
                }

                if (fieldValue == null) {
                    continue;
                }

                queue.addLast(new Node(fieldValue, node.path + "." + field.getName()));
            }
        }
    }

    private static List<PrebakeTarget> collectStartupTargets() {
        LinkedHashMap<String, PrebakeTarget> targets = new LinkedHashMap<>();
        File packsFolder = Iris.instance.getDataFolder("packs");
        File[] packs = packsFolder.listFiles();
        if (packs != null) {
            Arrays.sort(packs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File pack : packs) {
                addStartupTarget(targets, pack);
            }
        }

        File worldContainer = Bukkit.getWorldContainer();
        File[] worlds = worldContainer.listFiles();
        if (worlds != null) {
            Arrays.sort(worlds, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File world : worlds) {
                if (world == null || !world.isDirectory()) {
                    continue;
                }

                File selfContainedPack = new File(world, "iris/pack");
                addStartupTarget(targets, selfContainedPack);
            }
        }

        return new ArrayList<>(targets.values());
    }

    private static void addStartupTarget(LinkedHashMap<String, PrebakeTarget> targets, File folder) {
        if (!isPrebakeDataFolder(folder)) {
            return;
        }

        String canonicalPath = toCanonicalPath(folder);
        if (targets.containsKey(canonicalPath)) {
            return;
        }

        targets.put(canonicalPath, new PrebakeTarget(folder, startupLabel(folder)));
    }

    private static boolean isPrebakeDataFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return false;
        }

        for (String loaderFolder : PREBAKE_LOADERS) {
            File candidate = new File(folder, loaderFolder);
            if (candidate.exists() && candidate.isDirectory()) {
                return true;
            }
        }

        return false;
    }

    private static String startupLabel(File folder) {
        File parent = folder.getParentFile();
        if (parent != null && "iris".equalsIgnoreCase(parent.getName())) {
            File worldFolder = parent.getParentFile();
            if (worldFolder != null) {
                return worldFolder.getName() + "/self-contained";
            }
        }

        return folder.getName();
    }

    private static String toCanonicalPath(File folder) {
        try {
            return folder.getCanonicalPath();
        } catch (IOException e) {
            return folder.getAbsolutePath();
        }
    }

    private static long[] collectDomainSeeds(SeedManager seedManager) {
        if (seedManager == null) {
            return NO_SEEDS;
        }

        return new long[]{
                seedManager.getSeed(),
                seedManager.getComplex(),
                seedManager.getComplexStreams(),
                seedManager.getBasic(),
                seedManager.getHeight(),
                seedManager.getComponent(),
                seedManager.getScript(),
                seedManager.getMantle(),
                seedManager.getEntity(),
                seedManager.getBiome(),
                seedManager.getDecorator(),
                seedManager.getTerrain(),
                seedManager.getSpawn(),
                seedManager.getCarve(),
                seedManager.getDeposit(),
                seedManager.getPost(),
                seedManager.getBodies(),
                seedManager.getMode()
        };
    }

    private static Field[] fieldsOf(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, IrisNoisemapPrebakePipeline::resolveFields);
    }

    private static Field[] resolveFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            Field[] declared = cursor.getDeclaredFields();
            for (Field field : declared) {
                fields.add(field);
            }
            cursor = cursor.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    private static boolean skipField(Field field) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
            return true;
        }

        Class<?> type = field.getType();
        if (type.isPrimitive()) {
            return true;
        }

        return isLeafType(type);
    }

    private static boolean isLeafType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()) {
            return true;
        }

        if (type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type)
                || type == Class.class
                || type == Locale.class
                || type == File.class) {
            return true;
        }

        String name = type.getName();
        return name.startsWith("java.time.")
                || name.startsWith("java.util.concurrent.atomic.")
                || name.startsWith("org.bukkit.")
                || name.startsWith("net.minecraft.")
                || name.startsWith("art.arcane.iris.engine.data.cache.")
                || name.equals("art.arcane.volmlib.util.math.RNG");
    }

    private static long mixSeed(long seed, int hash) {
        long mixed = seed ^ ((long) hash * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private static final class Node {
        private final Object value;
        private final String path;

        private Node(Object value, String path) {
            this.value = value;
            this.path = path;
        }
    }

    private static final class StyleReference {
        private final IrisGeneratorStyle style;
        private final int hash;

        private StyleReference(IrisGeneratorStyle style, int hash) {
            this.style = style;
            this.hash = hash;
        }
    }

    private static final class PrebakeRunResult {
        private static final PrebakeRunResult SKIPPED = new PrebakeRunResult(false, false, 0);
        private static final PrebakeRunResult UNCHANGED = new PrebakeRunResult(false, true, 0);

        private final boolean executed;
        private final boolean unchanged;
        private final int failures;

        private PrebakeRunResult(boolean executed, boolean unchanged, int failures) {
            this.executed = executed;
            this.unchanged = unchanged;
            this.failures = failures;
        }
    }

    private static final class PrebakeTarget {
        private final File folder;
        private final String label;

        private PrebakeTarget(File folder, String label) {
            this.folder = folder;
            this.label = label;
        }
    }

    private static final class StartupTargetResult {
        private final String label;
        private final PrebakeRunResult result;

        private StartupTargetResult(String label, PrebakeRunResult result) {
            this.label = label;
            this.result = result;
        }
    }

    private static final class StartupProgress {
        private final int targetTotal;
        private final AtomicInteger targetsCompleted = new AtomicInteger();
        private final AtomicInteger executedTargets = new AtomicInteger();
        private final AtomicInteger unchangedTargets = new AtomicInteger();
        private final AtomicInteger failedTargets = new AtomicInteger();
        private final AtomicInteger stylesDiscovered = new AtomicInteger();
        private final AtomicInteger stylesFinished = new AtomicInteger();

        private StartupProgress(int targetTotal) {
            this.targetTotal = targetTotal;
        }

        private void onStylesDiscovered(int styleCount) {
            if (styleCount > 0) {
                stylesDiscovered.addAndGet(styleCount);
            }
        }

        private void onStyleFinished() {
            stylesFinished.incrementAndGet();
        }

        private void onTargetCompleted(PrebakeRunResult result) {
            targetsCompleted.incrementAndGet();
            if (result.executed) {
                executedTargets.incrementAndGet();
            }
            if (result.unchanged) {
                unchangedTargets.incrementAndGet();
            }
            if (result.failures > 0) {
                failedTargets.incrementAndGet();
            }
        }

        private void onTargetFailed() {
            targetsCompleted.incrementAndGet();
            failedTargets.incrementAndGet();
        }
    }

    private static final class StartupPrebakeThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Iris-NoisemapPrebake-" + STARTUP_WORKER_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
