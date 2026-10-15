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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.objects.ObjectChunk;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ObjectSink;
import art.arcane.iris.builtin.objects.ObjectSize;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.NoiseType;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What {@code iris:objects} asks of Iris, answered from the world's engine: the pack's objects, how Iris scales and places
 * them, and the layout around a world position.
 */
final class IrisObjectHost implements ObjectHost {
    private final Engine engine;
    private final ModuleLog log;
    private volatile PackResourceIds ids;
    private final Map<ProceduralObjectSettings, List<ProceduralPlacement>> procedural = new ConcurrentHashMap<>();
    private volatile Map<ResourceId, String> biomeKeys;
    private volatile Map<ResourceId, String> regionKeys;

    IrisObjectHost(Engine engine, ModuleLog log) {
        this.engine = engine;
        this.log = log;
    }

    private PackResourceIds ids() {
        PackResourceIds known = ids;
        if (known == null) {
            known = new PackResourceIds(engine.getData().getDataFolder());
            ids = known;
        }
        return known;
    }

    @Override
    public boolean debugging() {
        return IrisSettings.get().getGeneral().isDebug();
    }

    @Override
    public int caveLavaHeight() {
        return engine.getDimension().getCaveLavaHeight();
    }

    @Override
    public int fluidHeight() {
        return engine.getDimension().getFluidHeight();
    }

    @Override
    public FeatureSettings columnSettings() {
        return new ChunkModuleSettings(engine, ids());
    }

    @Override
    public RNG chunkRandom(int chunkX, int chunkZ) {
        long seed = CacheKey.key(chunkX, chunkZ) + engine.getSeedManager().getMantle();
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(chunkX, chunkZ)));
    }

    @Override
    public ObjectChunk chunk() {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        return new IrisObjectChunk(chunk.engine(), chunk.writer(), log);
    }

    @Override
    public Optional<PlaceableObject> object(String key) {
        return Optional.ofNullable(engine.getData().getObjectLoader().load(key));
    }

    @Override
    public Optional<PlaceableObject> pickScaled(ObjectPlacement placement, RNG rng) {
        IrisObjectPlacement host = hostPlacement(placement);
        IrisObject picked = host.getObject(engine.getComplex(), rng);
        return Optional.ofNullable(host.getScale().get(rng, picked, engine));
    }

    @Override
    public int density(ObjectPlacement placement, RNG rng, double x, double z) {
        return hostPlacement(placement).getDensity(rng, x, z, engine.getData());
    }

    @Override
    public Optional<ObjectSize> sizeOf(String objectKey) {
        File file = engine.getData().getObjectLoader().findFile(objectKey);
        if (file == null) {
            return Optional.empty();
        }
        try {
            var size = IrisObject.sampleSize(file);
            return Optional.of(new ObjectSize(size.getBlockX(), size.getBlockY(), size.getBlockZ()));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void warm(ObjectPlacement placement, RNG rng) {
        IrisObjectPlacement host = hostPlacement(placement);
        host.getSurfaceWarp(rng, engine.getData(), engine);
        host.getDensity(rng, 0, 0, engine.getData());
    }

    @Override
    public List<ProceduralPlacement> procedural(ProceduralObjectSettings settings) {
        return procedural.computeIfAbsent(settings, declared -> {
            List<ProceduralPlacement> placements = new ArrayList<>();
            for (IrisProceduralPlacement placement : ObjectPlacements.procedural(declared).getAllPlacements()) {
                placements.add(new IrisProceduralPlacementView(placement, engine.getData()));
            }
            return List.copyOf(placements);
        });
    }

    @Override
    public Optional<UpperObjects> upperObjects(int worldX, int worldZ) {
        UpperDimensionContext upper = engine.getUpperContext();
        if (upper == null) {
            return Optional.empty();
        }
        IrisBiome biome = upper.getUpperBiome(worldX, worldZ);
        IrisRegion region = upper.getUpperRegion(worldX, worldZ);
        return Optional.of(new UpperObjects(
                biome == null ? null : biome.getModuleSettings().find(ObjectSettings.class).orElse(null),
                region == null ? null : region.getModuleSettings().find(ObjectSettings.class).orElse(null),
                biome == null ? null : biome.getLoadKey(), region == null ? null : region.getLoadKey()));
    }

    @Override
    public int placeInto(PlaceableObject object, int x, int y, int z, ObjectPlacement placement, ObjectSink sink, RNG rng) {
        IrisData data = engine.getData();
        return ((IrisObject) object).place(x, y, z, new SinkPlacer(engine, sink), hostPlacement(placement), rng, data);
    }

    @Override
    public ResourceId regionAt(int worldX, int worldZ) {
        return ids().of(engine.getRegion(worldX, worldZ).getLoadKey());
    }

    @Override
    public ResourceId surfaceBiomeAt(int worldX, int worldZ) {
        return ids().of(engine.getSurfaceBiome(worldX, worldZ).getLoadKey());
    }

    @Override
    public ResourceId biomeAt(int worldX, int y, int worldZ) {
        return ids().of(engine.getBiome(worldX, y, worldZ).getLoadKey());
    }

    @Override
    public <F extends IrisFeature> Optional<F> biomeDeclaration(ResourceId biome, Class<F> type) {
        String key = biomeKeys().get(biome);
        if (key == null) {
            return Optional.empty();
        }
        IrisBiome loaded = engine.getData().getBiomeLoader().load(key);
        return loaded == null ? Optional.empty() : loaded.getModuleSettings().find(type);
    }

    @Override
    public <F extends IrisFeature> Optional<F> regionDeclaration(ResourceId region, Class<F> type) {
        String key = regionKeys().get(region);
        if (key == null) {
            return Optional.empty();
        }
        IrisRegion loaded = engine.getData().getRegionLoader().load(key);
        return loaded == null ? Optional.empty() : loaded.getModuleSettings().find(type);
    }

    @Override
    public <F extends IrisFeature> List<Declaration<F>> declarations(Class<F> type) {
        List<Declaration<F>> found = new ArrayList<>();
        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            addDeclaration(found, Target.REGION, region, type);
        }
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            addDeclaration(found, Target.BIOME, biome, type);
        }
        return found;
    }

    private static <F extends IrisFeature> void addDeclaration(List<Declaration<F>> found, Target level, IrisRegistrant owner, Class<F> type) {
        if (owner == null) {
            return;
        }
        owner.getModuleSettings().find(type).ifPresent(settings -> found.add(new Declaration<>(level, owner.getLoadKey(), settings)));
    }

    private IrisObjectPlacement hostPlacement(ObjectPlacement placement) {
        return ObjectPlacements.host(placement, engine.getData());
    }

    private Map<ResourceId, String> biomeKeys() {
        Map<ResourceId, String> keys = biomeKeys;
        if (keys == null) {
            keys = indexOf(engine.getData().getBiomeLoader());
            biomeKeys = keys;
        }
        return keys;
    }

    private Map<ResourceId, String> regionKeys() {
        Map<ResourceId, String> keys = regionKeys;
        if (keys == null) {
            keys = indexOf(engine.getData().getRegionLoader());
            regionKeys = keys;
        }
        return keys;
    }

    private Map<ResourceId, String> indexOf(ResourceLoader<?> loader) {
        Map<ResourceId, String> index = new HashMap<>();
        for (String key : loader.getPossibleKeys()) {
            index.putIfAbsent(ids().of(key), key);
        }
        return Map.copyOf(index);
    }
}
