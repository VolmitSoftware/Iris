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
import com.volmit.config.FeatureSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.host.LoadedPack;
import com.volmit.host.PlacementRunner;
import com.volmit.host.ResolvedGraph;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementRun;
import com.volmit.placement.Placements;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.function.Supplier;

/**
 * Runs the pack's placement graph for one world with IrisApi's {@link PlacementRunner}: the area widened by the graph's
 * reach, the writes buffered in a {@link TerrainEdit} over the run's terrain, and the world's terrain, settings and
 * blocks as the stages' inputs. Without an engine, such as for a studio paste, the stages have no terrain to sample and
 * no mantle.
 * <p>
 * The placement-level settings the stages read are those the run's {@link PlacementRun#settings()} declares, and the run's
 * name names their source.
 */
final class IrisPlacements implements Placements {
    private static final String OBJECT_KIND = "object";

    private final IrisData data;
    private final Engine engine;
    private final Supplier<PackWorld> world;

    /** @param engine the engine of the world placed in; null for a world Iris does not generate */
    IrisPlacements(IrisData data, Engine engine, Supplier<PackWorld> world) {
        this.data = data;
        this.engine = engine;
        this.world = world;
    }

    @Override
    public PlacementOutcome place(PlacementRun run) {
        LoadedPack pack = data.getModules().pack();
        ResolvedGraph graph = pack.placement();
        BlockBox area = widened(run.area(), graph.reach().blocks());
        PackResourceIds ids = new PackResourceIds(data.getDataFolder());
        ResourceId source = ids.of(OBJECT_KIND, run.name());
        ModuleSettings placementSettings = run.settings()
                .map(json -> data.getModules().settings().decodePlacement(json))
                .orElse(ModuleSettings.NONE);
        TerrainEdit edit = new TerrainEdit(run.terrain(), area, () -> mantle(area, pack));
        IrisPlacementInputs inputs = new IrisPlacementInputs(terrain(ids, area),
                new PlacementModuleSettings(worldSettings(ids), placementSettings, source),
                new TerrainBlockView(run.terrain(), area), world, run.terrain(), run.input());
        StageMetrics metrics = new StageMetrics(engine != null ? engine.getMetrics() : null);
        return new PlacementRunner(graph).place(run.request(), edit, inputs, run.seed(), placementSettings.placement(source), metrics);
    }

    private static BlockBox widened(BlockBox area, int blocks) {
        return new BlockBox(area.minX() - blocks, area.minY(), area.minZ() - blocks, area.maxX() + blocks, area.maxY(),
                area.maxZ() + blocks);
    }

    private TerrainSampler terrain(PackResourceIds ids, BlockBox area) {
        if (engine == null) {
            return IrisPlacementInputs.noTerrain(area);
        }
        return new IrisTerrainSampler(engine, ids, area.minX(), area.minZ(), area.maxX(), area.maxZ());
    }

    private PlacementMantle mantle(BlockBox area, LoadedPack pack) {
        if (engine == null) {
            throw new UnsupportedOperationException("This placement runs without an Iris engine, so it has no mantle");
        }
        return new PlacementMantle(engine, area, pack.catalog().mantle());
    }

    private FeatureSettings worldSettings(PackResourceIds ids) {
        return engine == null ? PlacementModuleSettings.noWorld() : new ChunkModuleSettings(engine, ids);
    }
}
