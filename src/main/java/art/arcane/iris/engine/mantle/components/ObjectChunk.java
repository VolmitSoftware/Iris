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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.modules.objects.CarvedSpace;
import art.arcane.iris.modules.objects.CavePlacement;
import art.arcane.iris.modules.objects.CaveTransaction;
import art.arcane.iris.modules.objects.EffectiveSettings;
import art.arcane.iris.modules.objects.ForwardingTerrain;
import art.arcane.iris.modules.objects.PlacementChanges;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.modules.objects.PlacementTags;
import art.arcane.iris.modules.objects.TerrainTransaction;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockTrait;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementTerrain;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;

/**
 * The chunk whose mantle {@code iris:objects} is placing into: the one place its objects are written. Every placement
 * here runs the pack's placement stages ({@link com.volmit.placement.Placements}) over the chunk's mantle, then writes the
 * placement's tags beside the object's own blocks.
 */
final class ObjectChunk {
    private static final int REJECTED = -1;
    private static final int SAMPLED_HEIGHT = -1;

    private final PackWorld world;
    private final PlacementTerrain terrain;
    private final CarvedSpace carved;
    private final BlockRegistry blocks;
    private final ModuleLog log;
    private final int worldHeight;

    ObjectChunk(MantleStageContext context, ModuleLog log) {
        this.world = context.world();
        this.terrain = context.placementTerrain();
        this.worldHeight = context.terrain().bounds().maxY();
        this.carved = new CarvedSpace(context.mantle().carved(), context.mantle().bounds());
        this.blocks = world.resources().blocks();
        this.log = log;
    }

    /** The voids the cave carver planned around the chunk. */
    CarvedSpace carved() {
        return carved;
    }

    /** Whether the golden-debug switch is on for the chunk, which logs every decision of its placements. */
    boolean goldenDebug(int chunkX, int chunkZ) {
        return GoldenDebugTerrain.isGoldenDebugChunk(chunkX, chunkZ);
    }

    /**
     * Places an object on the terrain, where it sits at the height its place mode finds.
     *
     * @param goldenTag logs the placement's questions under this tag, or null to leave them
     * @return the height the object was placed at, negative when the placement rejected it
     */
    int placeOnSurface(PlaceableObject object, int x, int z, ObjectPlacement placement, PlacementChanges changes,
                       PlacementTags tags, String goldenTag) {
        PlacementTerrain placer = proceduralTerrain(terrain, object, goldenTag);
        return place(ObjectRun.of(object, EffectiveSettings.withChanges(placement, changes), x, SAMPLED_HEIGHT, z), placer, tags);
    }

    /**
     * Places an object in a cave, anchored at {@code anchorY}, and writes it only when all of it lies inside the cave and
     * no deeper than {@code minDepthBelowSurface} under the surface.
     *
     * @param goldenTag logs the placement's questions under this tag, or null to leave them
     */
    CavePlacement placeInCave(PlaceableObject object, int x, int anchorY, int z, ObjectPlacement placement, PlacementChanges changes,
                              int minDepthBelowSurface, PlacementTags tags, String goldenTag) {
        ObjectPlacement effective = EffectiveSettings.withChanges(placement, changes);
        CaveTransaction transaction = new CaveTransaction(proceduralTerrain(terrain, object, goldenTag), worldHeight, anchorY,
                minDepthBelowSurface);
        int placeY = anchorY;
        if (effective.mode().placeMode().has(PlaceTrait.UPSIDE_DOWN)) {
            placeY = Math.max(1, transaction.caveCeiling(x, z) - 1 - Math.floorDiv(object.height(), 2));
        }
        int result = place(ObjectRun.of(object, effective, x, placeY, z), transaction, tags);
        if (result < 0) {
            transaction.discard();
            return new CavePlacement(result, TerrainTransaction.Commit.EMPTY);
        }
        return new CavePlacement(result, transaction.commit());
    }

    /** Places an object at a given height, which the terrain stacked above uses for the objects hung from it. */
    int placeUpper(PlaceableObject object, int x, int anchorY, int z, ObjectPlacement placement, PlacementChanges changes,
                   PlacementTags tags) {
        return place(ObjectRun.of(object, EffectiveSettings.withChanges(placement, changes), x, anchorY, z), terrain, tags);
    }

    private int place(ObjectRun run, PlacementTerrain placer, PlacementTags tags) {
        PlacementOutcome outcome = run.placeIn(world, placer);
        if (!outcome.isPlaced()) {
            return REJECTED;
        }
        writeTags(placer, run, tags);
        return run.anchorY().orElse(REJECTED);
    }

    /**
     * A procedural variant's terrain: it keeps the leaf states the variant baked, persistent or decaying, and the
     * golden-debug switch logs its questions. Pack objects use the chunk's terrain as it is.
     */
    private PlacementTerrain proceduralTerrain(PlacementTerrain base, PlaceableObject object, String goldenTag) {
        if (!ProceduralObjectCatalog.isProceduralKey(object.name())) {
            return base;
        }
        PlacementTerrain keepingBakedLeaves = new DecayingTerrain(base);
        return goldenTag == null ? keepingBakedLeaves : new GoldenDebugTerrain(keepingBakedLeaves, goldenTag, log);
    }

    /** Writes the tags beside each of the object's own blocks. */
    private void writeTags(PlacementTerrain placer, ObjectRun run, PlacementTags tags) {
        for (BlockPosition position : run.objectBlocks()) {
            if (tags.marker() != null) {
                placer.recordPlacement(position.x(), position.y(), position.z(), tags.marker());
                if (tags.treeMaterial()) {
                    placer.mark(new PlacedMarker.TreeMaterial(position));
                }
            }
            if (tags.buriedTreasure() && blocks.hasTrait(placer.get(position.x(), position.y(), position.z()), BlockTrait.STORAGE)) {
                placer.mark(new PlacedMarker.BuriedTreasure(position));
            }
        }
    }

    /** A terrain that leaves non-persistent leaves as they are, so they decay like the real thing. */
    private static final class DecayingTerrain extends ForwardingTerrain {
        private DecayingTerrain(PlacementTerrain delegate) {
            super(delegate);
        }

        @Override
        public boolean preventsDecay() {
            return false;
        }
    }
}
