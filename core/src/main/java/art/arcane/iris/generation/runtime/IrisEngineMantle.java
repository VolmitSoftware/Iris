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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.mantle.MantleComponent;
import art.arcane.iris.generation.mantle.MantlePass;
import art.arcane.iris.generation.mantle.MantleCarvingComponent;
import art.arcane.iris.generation.mantle.MantleFloatingObjectComponent;
import art.arcane.iris.generation.mantle.MantleObjectComponent;
import art.arcane.iris.generation.mantle.MantleHydrologyComponent;
import art.arcane.iris.generation.mantle.IrisStructureComponent;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.world.storage.matter.IrisMatterContext;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.mantle.io.Lz4IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.runtime.IOWorker;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.iris.generation.concurrent.HyperLock;
import art.arcane.iris.generation.concurrent.MultiBurst;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Data
@EqualsAndHashCode(exclude = "engine")
@ToString(exclude = "engine")
public class IrisEngineMantle implements EngineMantle {
    public static final String STORAGE_FOLDER_NAME = "mantle-hydrology";
    private final Engine engine;
    private final Path storageDirectory;
    @Getter(AccessLevel.NONE)
    private final AtomicReference<IrisData> runtimeData;
    private final Mantle<Matter> mantle;
    @Getter(AccessLevel.NONE)
    private final KMap<Integer, KList<MantleComponent>> components;
    private final KMap<MantleFlag, MantleComponent> registeredComponents = new KMap<>();
    private final AtomicCache<List<MantlePass>> componentsCache = new AtomicCache<>();
    private final AtomicCache<Set<MantleFlag>> disabledFlags = new AtomicCache<>();
    private final MantleObjectComponent object;

    public IrisEngineMantle(Engine engine) {
        this(engine, legacyStorageDirectory(engine.getTarget()));
    }

    public IrisEngineMantle(Engine engine, Path storageDirectory) {
        this.engine = Objects.requireNonNull(engine, "Iris engine");
        this.storageDirectory = normalizeStorageDirectory(storageDirectory);
        this.runtimeData = new AtomicReference<>(Objects.requireNonNull(engine.getData(), "Iris mantle data"));
        this.mantle = createMantle(engine, this.storageDirectory, runtimeData::get);
        components = new KMap<>();
        MantleObjectComponent createdObject;
        try {
            registerComponent(new MantleCarvingComponent(this));
            registerComponent(new MantleHydrologyComponent(this));
            createdObject = new MantleObjectComponent(this);
            registerComponent(createdObject);
            registerComponent(new MantleFloatingObjectComponent(this));
            registerComponent(new IrisStructureComponent(this));
        } catch (Throwable failure) {
            try {
                mantle.close();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw EngineShutdownSequence.propagate(failure);
        }
        object = createdObject;
    }

    @Override
    public int getRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getFirst().passChunkRadius();
    }

    @Override
    public int getRealRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getLast().passChunkRadius();
    }

    @Override
    public void cleanupChunksCoveredBy(int newRealX, int newRealZ, boolean force, ChunkCleanupCallback callback) {
        Objects.requireNonNull(callback, "callback");
        int radius = Math.max(getRadius(), getRealRadius());
        MissingRealChunk missingChunk = radius > 0 ? new MissingRealChunk() : null;
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            int candidateX = newRealX + offsetX;
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int candidateZ = newRealZ + offsetZ;
                if (getMantle().hasFlag(candidateX, candidateZ, MantleFlag.CLEANED)
                        || !isCovered(candidateX, candidateZ, missingChunk)) {
                    continue;
                }
                if (cleanupCoveredChunk(candidateX, candidateZ, force)) {
                    callback.onChunkCleaned(candidateX, candidateZ);
                }
            }
        }
    }

    @Override
    public List<MantlePass> getComponents() {
        return componentsCache.aquire(() -> {
            List<List<MantleComponent>> passes = components.keySet()
                    .stream()
                    .sorted()
                    .map(components::get)
                    .map(List::<MantleComponent>copyOf)
                    .filter(pass -> !pass.isEmpty())
                    .toList();

            MantlePass[] built = new MantlePass[passes.size()];
            int downstreamBlockRadius = 0;
            for (int i = passes.size() - 1; i >= 0; i--) {
                List<MantleComponent> pass = passes.get(i);
                int passBlockRadius = pass.stream()
                        .filter(MantleComponent::isEnabled)
                        .mapToInt(MantleComponent::getOutputRadius)
                        .max()
                        .orElse(0);
                int passInputRadius = pass.stream()
                        .filter(MantleComponent::isEnabled)
                        .mapToInt(MantleComponent::getInputRadius)
                        .max()
                        .orElse(0);
                int invocationRadius = downstreamBlockRadius + passBlockRadius;
                built[i] = new MantlePass(pass, Math.ceilDiv(invocationRadius, 16), downstreamBlockRadius);
                downstreamBlockRadius = invocationRadius + passInputRadius;
            }

            return List.of(built);
        });
    }

    @Override
    public Map<MantleFlag, MantleComponent> getRegisteredComponents() {
        return Collections.unmodifiableMap(registeredComponents);
    }

    @Override
    public boolean registerComponent(MantleComponent c) {
        if (registeredComponents.putIfAbsent(c.getFlag(), c) != null) return false;
        c.setEnabled(!getDisabledFlags().contains(c.getFlag()));
        components.computeIfAbsent(c.getPriority(), k -> new KList<>()).add(c);
        componentsCache.reset();
        return true;
    }

    @Override
    public KList<MantleFlag> getComponentFlags() {
        return new KList<>(registeredComponents.keySet());
    }

    @Override
    public void hotload() {
        runtimeData.set(Objects.requireNonNull(engine.getData(), "Iris mantle data"));
        disabledFlags.reset();
        for (MantleComponent component : registeredComponents.values()) {
            component.hotload();
            component.setEnabled(!getDisabledFlags().contains(component.getFlag()));
        }
        componentsCache.reset();
    }

    private Set<MantleFlag> getDisabledFlags() {
        return disabledFlags.aquire(() -> {
            KList<MantleFlag> disabled = new KList<>();
            disabled.addAll(getDimension().getDisabledComponents());
            if (!getDimension().isCarvingEnabled()) {
                disabled.addIfMissing(ReservedFlag.CARVED);
            }
            if (disabled.contains(ReservedFlag.CARVED)
                    || !isHydrologyEnabled(getDimension())) {
                disabled.addIfMissing(ReservedFlag.RIVER_HYDROLOGY);
            }
            return Set.copyOf(disabled);
        });
    }

    static boolean isHydrologyEnabled(IrisDimension dimension) {
        return MantleHydrologyComponent.isEnabledFor(dimension);
    }

    @Override
    public MantleObjectComponent getObjectComponent() {
        return object;
    }

    static Path legacyStorageDirectory(EngineTarget target) {
        EngineTarget requiredTarget = Objects.requireNonNull(target, "Iris engine target");
        return normalizeStorageDirectory(requiredTarget.getWorld().worldFolder().toPath().resolve(STORAGE_FOLDER_NAME));
    }

    static Path normalizeStorageDirectory(Path storageDirectory) {
        return Objects.requireNonNull(storageDirectory, "mantle storage directory").toAbsolutePath().normalize();
    }

    private boolean isCovered(int x, int z, MissingRealChunk missingChunk) {
        int radius = Math.max(getRadius(), getRealRadius());
        if (missingChunk != null && missingChunk.known
                && Math.abs((long) missingChunk.x - x) <= radius
                && Math.abs((long) missingChunk.z - z) <= radius) {
            if (!getMantle().hasFlag(missingChunk.x, missingChunk.z, MantleFlag.REAL)) {
                return false;
            }
            missingChunk.known = false;
        }
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int chunkX = x + offsetX;
                int chunkZ = z + offsetZ;
                if (!getMantle().hasFlag(chunkX, chunkZ, MantleFlag.REAL)) {
                    if (missingChunk != null) {
                        missingChunk.x = chunkX;
                        missingChunk.z = chunkZ;
                        missingChunk.known = true;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public static Mantle<Matter> createMantle(
            Engine engine,
            Path storageDirectory,
            Supplier<IrisData> dataSupplier
    ) {
        IrisMatterSupport.ensureRegistered();
        File dataFolder = storageDirectory.toFile();
        int worldHeight = engine.getTarget().getHeight();
        MantleDataAdapter<Matter> adapter = createDataAdapter(dataSupplier);
        MantleHooks hooks = createHooks(EnginePanic.scoped("world " + engine.getWorld().name()));
        art.arcane.volmlib.util.mantle.Mantle.RegionIO<TectonicPlate<Matter>> regionIO =
                createRegionIO(dataFolder, worldHeight, adapter, hooks);
        return new Mantle<>(
                dataFolder,
                worldHeight,
                Short.MAX_VALUE,
                new HyperLock(),
                MultiBurst.ioBurst,
                regionIO,
                adapter,
                hooks
        );
    }

    public static MantleDataAdapter<Matter> createRuntimeDataAdapter(IrisData data) {
        return createDataAdapter(() -> data);
    }

    public static MantleHooks createRuntimeHooks() {
        return createHooks(EnginePanic.scoped("runtime mantle"));
    }

    private static MantleDataAdapter<Matter> createDataAdapter(Supplier<IrisData> dataSupplier) {
        return new MantleDataAdapter<>() {
            @Override
            public Matter createSection() {
                return new IrisMatter(16, 16, 16);
            }

            @Override
            public Matter readSection(art.arcane.volmlib.util.io.CountingDataInputStream din) throws IOException {
                IrisData data = Objects.requireNonNull(dataSupplier.get(), "Iris mantle data is unavailable.");
                try (IrisMatterContext.Scope scope = IrisMatterContext.open(data)) {
                    return Matter.readDin(din);
                }
            }

            @Override
            public void writeSection(Matter section, java.io.DataOutputStream dos) throws IOException {
                section.writeDos(dos);
            }

            @Override
            public void trimSection(Matter section) {
                section.trimSlices();
            }

            @Override
            public boolean isSectionEmpty(Matter section) {
                return section.getSliceMap().isEmpty();
            }

            @Override
            public Class<?> classifyValue(Object value) {
                if (value instanceof PlatformBlockState) {
                    return PlatformBlockState.class;
                }
                return IrisPlatforms.get().classifyMantleValue(value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> void set(Matter section, int x, int y, int z, Class<?> type, T value) {
                MatterSlice<T> slice = (MatterSlice<T>) section.slice(type);
                slice.set(x, y, z, value);
            }

            @Override
            public <T> void remove(Matter section, int x, int y, int z, Class<T> type) {
                MatterSlice<T> slice = section.slice(type);
                slice.set(x, y, z, null);
            }

            @Override
            public <T> T get(Matter section, int x, int y, int z, Class<T> type) {
                MatterSlice<T> slice = section.slice(type);
                return slice.get(x, y, z);
            }

            @Override
            public <T> void iterate(Matter section, Class<T> type, art.arcane.volmlib.util.function.Consumer4<Integer, Integer, Integer, T> iterator) {
                MatterSlice<T> slice = section.getSlice(type);
                if (slice != null) {
                    slice.iterateSync(iterator);
                }
            }

            @Override
            public boolean hasSlice(Matter section, Class<?> type) {
                return section.hasSlice(type);
            }

            @Override
            public void deleteSlice(Matter section, Class<?> type) {
                section.deleteSlice(type);
            }
        };
    }

    private static MantleHooks createHooks(EnginePanic.Diagnostics panic) {
        return new MantleHooks() {
            @Override
            public void onBeforeReadSection(int index) {
                panic.add("read.section", "Section[" + index + "]");
            }

            @Override
            public void onReadSectionFailure(int index,
                                             long start,
                                             long end,
                                             art.arcane.volmlib.util.io.CountingDataInputStream din,
                                             IOException error) {
                IrisLogging.error("Failed to read chunk section, skipping it.");
                panic.add("read.byte.range", start + " " + end);
                panic.add("read.byte.current", din.count() + "");
                IrisLogging.reportError(error);
                panic.panic();
                TectonicPlate.addError();
            }

            @Override
            public void onBeforeReadChunk(int index) {
                panic.add("read-chunk", "Chunk[" + index + "]");
            }

            @Override
            public void onAfterReadChunk(int index) {
                panic.saveLast();
            }

            @Override
            public void onReadChunkFailure(int index,
                                           long start,
                                           long end,
                                           art.arcane.volmlib.util.io.CountingDataInputStream din,
                                           Throwable error) {
                IrisLogging.error("Failed to read chunk, creating a new chunk instead.");
                panic.add("read.byte.range", start + " " + end);
                panic.add("read.byte.current", din.count() + "");
                IrisLogging.reportError(error);
                panic.panic();
            }

            @Override
            public boolean shouldRetainSlice(Class<?> sliceType) {
                return WorldMaintenance.isRetainingMantleDataForSlice(sliceType.getCanonicalName());
            }

            @Override
            public String formatDuration(double millis) {
                return Form.duration(millis, 0);
            }

            @Override
            public void onDebug(String message) {
                IrisLogging.debug(message);
            }

            @Override
            public void onWarn(String message) {
                IrisLogging.warn(message);
            }

            @Override
            public void onError(Throwable throwable) {
                IrisLogging.reportError(throwable);
            }
        };
    }

    private static art.arcane.volmlib.util.mantle.Mantle.RegionIO<TectonicPlate<Matter>> createRegionIO(File root,
                                                                                                          int worldHeight,
                                                                                                          MantleDataAdapter<Matter> adapter,
                                                                                                          MantleHooks hooks) {
        IOWorker<TectonicPlate<Matter>> worker = new IOWorker<>(
                root,
                new Lz4IOWorkerCodecSupport(),
                128,
                (name, millis) -> {
                    String threadName = Thread.currentThread().getName();
                    String message = "Acquired Channel for " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(millis, 2)
                            + C.GRAY + " thread=" + threadName;
                    if (millis >= 1000L) {
                        IrisLogging.warn(message);
                    } else {
                        IrisLogging.debug(message);
                    }
                }
        );

        return new art.arcane.volmlib.util.mantle.Mantle.RegionIO<>() {
            @Override
            public TectonicPlate<Matter> read(String name) throws Exception {
                PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
                try {
                    return worker.read(name, (regionName, in) ->
                            TectonicPlate.read(worldHeight, in, regionName.startsWith("pv."), adapter, hooks));
                } finally {
                    // hasError() is a consuming ThreadLocal read: evaluate exactly once. The
                    // dump must never throw out of this finally — that would replace the
                    // successfully parsed plate with the dump failure, and the caller would
                    // overwrite 1024 good chunks with a fresh empty plate.
                    boolean errored = TectonicPlate.hasError();
                    if (errored && IrisSettings.get().getGeneral().isDumpMantleOnError()) {
                        try {
                            File dump = IrisPlatforms.get().dataFile("dump", name + ".bin");
                            worker.dumpDecoded(name, dump.toPath());
                        } catch (Throwable dumpFailure) {
                            IrisLogging.warn("Failed to dump mantle region " + name + " for diagnostics");
                            IrisLogging.reportError(dumpFailure);
                        }
                    }
                    IrisLogging.debug("Read Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(stopwatch.getMilliseconds(), 2));
                }
            }

            @Override
            public void write(String name, TectonicPlate<Matter> region) throws Exception {
                PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
                worker.write(name, "iris", ".bin", region, TectonicPlate::write);
                IrisLogging.debug("Saved Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(stopwatch.getMilliseconds(), 2));
            }

            @Override
            public void close() throws Exception {
                worker.close();
            }
        };
    }

    private static final class MissingRealChunk {
        private int x;
        private int z;
        private boolean known;
    }
}
