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

package art.arcane.iris.engine;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.EnginePanic;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.components.MantleCarvingComponent;
import art.arcane.iris.engine.mantle.components.MantleFluidBodyComponent;
import art.arcane.iris.engine.mantle.components.MantleObjectComponent;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
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
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.iris.util.common.parallel.HyperLock;
import art.arcane.iris.util.common.parallel.MultiBurst;
import lombok.*;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(exclude = "engine")
@ToString(exclude = "engine")
public class IrisEngineMantle implements EngineMantle {
    private final Engine engine;
    private final Mantle<Matter> mantle;
    @Getter(AccessLevel.NONE)
    private final KMap<Integer, KList<MantleComponent>> components;
    private final KMap<MantleFlag, MantleComponent> registeredComponents = new KMap<>();
    private final AtomicCache<List<Pair<List<MantleComponent>, Integer>>> componentsCache = new AtomicCache<>();
    private final AtomicCache<Set<MantleFlag>> disabledFlags = new AtomicCache<>();
    private final MantleObjectComponent object;

    public IrisEngineMantle(Engine engine) {
        this.engine = engine;
        this.mantle = createMantle(engine);
        components = new KMap<>();
        registerComponent(new MantleCarvingComponent(this));
        registerComponent(new MantleFluidBodyComponent(this));
        object = new MantleObjectComponent(this);
        registerComponent(object);
    }

    @Override
    public int getRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getFirst().getB();
    }

    @Override
    public int getRealRadius() {
        if (components.isEmpty()) return 0;
        return getComponents().getLast().getB();
    }

    @Override
    public List<Pair<List<MantleComponent>, Integer>> getComponents() {
        return componentsCache.aquire(() -> {
            var list = components.keySet()
                    .stream()
                    .sorted()
                    .map(components::get)
                    .map(components -> {
                        int radius = components.stream()
                                .filter(MantleComponent::isEnabled)
                                .mapToInt(MantleComponent::getRadius)
                                .max()
                                .orElse(0);
                        return new Pair<>(List.copyOf(components), radius);
                    })
                    .filter(pair -> !pair.getA().isEmpty())
                    .toList();

            int radius = 0;
            for (var pair : list.reversed()) {
                radius += pair.getB();
                pair.setB(Math.ceilDiv(radius, 16));
            }

            return list;
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
        disabledFlags.reset();
        for (var component : registeredComponents.values()) {
            component.hotload();
            component.setEnabled(!getDisabledFlags().contains(component.getFlag()));
        }
        componentsCache.reset();
    }

    private Set<MantleFlag> getDisabledFlags() {
        return disabledFlags.aquire(() -> Set.copyOf(getDimension().getDisabledComponents()));
    }

    @Override
    public MantleObjectComponent getObjectComponent() {
        return object;
    }

    private static Mantle<Matter> createMantle(Engine engine) {
        IrisMatterSupport.ensureRegistered();
        File dataFolder = new File(engine.getWorld().worldFolder(), "mantle");
        int worldHeight = engine.getTarget().getHeight();
        MantleDataAdapter<Matter> adapter = createRuntimeDataAdapter();
        MantleHooks hooks = createRuntimeHooks();
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

    public static MantleDataAdapter<Matter> createRuntimeDataAdapter() {
        return createDataAdapter();
    }

    public static MantleHooks createRuntimeHooks() {
        return createHooks();
    }

    private static MantleDataAdapter<Matter> createDataAdapter() {
        return new MantleDataAdapter<>() {
            @Override
            public Matter createSection() {
                return new IrisMatter(16, 16, 16);
            }

            @Override
            public Matter readSection(art.arcane.volmlib.util.io.CountingDataInputStream din) throws IOException {
                return Matter.readDin(din);
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
                if (value instanceof World) {
                    return World.class;
                }

                if (value instanceof BlockData) {
                    return BlockData.class;
                }

                if (value instanceof Entity) {
                    return Entity.class;
                }

                return value.getClass();
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

    private static MantleHooks createHooks() {
        return new MantleHooks() {
            @Override
            public void onBeforeReadSection(int index) {
                Iris.addPanic("read.section", "Section[" + index + "]");
            }

            @Override
            public void onReadSectionFailure(int index,
                                             long start,
                                             long end,
                                             art.arcane.volmlib.util.io.CountingDataInputStream din,
                                             IOException error) {
                Iris.error("Failed to read chunk section, skipping it.");
                Iris.addPanic("read.byte.range", start + " " + end);
                Iris.addPanic("read.byte.current", din.count() + "");
                Iris.reportError(error);
                error.printStackTrace();
                Iris.panic();
                TectonicPlate.addError();
            }

            @Override
            public void onBeforeReadChunk(int index) {
                Iris.addPanic("read-chunk", "Chunk[" + index + "]");
            }

            @Override
            public void onAfterReadChunk(int index) {
                EnginePanic.saveLast();
            }

            @Override
            public void onReadChunkFailure(int index,
                                           long start,
                                           long end,
                                           art.arcane.volmlib.util.io.CountingDataInputStream din,
                                           Throwable error) {
                Iris.error("Failed to read chunk, creating a new chunk instead.");
                Iris.addPanic("read.byte.range", start + " " + end);
                Iris.addPanic("read.byte.current", din.count() + "");
                Iris.reportError(error);
                error.printStackTrace();
                Iris.panic();
            }

            @Override
            public boolean shouldRetainSlice(Class<?> sliceType) {
                return IrisToolbelt.isRetainingMantleDataForSlice(sliceType.getCanonicalName());
            }

            @Override
            public String formatDuration(double millis) {
                return Form.duration(millis, 0);
            }

            @Override
            public void onDebug(String message) {
                Iris.debug(message);
            }

            @Override
            public void onWarn(String message) {
                Iris.warn(message);
            }

            @Override
            public void onError(Throwable throwable) {
                Iris.reportError(throwable);
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
                        Iris.warn(message);
                    } else {
                        Iris.debug(message);
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
                    if (TectonicPlate.hasError() && IrisSettings.get().getGeneral().isDumpMantleOnError()) {
                        File dump = Iris.instance.getDataFolder("dump", name + ".bin");
                        worker.dumpDecoded(name, dump.toPath());
                    } else {
                        Iris.debug("Read Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(stopwatch.getMilliseconds(), 2));
                    }
                }
            }

            @Override
            public void write(String name, TectonicPlate<Matter> region) throws Exception {
                PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
                worker.write(name, "iris", ".bin", region, TectonicPlate::write);
                Iris.debug("Saved Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(stopwatch.getMilliseconds(), 2));
            }

            @Override
            public void close() throws Exception {
                worker.close();
            }
        };
    }
}
