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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeFeatureBiomeSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedGeneratorPolicy;

import art.arcane.volmlib.nativelib.terrain.feature.NativeFeatureTable;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedImportedFeatures;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.structure.nativegen.NativeFeatureGenerationPolicy;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import art.arcane.iris.generation.context.IrisContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Native placed-feature passthrough for one Iris dimension, gated on {@code importedFeatures.enabled}.
 *
 * <p>This runs the FEATURES half of vanilla's decoration pass and nothing else. Iris places native structures
 * itself, with its own vertical fitting and vegetation clearing, so calling {@code super.applyBiomeDecoration}
 * would place every structure a second time. The feature half is reproduced here off the same decoration and
 * feature seeds vanilla derives, so an imported feature lands where vanilla would have put it.
 *
 * <p>Threading: {@link NativeModdedImportedFeatures#run} runs on the worldgen thread that is generating the chunk, never on
 * {@link ModdedGenPool}. The FEATURES chunk step is not parallel-safe - it writes into the eight neighbouring
 * chunks through {@code WorldGenLevel}, and vanilla and every threaded chunk system serialize it. Terrain is
 * the only Iris step that may fan out.
 *
 * <p>Everything here is inert while the control is disabled: no table is built, no registry is walked, and
 * {@link NativeModdedImportedFeatures#generationSettings} answers exactly what vanilla's default getter answers.
 */
final class ModdedImportedFeatureStage implements NativeModdedGeneratorPolicy.FeatureStage<Engine> {
    private final NativeFeatureBiomeSource biomeSource;
    private final NativeModdedImportedFeatures nativeFeatures;
    private final ReentrantLock buildLock = new ReentrantLock();
    private final ConcurrentHashMap<Integer, NativeFeatureTable> featureTables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> inertGenerations = new ConcurrentHashMap<>();
    private volatile IrisModdedChunkGenerator generator;

    ModdedImportedFeatureStage(NativeFeatureBiomeSource biomeSource) {
        this.biomeSource = biomeSource;
        nativeFeatures = new NativeModdedImportedFeatures(biomeSource, new NativeModdedImportedFeatures.PlacementObserver() {
            @Override
            public void completed() {
                WorldCheckFeaturePlacement.recordPlacementPass();
            }

            @Override
            public void failed(int chunkX, int chunkZ, Throwable error) {
                WorldCheckFeaturePlacement.recordPlacementFailure(chunkX, chunkZ, error);
            }
        });
    }

    void bind(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    /**
     * Drops the feature table. Called from every repoint, hotload and unbind path so a table built for one
     * pack can never serve another.
     */
    void invalidate() {
        featureTables.clear();
        inertGenerations.clear();
    }

    boolean active() {
        return !featureTables.isEmpty();
    }

    /**
     * The generation-settings getter handed to {@code ChunkGenerator}'s two-argument constructor. Maps an Iris
     * custom biome holder onto the generation settings of the vanilla biome its Iris biome derives from, so
     * the per-step feature lists and {@code BiomeFilter}'s hasFeature gate both see real features for a biome
     * whose datapack JSON declares none by design. Real registry biomes pass straight through.
     *
     * <p>With {@code importedFeatures} disabled there is no table and this is vanilla's default getter.
     */
    public NativeFeatureTable currentTable() {
        Engine engine = generator == null ? null : generator.engineOrNull();
        return engine == null ? null : featureTables.get(engine.getCacheID());
    }

    public NativeModdedImportedFeatures nativeFeatures() {
        return nativeFeatures;
    }

    /**
     * Chunk-path prepare. The volatile fast path is unlocked, so a prepared stage costs two reads per chunk;
     * the build itself is serialized because {@code applyBiomeDecoration} calls this from every worldgen
     * thread, and two threads that both found the stage unprepared would each run {@code FeatureSorter}, whose
     * cycle detection is the expensive part. Waiting here is safe: this caller holds no generator monitor.
     */
    public void prepare(Engine engine) {
        prepare(engine, true);
    }

    /**
     * Bind and repoint prepare, which is what makes a feature-order cycle a single bind-time ERROR instead of
     * a chunk-generation crash. Those callers hold the generator monitor and the build path can need it (the
     * biome source may bind an engine while resolving), so this one never waits for another thread's build: it
     * builds now or leaves it to the next chunk's prepare.
     */
    void prepareWithoutWaiting(Engine engine) {
        prepare(engine, false);
    }

    private void prepare(Engine engine, boolean waitForBuild) {
        if (engine == null || engine.isClosed() || engine.isClosing()) {
            return;
        }
        int runtimeIdentity = engine.getCacheID();
        long generation = biomeSource.packGeneration();
        if (settled(runtimeIdentity, generation)) {
            return;
        }
        if (waitForBuild) {
            buildLock.lock();
        } else if (!buildLock.tryLock()) {
            return;
        }
        try {
            if (settled(runtimeIdentity, generation)) {
                return;
            }
            build(engine, runtimeIdentity, generation);
        } finally {
            buildLock.unlock();
        }
    }

    private boolean settled(int runtimeIdentity, long generation) {
        NativeFeatureTable current = featureTables.get(runtimeIdentity);
        if (current != null && current.generation() == generation) {
            return true;
        }
        return Long.valueOf(generation).equals(inertGenerations.get(runtimeIdentity));
    }

    private void build(Engine engine, int runtimeIdentity, long generation) {
        IrisImportedFeatureControl control;
        try {
            control = NativeFeatureGenerationPolicy.control(engine);
        } catch (RuntimeException error) {
            ModdedIrisLog.error("Iris could not read importedFeatures for this dimension; features off: {}",
                    error.toString(), error);
            markInert(runtimeIdentity, generation);
            return;
        }
        if (!control.shouldGenerateFeatures()) {
            markInert(runtimeIdentity, generation);
            return;
        }
        NativeFeatureTable built;
        try (GenerationSessionLease lease = engine.acquireGenerationLease("modded_imported_features");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            built = nativeFeatures.buildTable(new ModdedImportedFeaturePolicy(engine), control, generation);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris importedFeatures is off for {}: feature table construction failed: {}",
                    dimensionKey(engine), error.toString(), error);
            markInert(runtimeIdentity, generation);
            return;
        }
        if (built == null) {
            markInert(runtimeIdentity, generation);
            return;
        }
        featureTables.put(runtimeIdentity, built);
        inertGenerations.remove(runtimeIdentity);
        // Arm the worldcheck log watch here, before any chunk decorates: arming from the first pass instead
        // missed every far-chunk write the first chunk made. No-op unless -Diris.worldcheck is set.
        WorldCheckFeaturePlacement.arm();
        ModdedIrisLog.info("Iris importedFeatures on for {}: {} biomes, {} steps, {} custom-biome derivative maps",
                dimensionKey(engine), built.biomeCount(), built.stepCount(),
                built.derivativeCount());
    }

    private void markInert(int runtimeIdentity, long generation) {
        featureTables.remove(runtimeIdentity);
        inertGenerations.put(runtimeIdentity, generation);
    }

    public NativeFeatureTable placementTable(Engine engine) {
        int runtimeIdentity = engine.getCacheID();
        NativeFeatureTable table = featureTables.get(runtimeIdentity);
        if (table == null) {
            WorldCheckFeaturePlacement.recordFeaturesOff();
            return null;
        }
        if (table.generation() != biomeSource.packGeneration()) {
            featureTables.remove(runtimeIdentity, table);
            inertGenerations.remove(runtimeIdentity);
            return null;
        }
        return generator == null ? null : table;
    }

    void evictRuntime(int runtimeIdentity) {
        featureTables.remove(runtimeIdentity);
        inertGenerations.remove(runtimeIdentity);
    }

    private static String dimensionKey(Engine engine) {
        return engine.getDimension() == null ? "<unbound>" : engine.getDimension().getLoadKey();
    }
}
