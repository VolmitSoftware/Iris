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

import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.ObjectChunk;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSink;
import art.arcane.iris.builtin.objects.ObjectSize;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.NoiseType;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.Level;
import com.volmit.generation.InvocationContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What {@code iris:objects} asks of Iris's object system, answered from the world's engine: the pack's objects, how Iris
 * scales, picks and places them, and the loot of what it placed.
 */
final class IrisObjectHost implements ObjectHost {
    private final Engine engine;
    private final ModuleLog log;
    private volatile PackResourceIds ids;
    private final Map<ProceduralPlacement, IrisObjectPlacement> proceduralOptions = new ConcurrentHashMap<>();

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
    public RNG chunkRandom(int chunkX, int chunkZ) {
        long seed = CacheKey.key(chunkX, chunkZ) + engine.getSeedManager().getMantle();
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(chunkX, chunkZ)));
    }

    @Override
    public ObjectChunk chunk(InvocationContext context) {
        IrisMantleChunk chunk = context.extension(IrisMantleChunk.class).orElseThrow();
        return new IrisObjectChunk(chunk.engine(), chunk.writer(), log, this::options);
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
    public ObjectBuilder builder() {
        return new IrisObjectBuilder(engine.getData());
    }

    /** The options {@code placement} is scattered with, as Iris's own placement, made once per placement. */
    IrisObjectPlacement options(ProceduralPlacement placement) {
        return proceduralOptions.computeIfAbsent(placement, ProceduralOptions::of);
    }

    @Override
    public int placeInto(PlaceableObject object, int x, int y, int z, ObjectPlacement placement, ObjectSink sink, RNG rng) {
        IrisData data = engine.getData();
        return ((IrisObject) object).place(x, y, z, new SinkPlacer(engine, sink), hostPlacement(placement), rng, data);
    }

    @Override
    public Optional<ObjectLoots> placedLoot(int x, int y, int z) {
        PlacedObject placed = engine.getObjectPlacement(x, y, z);
        IrisObjectPlacement placement = placed == null ? null : placed.getPlacement();
        if (placement == null) {
            return Optional.empty();
        }
        return Optional.of(new ObjectLoots(placement.getLoot(), placement.getVanillaLoot(), placement.isOverrideGlobalLoot()));
    }

    @Override
    public <F extends IrisFeature> List<Declaration<F>> declarations(Class<F> type) {
        List<Declaration<F>> found = new ArrayList<>();
        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            addDeclaration(found, Level.REGION, region, type);
        }
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            addDeclaration(found, Level.BIOME, biome, type);
        }
        return found;
    }

    private <F extends IrisFeature> void addDeclaration(List<Declaration<F>> found, Level level, IrisRegistrant owner, Class<F> type) {
        if (owner == null) {
            return;
        }
        owner.getModuleSettings().find(type).ifPresent(settings -> found.add(new Declaration<>(level, ids().of(owner.getLoadKey()), settings)));
    }

    private IrisObjectPlacement hostPlacement(ObjectPlacement placement) {
        return ObjectPlacements.host(placement, engine.getData());
    }
}
