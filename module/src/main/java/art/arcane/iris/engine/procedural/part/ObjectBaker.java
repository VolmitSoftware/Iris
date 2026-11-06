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
package art.arcane.iris.engine.procedural.part;

import art.arcane.iris.engine.procedural.BakeProfile;
import com.volmit.geometry.BlockPosition;
import art.arcane.iris.engine.procedural.IrisProceduralBlocks;
import art.arcane.iris.modules.objects.ObjectBuilder;
import art.arcane.iris.engine.procedural.ProceduralShape;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Vector3i;
import com.volmit.block.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Bakes one variant of a procedural object: grows its parts, paints them, decorates them and settles the leaves. */
final class ObjectBaker {
    private static final long DECORATION_SEED_SALT = 77777L;
    private static final long RULE_SEED_SALT = 4111L;

    private ObjectBaker() {
    }

    static ProceduralShape bake(ProceduralObject definition, PartOutline outline, RNG rng, ObjectBuilder blocks, BakeProfile profile) {
        BakedBlocks baked = bakeBlocks(definition, outline, rng, blocks, profile);
        return baked == null ? null : IrisProceduralBlocks.assemble(baked.placed());
    }

    /** Bakes the variant {@code rng} draws, the same as {@link #bake}, and tells which part placed each of its blocks. */
    static PartTrace trace(ProceduralObject definition, PartOutline outline, RNG rng, ObjectBuilder blocks) {
        BakedBlocks baked = bakeBlocks(definition, outline, rng, blocks, BakeProfile.NONE);
        if (baked == null) {
            return PartTrace.EMPTY;
        }
        IrisProceduralBlocks.Anchor anchor = IrisProceduralBlocks.Anchor.of(baked.placed().keySet());
        Map<Long, Integer> partsByBlock = new HashMap<>();
        for (long key : baked.painted().keySet()) {
            PartCanvas.Cell cell = baked.canvas().get(key);
            if (cell == null) {
                continue;
            }
            BlockPosition position = anchor.place(baked.sunkPosition(key));
            partsByBlock.put(PartCanvas.key(position.x(), position.y(), position.z()), cell.part());
        }
        return new PartTrace(partsByBlock);
    }

    private static BakedBlocks bakeBlocks(ProceduralObject definition, PartOutline outline, RNG rng, ObjectBuilder blocks,
                                          BakeProfile profile) {
        PartCanvas canvas = new PartCanvas();
        long variantSeed = rng.nextLong();
        new PartGrower(canvas, definition.defaultRole(), outline).growRoots(definition.parts(), variantSeed);
        profile.lap(BakeProfile.Stage.PARTS);
        if (canvas.isEmpty()) {
            return null;
        }

        Map<Long, BlockState> painted = new MaterialPainter(canvas, definition.materials(), blocks, definition.seed(),
                variantSeed + RULE_SEED_SALT).paint();
        int sinkDepth = sinkDepth(painted, definition.sink());
        profile.lap(BakeProfile.Stage.PAINT);
        new Decorator(canvas, painted, blocks, definition.seed(), sinkDepth).decorate(definition.decorations(), variantSeed + DECORATION_SEED_SALT);
        profile.lap(BakeProfile.Stage.DECORATE);
        if (definition.leafDecay()) {
            LeafDecay.supportLeaves(painted, blocks);
        } else {
            LeafDecay.persistLeaves(painted, blocks);
        }
        if (definition.waterlogged()) {
            waterlog(painted, blocks);
        }
        profile.lap(BakeProfile.Stage.LEAVES);
        return new BakedBlocks(canvas, painted, sinkDepth);
    }

    private static void waterlog(Map<Long, BlockState> painted, ObjectBuilder blocks) {
        painted.replaceAll((key, state) -> "false".equals(IrisProceduralBlocks.propertyValue(state, "waterlogged"))
                ? blocks.withPropertyIfAccepted(state, "waterlogged", "true") : state);
    }

    /** How far the object sinks: a fraction of its height. Heights below it end up underground. */
    private static int sinkDepth(Map<Long, BlockState> painted, double sink) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (long key : painted.keySet()) {
            lowest = Math.min(lowest, PartCanvas.y(key));
            highest = Math.max(highest, PartCanvas.y(key));
        }
        return (int) Math.round(Math.max(0, Math.min(1, sink)) * (highest - lowest + 1));
    }

    /** A variant's blocks before they are anchored: the parts' canvas, the painted blocks and how far they sink. */
    private record BakedBlocks(PartCanvas canvas, Map<Long, BlockState> painted, int sinkDepth) {
        Map<Vector3i, BlockState> placed() {
            Map<Vector3i, BlockState> placed = new HashMap<>();
            painted.forEach((key, state) -> placed.put(sunkPosition(key), state));
            return placed;
        }

        Vector3i sunkPosition(long key) {
            return new Vector3i(PartCanvas.x(key), PartCanvas.y(key) - sinkDepth, PartCanvas.z(key));
        }
    }
}
