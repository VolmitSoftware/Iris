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

import art.arcane.iris.engine.procedural.IrisProceduralBlocks;
import art.arcane.iris.modules.objects.ObjectBuilder;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.geometry.BlockFace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Places decorations against the painted object. A decoration attaches to the block it rests on: vines and lichen
 * grow toward it, directional blocks face away from it and lanterns hang under it.
 */
final class Decorator {
    private static final long DECORATION_SEED_STRIDE = 911L;
    private static final BlockFace[] SIDES = {BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH};

    private final PartCanvas canvas;
    private final Map<Long, BlockState> painted;
    private final ObjectBuilder blocks;
    private final long paletteSeed;
    private final int groundY;

    /** @param groundY the lowest height above the terrain; anything below it ends up buried, so nothing decorates there */
    Decorator(PartCanvas canvas, Map<Long, BlockState> painted, ObjectBuilder blocks, long paletteSeed, int groundY) {
        this.canvas = canvas;
        this.painted = painted;
        this.blocks = blocks;
        this.paletteSeed = paletteSeed;
        this.groundY = groundY;
    }

    void decorate(List<ProceduralDecoration> decorations, long seed) {
        for (int index = 0; index < decorations.size(); index++) {
            ProceduralDecoration decoration = decorations.get(index);
            if (!decoration.choice().isSet()) {
                continue;
            }
            long decorationSeed = seed + index * DECORATION_SEED_STRIDE;
            RNG rng = new RNG(decorationSeed);
            for (Spot spot : spots(decoration, rng)) {
                if (spot.y() >= groundY && PartNoise.passes(decoration.pattern(), decoration.chance(), decoration.scale(), spot.x(), spot.y(), spot.z(), decorationSeed)) {
                    place(decoration, spot, rng);
                }
            }
        }
    }

    private void place(ProceduralDecoration decoration, Spot spot, RNG rng) {
        boolean hangs = decoration.at() == DecorationSpot.BELOW || decoration.at() == DecorationSpot.BOTTOM;
        int strand = hangs ? Math.max(1, (int) Math.round(decoration.length().sample(rng, 0))) : 1;
        for (int step = 0; step < strand; step++) {
            int y = spot.y() - step;
            long key = PartCanvas.key(spot.x(), y, spot.z());
            if (y < groundY || painted.containsKey(key)) {
                return;
            }
            BlockState state = decoration.choice().at(blocks, paletteSeed, spot.x(), y, spot.z());
            if (state == null || blocks.has(state, BlockTrait.AIR)) {
                return;
            }
            painted.put(key, attached(state, spot.support()));
        }
    }

    private List<Spot> spots(ProceduralDecoration decoration, RNG rng) {
        List<Long> decorated = decoratedBlocks(decoration);
        return switch (decoration.at()) {
            case ABOVE -> besideEach(decorated, BlockFace.UP);
            case BELOW -> besideEach(decorated, BlockFace.DOWN);
            case SIDE -> besideEach(decorated, SIDES);
            case TOP -> columnEnds(decorated, true);
            case BOTTOM -> columnEnds(decorated, false);
            case TIPS -> tips(decoration);
            case GROUND -> ground(decoration.radius().sample(rng, 0));
        };
    }

    /** The painted blocks of the roles the decoration is for, in a stable order. */
    private List<Long> decoratedBlocks(ProceduralDecoration decoration) {
        Set<Integer> roles = new HashSet<>();
        for (String role : decoration.on()) {
            roles.add(canvas.role(role));
        }
        List<Long> decorated = new ArrayList<>();
        for (long key : painted.keySet()) {
            PartCanvas.Cell cell = canvas.get(key);
            if (cell != null && (roles.isEmpty() || roles.contains(cell.role()))) {
                decorated.add(key);
            }
        }
        decorated.sort(Long::compare);
        return decorated;
    }

    private List<Spot> besideEach(List<Long> decorated, BlockFace... faces) {
        List<Spot> spots = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (long key : decorated) {
            for (BlockFace face : faces) {
                int[] step = stepToward(face);
                int x = PartCanvas.x(key) + step[0];
                int y = PartCanvas.y(key) + step[1];
                int z = PartCanvas.z(key) + step[2];
                long spotKey = PartCanvas.key(x, y, z);
                if (!painted.containsKey(spotKey) && taken.add(spotKey)) {
                    spots.add(new Spot(x, y, z, opposite(face)));
                }
            }
        }
        return spots;
    }

    private List<Spot> columnEnds(List<Long> decorated, boolean top) {
        Map<Long, Integer> endByColumn = new HashMap<>();
        for (long key : decorated) {
            long column = PartCanvas.key(PartCanvas.x(key), 0, PartCanvas.z(key));
            int y = PartCanvas.y(key);
            endByColumn.merge(column, y, top ? Math::max : Math::min);
        }
        List<Spot> spots = new ArrayList<>();
        endByColumn.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int x = PartCanvas.x(entry.getKey());
            int z = PartCanvas.z(entry.getKey());
            int y = entry.getValue() + (top ? 1 : -1);
            if (!painted.containsKey(PartCanvas.key(x, y, z))) {
                spots.add(new Spot(x, y, z, top ? BlockFace.DOWN : BlockFace.UP));
            }
        });
        return spots;
    }

    private List<Spot> tips(ProceduralDecoration decoration) {
        Set<Integer> roles = new HashSet<>();
        for (String role : decoration.on()) {
            roles.add(canvas.role(role));
        }
        List<Spot> spots = new ArrayList<>();
        for (PartCanvas.Tip tip : canvas.tips()) {
            if (!roles.isEmpty() && !roles.contains(tip.role())) {
                continue;
            }
            int x = tip.x() + (int) Math.round(tip.heading()[0]);
            int y = tip.y() + (int) Math.round(tip.heading()[1]);
            int z = tip.z() + (int) Math.round(tip.heading()[2]);
            if (!painted.containsKey(PartCanvas.key(x, y, z))) {
                spots.add(new Spot(x, y, z, supportToward(tip.x() - x, tip.y() - y, tip.z() - z)));
            }
        }
        return spots;
    }

    /** Every open ground spot within the radius of where the object stands, so an arch gets plants around both feet. */
    private List<Spot> ground(double radius) {
        int reach = (int) Math.ceil(radius);
        Set<Long> nearby = new TreeSet<>();
        for (long foot : footprint()) {
            int footX = PartCanvas.x(foot);
            int footZ = PartCanvas.z(foot);
            for (int x = footX - reach; x <= footX + reach; x++) {
                for (int z = footZ - reach; z <= footZ + reach; z++) {
                    if (Math.hypot(x - footX, z - footZ) <= radius) {
                        nearby.add(PartCanvas.key(x, groundY, z));
                    }
                }
            }
        }
        List<Spot> spots = new ArrayList<>();
        for (long key : nearby) {
            int x = PartCanvas.x(key);
            int z = PartCanvas.z(key);
            if (!painted.containsKey(key) && !painted.containsKey(PartCanvas.key(x, groundY + 1, z))) {
                spots.add(new Spot(x, groundY, z, BlockFace.DOWN));
            }
        }
        return spots;
    }

    /** The columns where the object meets the ground, or its origin if it does not reach the ground. */
    private Set<Long> footprint() {
        Set<Long> footprint = new TreeSet<>();
        for (long key : painted.keySet()) {
            if (PartCanvas.y(key) == groundY) {
                footprint.add(PartCanvas.key(PartCanvas.x(key), groundY, PartCanvas.z(key)));
            }
        }
        if (footprint.isEmpty()) {
            footprint.add(PartCanvas.key(0, groundY, 0));
        }
        return footprint;
    }

    private static BlockFace supportToward(int dx, int dy, int dz) {
        if (dy > 0) {
            return BlockFace.UP;
        }
        if (dy < 0) {
            return BlockFace.DOWN;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** {@code state} turned to rest on the block toward {@code support}. */
    private BlockState attached(BlockState state, BlockFace support) {
        String side = support.name().toLowerCase();
        if ("false".equals(IrisProceduralBlocks.propertyValue(state, side))) {
            return blocks.withProperty(state, side, "true");
        }
        if (support == BlockFace.UP && IrisProceduralBlocks.hasProperty(state, "hanging")) {
            return blocks.withProperty(state, "hanging", "true");
        }
        if (IrisProceduralBlocks.hasProperty(state, "facing")) {
            return blocks.withProperty(state, "facing", opposite(support).name().toLowerCase());
        }
        return state;
    }

    private static int[] stepToward(BlockFace face) {
        return switch (face) {
            case UP -> new int[]{0, 1, 0};
            case DOWN -> new int[]{0, -1, 0};
            case NORTH -> new int[]{0, 0, -1};
            case SOUTH -> new int[]{0, 0, 1};
            case EAST -> new int[]{1, 0, 0};
            case WEST -> new int[]{-1, 0, 0};
        };
    }

    private static BlockFace opposite(BlockFace face) {
        return switch (face) {
            case UP -> BlockFace.DOWN;
            case DOWN -> BlockFace.UP;
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
        };
    }

    /** An empty block a decoration may go in, and the side its support is on. */
    private record Spot(int x, int y, int z, BlockFace support) {
    }
}
