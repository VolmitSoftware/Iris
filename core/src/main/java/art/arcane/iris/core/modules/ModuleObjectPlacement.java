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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.FeatureSettings;
import com.volmit.host.PlacementOutcome;
import com.volmit.host.LoadedPack;
import com.volmit.host.PlacementRunner;
import com.volmit.host.ResolvedGraph;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;
import com.volmit.world.TerrainSampler;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Places an Iris object by running the pack's placement graph, Iris's built-in placement stages included,
 * with IrisApi's {@link PlacementRunner}. The writes are buffered and reach the placer only when no stage
 * rejects the placement.
 * <p>
 * Each stage draws from its own stream of one seed per placement, from the world seed and the placement's
 * identity: the object and the requested position. Without an engine, such as for a studio paste, the placement
 * runs in a {@link ToolWorld} of its own, whose seed is one draw from the caller's random.
 */
public final class ModuleObjectPlacement {
    private static final String UNNAMED_OBJECT = "unnamed";

    private ModuleObjectPlacement() {
    }

    /**
     * @param requestedY the height to place at, or a negative value for the placement to sample it
     * @param listener hears each of the object's own blocks once the placement is written; null for none
     * @param cave the cave the object is placed in, or null outside caves
     */
    public static ObjectPlacementResult place(IrisObject object, int x, int requestedY, int z, IObjectPlacer placer,
                                              IrisObjectPlacement config, RNG rng,
                                              BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener,
                                              CarveResult cave, IrisData data) {
        Objects.requireNonNull(placer, "Object placer is required.");
        Objects.requireNonNull(config, "Object placement config is required.");
        Objects.requireNonNull(rng, "Object placement RNG is required.");
        Objects.requireNonNull(data, "Object placement data is required.");
        LoadedPack pack = data.getModules().pack();
        ResolvedGraph graph = pack.placement();
        GeneratedObject geometry = geometryOf(object, config);
        BlockBox area = PlacementArea.around(object, geometry, config, x, z, graph.reach().blocks());
        PlatformBlockStates states = new PlatformBlockStates(ModuleObjectPlacement::platformState);
        ObjectPlacerEdit edit = new ObjectPlacerEdit(placer, area, states, listener, () -> mantle(placer.getEngine(), area, pack));
        IrisPlacement placement = new IrisPlacement(object, config, placer, cave, x, requestedY, z, data, states, edit);
        PackResourceIds ids = new PackResourceIds(data.getDataFolder());
        String objectKey = keyOf(object);
        Engine engine = placer.getEngine();
        long worldSeed = engine != null ? engine.getSeedManager().getSeed() : rng.nextLong();
        ResourceId source = ids.of("object", objectKey);
        PlacementRequest request = new PlacementRequest(ids.of("placement/" + objectKey + "/" + x + "/" + requestedY + "/" + z),
                geometry, new BlockPosition(x, requestedY, z));
        long seed = ModuleSeeds.placement(worldSeed, objectKey, x, requestedY, z);
        StageMetrics metrics = new StageMetrics(engine != null ? engine.getMetrics() : null);
        try (ToolWorld toolWorld = engine == null ? new ToolWorld(worldSeed, data.getPackResources(), new PackDeclaredSettings(data)) : null) {
            IrisPlacementInputs inputs = new IrisPlacementInputs(terrain(engine, ids, area),
                    new PlacementModuleSettings(worldSettings(engine, ids), config.getModuleSettings(), source),
                    new PlacerBlockView(placer, area, states), engine != null ? IrisPlacementInputs.worldOf(engine) : () -> toolWorld,
                    placement);
            PlacementOutcome outcome = new PlacementRunner(graph)
                    .place(request, edit, inputs, seed, config.getModuleSettings().placement(source), metrics);
            return outcome.isPlaced()
                    ? ObjectPlacementResult.placed(placement.anchorY())
                    : ObjectPlacementResult.rejected(outcome.rejection().orElseThrow());
        }
    }

    /** Smart boring adds the object's bored air, so it has to happen before the geometry is read. */
    private static GeneratedObject geometryOf(IrisObject object, IrisObjectPlacement config) {
        if (config.isSmartBore()) {
            IrisObjectShaping.ensureSmartBored(object);
        }
        return object.getGeometry();
    }

    private static TerrainSampler terrain(Engine engine, PackResourceIds ids, BlockBox area) {
        if (engine == null) {
            return IrisPlacementInputs.noTerrain(area);
        }
        return new IrisTerrainSampler(engine, ids, area.minX(), area.minZ(), area.maxX(), area.maxZ());
    }

    private static PlacementMantle mantle(Engine engine, BlockBox area, LoadedPack pack) {
        if (engine == null) {
            throw new UnsupportedOperationException("This placement runs without an Iris engine, so it has no mantle");
        }
        return new PlacementMantle(engine, area, pack.catalog().mantle());
    }

    private static FeatureSettings worldSettings(Engine engine, PackResourceIds ids) {
        return engine == null ? PlacementModuleSettings.noWorld() : new ChunkModuleSettings(engine, ids);
    }

    /** Resolved by the platform bound now, so a rebound platform never answers with another's states. */
    private static PlatformBlockState platformState(String key) {
        return IrisPlatforms.get().registries().block(key);
    }

    private static String keyOf(IrisObject object) {
        return object.getLoadKey() == null ? UNNAMED_OBJECT : object.getLoadKey();
    }
}
