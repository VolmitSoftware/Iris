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
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns the canvas into block data: each block takes its role's material, or the first of its rules that applies. */
final class MaterialPainter {
    private static final long RULE_SEED_STRIDE = 7907L;
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private final PartCanvas canvas;
    private final Map<Integer, ProceduralMaterial> materialByRole = new HashMap<>();
    private final ProceduralMaterial fallback;
    private final ObjectBuilder blocks;
    private final long paletteSeed;
    private final long ruleSeed;
    private final int lowestY;
    private final int heightSpan;

    MaterialPainter(PartCanvas canvas, List<ProceduralMaterial> materials, ObjectBuilder blocks, long paletteSeed, long ruleSeed) {
        this.canvas = canvas;
        this.blocks = blocks;
        this.paletteSeed = paletteSeed;
        this.ruleSeed = ruleSeed;
        this.fallback = materials.isEmpty() ? null : materials.getFirst();
        for (ProceduralMaterial material : materials) {
            materialByRole.putIfAbsent(canvas.role(material.role()), material);
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (long key : canvas.cells().keySet()) {
            lowest = Math.min(lowest, PartCanvas.y(key));
            highest = Math.max(highest, PartCanvas.y(key));
        }
        this.lowestY = lowest;
        this.heightSpan = Math.max(1, highest - lowest);
    }

    /** The block of every claimed position, leaving out those a rule turned to air. */
    Map<Long, BlockState> paint() {
        Map<Long, BlockState> painted = new HashMap<>();
        for (Map.Entry<Long, PartCanvas.Cell> entry : canvas.cells().entrySet()) {
            BlockState state = blockFor(entry.getKey(), entry.getValue());
            if (state != null && !blocks.has(state, BlockTrait.AIR)) {
                painted.put(entry.getKey(), state);
            }
        }
        return painted;
    }

    private BlockState blockFor(long key, PartCanvas.Cell cell) {
        ProceduralMaterial material = materialByRole.getOrDefault(cell.role(), fallback);
        if (material == null) {
            return null;
        }
        int x = PartCanvas.x(key);
        int y = PartCanvas.y(key);
        int z = PartCanvas.z(key);
        BlockChoice choice = material.choice();
        List<MaterialRule> rules = material.rules();
        for (int index = 0; index < rules.size(); index++) {
            if (applies(rules.get(index), index, x, y, z, cell)) {
                choice = rules.get(index).choice();
                break;
            }
        }
        BlockState state = choice.at(blocks, paletteSeed, x, y, z);
        if (state == null || blocks.has(state, BlockTrait.AIR)) {
            return state;
        }
        return alignedToAxis(state, cell.axis());
    }

    private boolean applies(MaterialRule rule, int index, int x, int y, int z, PartCanvas.Cell cell) {
        double height = (y - lowestY) / (double) heightSpan;
        if (rule.height() != null && !rule.height().contains(height)) {
            return false;
        }
        if (rule.along() != null && !rule.along().contains(cell.along())) {
            return false;
        }
        if (rule.face() != null && !open(rule.face(), x, y, z)) {
            return false;
        }
        return PartNoise.passes(rule.pattern(), rule.chance().at(height), rule.scale(), x, y, z, ruleSeed + index * RULE_SEED_STRIDE);
    }

    private boolean open(RuleFace face, int x, int y, int z) {
        return switch (face) {
            case UP -> !canvas.has(x, y + 1, z);
            case DOWN -> !canvas.has(x, y - 1, z);
            case SIDE -> openSide(x, y, z);
            case ANY -> openSide(x, y, z) || !canvas.has(x, y + 1, z) || !canvas.has(x, y - 1, z);
        };
    }

    private boolean openSide(int x, int y, int z) {
        for (int[] side : SIDES) {
            if (!canvas.has(x + side[0], y, z + side[2])) {
                return true;
            }
        }
        return false;
    }

    private BlockState alignedToAxis(BlockState state, PartCanvas.Axis axis) {
        if (axis == PartCanvas.Axis.NONE || !IrisProceduralBlocks.hasProperty(state, "axis")) {
            return state;
        }
        return blocks.withProperty(state, "axis", axis.name().toLowerCase());
    }
}
