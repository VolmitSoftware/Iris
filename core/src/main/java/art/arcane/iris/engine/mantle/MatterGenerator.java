package art.arcane.iris.engine.mantle;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.misc.RegenRuntime;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public interface MatterGenerator {
    long REGEN_PASS_CACHE_TTL_MS = 600000L;
    Executor DISPATCHER = MultiBurst.burst;
    ConcurrentHashMap<String, Set<Long>> REGEN_GENERATED_CHUNKS_BY_PASS = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Set<Long>> REGEN_CLEARED_CHUNKS_BY_PASS = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Set<Long>> REGEN_PLANNED_CHUNKS_BY_PASS = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Long> REGEN_PASS_TOUCHED_MS = new ConcurrentHashMap<>();

    Engine getEngine();

    Mantle<Matter> getMantle();

    int getRadius();

    int getRealRadius();

    List<Pair<List<MantleComponent>, Integer>> getComponents();

    @ChunkCoordinates
    default void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        boolean useMulticore = multicore || IrisSettings.get().getGenerator().isUseMulticoreMantle();
        String threadName = Thread.currentThread().getName();
        boolean regenThread = threadName.startsWith("Iris-Regen-");
        boolean traceRegen = regenThread && IrisSettings.get().getGeneral().isDebug();
        boolean forceRegen = regenThread;
        String regenPassKey = forceRegen ? resolveRegenPassKey(threadName) : null;
        boolean optimizedRegen = forceRegen && !IrisSettings.get().getGeneral().isDebug() && regenPassKey != null;
        int writeRadius = optimizedRegen ? Math.min(getRadius(), getRealRadius()) : getRadius();
        Set<Long> clearedChunks = optimizedRegen ? getRegenPassSet(REGEN_CLEARED_CHUNKS_BY_PASS, regenPassKey) : new HashSet<>();
        Set<Long> plannedChunks = optimizedRegen ? getRegenPassSet(REGEN_PLANNED_CHUNKS_BY_PASS, regenPassKey) : null;

        if (optimizedRegen) {
            touchRegenPass(regenPassKey);
        }

        if (traceRegen) {
            Iris.info("Regen matter start: center=" + x + "," + z
                    + " radius=" + getRadius()
                    + " realRadius=" + getRealRadius()
                    + " writeRadius=" + writeRadius
                    + " multicore=" + useMulticore
                    + " components=" + getComponents().size()
                    + " optimized=" + optimizedRegen
                    + " passKey=" + (regenPassKey == null ? "none" : regenPassKey)
                    + " thread=" + threadName);
        }

        try (MantleWriter writer = new MantleWriter(getEngine().getMantle(), getMantle(), x, z, writeRadius, useMulticore)) {
            for (Pair<List<MantleComponent>, Integer> pair : getComponents()) {
                int rawPassRadius = pair.getB();
                int passRadius = optimizedRegen ? Math.min(rawPassRadius, writeRadius) : rawPassRadius;
                String passFlags = pair.getA().stream().map(component -> component.getFlag().toString()).collect(Collectors.joining(","));
                String passFlagKey = optimizedRegen ? regenPassKey + "|" + passFlags : null;
                Set<Long> generatedChunks = passFlagKey == null ? null : getRegenPassSet(REGEN_GENERATED_CHUNKS_BY_PASS, passFlagKey);
                int visitedChunks = 0;
                int clearedCount = 0;
                int plannedSkipped = 0;
                int componentSkipped = 0;
                int componentForcedReset = 0;
                int launchedLayers = 0;
                int dedupSkipped = 0;
                List<CompletableFuture<Void>> launchedTasks = useMulticore ? new ArrayList<>() : null;

                if (passFlagKey != null) {
                    touchRegenPass(passFlagKey);
                }
                if (traceRegen) {
                    Iris.info("Regen matter pass start: center=" + x + "," + z
                            + " passRadius=" + passRadius
                            + " rawPassRadius=" + rawPassRadius
                            + " flags=[" + passFlags + "]");
                }

                for (int i = -passRadius; i <= passRadius; i++) {
                    for (int j = -passRadius; j <= passRadius; j++) {
                        int passX = x + i;
                        int passZ = z + j;
                        visitedChunks++;
                        long passKey = chunkKey(passX, passZ);
                        if (generatedChunks != null && !generatedChunks.add(passKey)) {
                            dedupSkipped++;
                            continue;
                        }

                        MantleChunk<Matter> chunk = writer.acquireChunk(passX, passZ);
                        if (forceRegen) {
                            if (clearedChunks.add(passKey)) {
                                chunk.deleteSlices(BlockData.class);
                                chunk.deleteSlices(String.class);
                                chunk.deleteSlices(TileWrapper.class);
                                chunk.flag(MantleFlag.PLANNED, false);
                                clearedCount++;
                            }
                        }

                        if (!forceRegen && chunk.isFlagged(MantleFlag.PLANNED)) {
                            plannedSkipped++;
                            continue;
                        }

                        for (MantleComponent component : pair.getA()) {
                            if (!forceRegen && chunk.isFlagged(component.getFlag())) {
                                componentSkipped++;
                                continue;
                            }
                            if (forceRegen && chunk.isFlagged(component.getFlag())) {
                                chunk.flag(component.getFlag(), false);
                                componentForcedReset++;
                            }

                            launchedLayers++;
                            int finalPassX = passX;
                            int finalPassZ = passZ;
                            MantleChunk<Matter> finalChunk = chunk;
                            MantleComponent finalComponent = component;
                            Runnable task = () -> finalChunk.raiseFlagUnchecked(finalComponent.getFlag(),
                                    () -> finalComponent.generateLayer(writer, finalPassX, finalPassZ, context));

                            if (useMulticore) {
                                launchedTasks.add(CompletableFuture.runAsync(task, DISPATCHER));
                            } else {
                                task.run();
                            }
                        }
                    }
                }

                if (useMulticore) {
                    for (CompletableFuture<Void> launchedTask : launchedTasks) {
                        launchedTask.join();
                    }
                }

                if (traceRegen) {
                    Iris.info("Regen matter pass done: center=" + x + "," + z
                            + " passRadius=" + passRadius
                            + " rawPassRadius=" + rawPassRadius
                            + " visited=" + visitedChunks
                            + " cleared=" + clearedCount
                            + " dedupSkipped=" + dedupSkipped
                            + " plannedSkipped=" + plannedSkipped
                            + " componentSkipped=" + componentSkipped
                            + " componentForcedReset=" + componentForcedReset
                            + " launchedLayers=" + launchedLayers
                            + " flags=[" + passFlags + "]");
                }
            }

            for (int i = -getRealRadius(); i <= getRealRadius(); i++) {
                for (int j = -getRealRadius(); j <= getRealRadius(); j++) {
                    int realX = x + i;
                    int realZ = z + j;
                    long realKey = chunkKey(realX, realZ);
                    if (plannedChunks != null && !plannedChunks.add(realKey)) {
                        continue;
                    }
                    writer.acquireChunk(realX, realZ).flag(MantleFlag.PLANNED, true);
                }
            }
        }

        if (traceRegen) {
            Iris.info("Regen matter done: center=" + x + "," + z
                    + " markedRealRadius=" + getRealRadius()
                    + " forceRegen=" + forceRegen);
        }
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static Set<Long> getRegenPassSet(ConcurrentHashMap<String, Set<Long>> store, String passKey) {
        return store.computeIfAbsent(passKey, key -> ConcurrentHashMap.newKeySet());
    }

    private static String resolveRegenPassKey(String threadName) {
        String runtimeKey = RegenRuntime.getRunId();
        if (runtimeKey != null && !runtimeKey.isBlank()) {
            return runtimeKey;
        }

        if (!threadName.startsWith("Iris-Regen-")) {
            return null;
        }

        String suffix = threadName.substring("Iris-Regen-".length());
        int lastDash = suffix.lastIndexOf('-');
        if (lastDash <= 0) {
            return suffix;
        }
        return suffix.substring(0, lastDash);
    }

    private static void touchRegenPass(String passKey) {
        long now = System.currentTimeMillis();
        REGEN_PASS_TOUCHED_MS.put(passKey, now);
        if (REGEN_PASS_TOUCHED_MS.size() <= 64) {
            return;
        }

        Iterator<Map.Entry<String, Long>> iterator = REGEN_PASS_TOUCHED_MS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() <= REGEN_PASS_CACHE_TTL_MS) {
                continue;
            }

            String key = entry.getKey();
            iterator.remove();
            REGEN_GENERATED_CHUNKS_BY_PASS.remove(key);
            REGEN_CLEARED_CHUNKS_BY_PASS.remove(key);
            REGEN_PLANNED_CHUNKS_BY_PASS.remove(key);
        }
    }
}
