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

package com.volmit.iris.engine.object.procedural.ruin;

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.engine.object.procedural.tree.TreeFunctions;
import com.volmit.iris.util.math.Vector3i;
import com.volmit.iris.util.math.RNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuinGenerator {
    private RuinGenerator() {
    }

    public static ProceduralShape generate(Ruin ruin, int variantIndex, RNG rng, ObjectBuilder blocks) {
        int count = Math.max(1, ruin.variants());
        int height = pick(ruin.heightMin(), ruin.heightMax(), variantIndex, count, rng);
        int width = pick(ruin.widthMin(), ruin.widthMax(), variantIndex, count, rng);
        int length = pick(ruin.lengthMin(), ruin.lengthMax(), variantIndex, count, rng);

        RuinBlockCanvas canvas = new RuinBlockCanvas();
        switch (ruin.form()) {
            case PILLAR -> RuinShapes.pillar(canvas, Math.max(1, width), Math.max(2, height), rng);
            case WALL -> RuinShapes.wall(canvas, Math.max(1, width), Math.max(2, length), Math.max(2, height), rng);
            case ARCH -> RuinShapes.arch(canvas, Math.max(1, width), Math.max(3, length), Math.max(3, height), rng);
            case FLOOR_SLAB -> RuinShapes.floorSlab(canvas, Math.max(3, width), Math.max(3, length), Math.max(1, Math.min(height, 2)));
            case RUBBLE -> RuinShapes.rubble(canvas, Math.max(3, width), Math.max(3, length), Math.max(1, Math.min(height, 4)), rng);
        }

        applyBurial(ruin, canvas, height);
        applyErosion(ruin, canvas);
        applyAccents(ruin, canvas, rng, blocks);

        return resolve(ruin, canvas, blocks);
    }

    private static int pick(int lo, int hi, int variantIndex, int count, RNG rng) {
        int min = Math.min(lo, hi);
        int max = Math.max(lo, hi);
        if (min == max || count <= 1) {
            return rng.i(min, max + 1);
        }
        double step = (max - min) / (double) (count - 1);
        double base = min + step * variantIndex;
        double jitter = rng.d(-step * 0.3, step * 0.3);
        return (int) Math.round(Math.max(min, Math.min(max, base + jitter)));
    }

    private static void applyBurial(Ruin ruin, RuinBlockCanvas canvas, int height) {
        double fraction = clamp01(ruin.buriedFraction());
        if (fraction <= 0.0) {
            return;
        }
        int sink = (int) Math.round(height * fraction);
        if (sink <= 0) {
            return;
        }
        Map<Vector3i, RuinBlockCanvas.Cell> moved = new HashMap<>();
        for (Map.Entry<Vector3i, RuinBlockCanvas.Cell> entry : canvas.cells().entrySet()) {
            Vector3i v = entry.getKey();
            moved.put(new Vector3i(v.getBlockX(), v.getBlockY() - sink, v.getBlockZ()), entry.getValue());
        }
        canvas.cells().clear();
        canvas.cells().putAll(moved);
    }

    private static void applyErosion(Ruin ruin, RuinBlockCanvas canvas) {
        double erosion = clamp01(ruin.erosion());
        if (erosion <= 0.0) {
            return;
        }
        double scale = Math.max(0.0001, ruin.erosionScale());
        long erosionSeed = ruin.seed() * 0x2545F4914F6CDD1DL + 17L;
        int minY = Integer.MAX_VALUE;
        for (Vector3i v : canvas.cells().keySet()) {
            minY = Math.min(minY, v.getBlockY());
        }

        List<Vector3i> doomed = new ArrayList<>();
        for (Map.Entry<Vector3i, RuinBlockCanvas.Cell> entry : canvas.cells().entrySet()) {
            Vector3i v = entry.getKey();
            RuinBlockCanvas.Cell cell = entry.getValue();
            if (cell.structural() || v.getBlockY() <= minY) {
                continue;
            }
            int sx = (int) Math.round(v.getBlockX() * scale);
            int sy = (int) Math.round(v.getBlockY() * scale);
            int sz = (int) Math.round(v.getBlockZ() * scale);
            double n = TreeFunctions.valueNoise3D(sx, sy, sz, erosionSeed);
            if (n < erosion) {
                doomed.add(v);
            }
        }
        for (Vector3i v : doomed) {
            canvas.cells().remove(v);
        }
    }

    private static void applyAccents(Ruin ruin, RuinBlockCanvas canvas, RNG rng, ObjectBuilder blocks) {
        if (ruin.accents() == null || ruin.accents().isEmpty()) {
            return;
        }
        int decoratorIndex = 0;
        for (com.volmit.iris.engine.object.procedural.ruin.RuinDecorator decorator : ruin.accents()) {
            RuinDecoratorApplier.apply(canvas, decorator, blocks, ruin.seed() + decoratorIndex * 911L, rng.nextParallelRNG(decoratorIndex + 1));
            decoratorIndex++;
        }
    }

    private static ProceduralShape resolve(Ruin ruin, RuinBlockCanvas canvas, ObjectBuilder blocks) {
        if (canvas.cells().isEmpty()) {
            return null;
        }
        double scale = Math.max(0.0001, ruin.weatheringScale());
        double mossiness = clamp01(ruin.mossiness());
        long weatherSeed = ruin.seed() * 0x9E3779B97F4A7C15L + 101L;
        RNG paletteRng = new RNG(ruin.seed());

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Vector3i v : canvas.cells().keySet()) {
            minY = Math.min(minY, v.getBlockY());
            maxY = Math.max(maxY, v.getBlockY());
        }
        int span = Math.max(1, maxY - minY);

        Map<Vector3i, BlockData> resolved = new HashMap<>();
        for (Map.Entry<Vector3i, RuinBlockCanvas.Cell> entry : canvas.cells().entrySet()) {
            Vector3i v = entry.getKey();
            RuinBlockCanvas.Cell cell = entry.getValue();
            BlockData bd;
            if (cell.role() == RuinBlockCanvas.Role.ACCENT) {
                bd = cell.accentData();
            } else {
                bd = structuralBlock(ruin, cell, v, blocks, paletteRng, scale, mossiness, weatherSeed, minY, span);
            }
            if (bd != null) {
                resolved.put(v, bd);
            }
        }

        return IrisProceduralBlocks.assemble(resolved);
    }

    private static BlockData structuralBlock(Ruin ruin, RuinBlockCanvas.Cell cell, Vector3i v, ObjectBuilder blocks, RNG paletteRng, double scale, double mossiness, long weatherSeed, int minY, int span) {
        boolean weathered = false;
        if (mossiness > 0.0) {
            int sx = (int) Math.round(v.getBlockX() * scale);
            int sy = (int) Math.round(v.getBlockY() * scale);
            int sz = (int) Math.round(v.getBlockZ() * scale);
            double n = TreeFunctions.valueNoise3D(sx, sy, sz, weatherSeed);
            double normalized = (v.getBlockY() - minY) / (double) span;
            double gradient = 1.0 - 0.6 * normalized;
            weathered = n < mossiness * gradient;
        }

        if (weathered) {
            BlockData wd = IrisProceduralBlocks.resolve(ruin.weatheredBlock(), ruin.weatheringPalette(), blocks, v.getBlockX(), v.getBlockY(), v.getBlockZ(), paletteRng);
            if (wd != null) {
                return wd;
            }
        }
        return IrisProceduralBlocks.resolve(ruin.block(), ruin.blockPalette(), blocks, v.getBlockX(), v.getBlockY(), v.getBlockZ(), paletteRng);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
