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

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.geometry.BlockPosition;
import com.volmit.placement.PlacementOutcome;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Places an Iris object for Iris's own callers (structures, commands, pastes) through the pack's placement graph, as
 * {@code PackWorld.placements()} places a module's. The writes reach the placer only when no stage rejects the
 * placement. Without an engine, such as for a studio paste, the placement runs in a {@link ToolWorld} of its own, whose
 * seed is one draw from the caller's random.
 */
public final class ModuleObjectPlacement {
    private ModuleObjectPlacement() {
    }

    /**
     * Places the object at {@code requestedY}, which may be below zero in a dimension that reaches down there.
     *
     * @param listener hears each of the object's own blocks once the placement is written; null for none
     * @param cave the cave the object is placed in, or null outside caves
     */
    public static ObjectPlacementResult place(IrisObject object, int x, int requestedY, int z, IObjectPlacer placer,
                                              ObjectPlacement config, RNG rng,
                                              BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener,
                                              CarveResult cave, IrisData data) {
        ObjectRun run = cave == null
                ? ObjectRun.of(object, config, x, requestedY, z)
                : ObjectRun.inCave(object, config, x, requestedY, z, caveOf(cave));
        return place(run, placer, config, rng, listener, data);
    }

    /** As {@link #place}, at the height the placement samples from the terrain, or from {@code cave}. */
    public static ObjectPlacementResult placeAutomatic(IrisObject object, int x, int z, IObjectPlacer placer,
                                                       ObjectPlacement config, RNG rng,
                                                       BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener,
                                                       CarveResult cave, IrisData data) {
        ObjectRun run = cave == null
                ? ObjectRun.automatic(object, config, x, z)
                : ObjectRun.automaticInCave(object, config, x, z, caveOf(cave));
        return place(run, placer, config, rng, listener, data);
    }

    private static ObjectRun.Cave caveOf(CarveResult cave) {
        return new ObjectRun.Cave(cave.getSurface(), cave.getHeight());
    }

    private static ObjectPlacementResult place(ObjectRun run, IObjectPlacer placer, ObjectPlacement config, RNG rng,
                                               BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener,
                                               IrisData data) {
        Objects.requireNonNull(placer, "Object placer is required.");
        Objects.requireNonNull(config, "Object placement config is required.");
        Objects.requireNonNull(rng, "Object placement RNG is required.");
        Objects.requireNonNull(data, "Object placement data is required.");
        PlacerTerrain terrain = listener == null ? PlacerTerrain.of(placer, data) : PlacerTerrain.rememberingWrites(placer, data);
        PlacementOutcome outcome = placeIn(run, terrain, placer.getEngine(), rng, data);
        if (!outcome.isPlaced()) {
            return ObjectPlacementResult.rejected(outcome.rejection().orElseThrow());
        }
        reportObjectBlocks(run, terrain, listener);
        int anchorY = run.anchorY().orElseThrow(() -> new IllegalStateException(
                "The placement graph placed " + run.object().name() + " without anchoring it"));
        return ObjectPlacementResult.placed(anchorY);
    }

    private static PlacementOutcome placeIn(ObjectRun run, PlacerTerrain terrain, Engine engine, RNG rng, IrisData data) {
        if (engine != null) {
            IrisPlacements placements = new IrisPlacements(data, engine, IrisPlacementInputs.worldOf(engine));
            return placements.place(run.toRun(terrain, engine.getSeedManager().getSeed(), data.getPackResources().objects()));
        }
        long worldSeed = rng.nextLong();
        try (ToolWorld toolWorld = new ToolWorld(worldSeed, data.getPackResources(), new PackDeclaredSettings(data),
                PackServices.of(data), world -> new IrisPlacements(data, null, () -> world))) {
            return run.placeIn(toolWorld, terrain);
        }
    }

    private static void reportObjectBlocks(ObjectRun run, PlacerTerrain terrain,
                                           BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener) {
        if (listener == null) {
            return;
        }
        for (BlockPosition position : run.objectBlocks()) {
            listener.accept(new art.arcane.volmlib.util.math.BlockPosition(position.x(), position.y(), position.z()),
                    terrain.writtenState(position));
        }
    }
}
