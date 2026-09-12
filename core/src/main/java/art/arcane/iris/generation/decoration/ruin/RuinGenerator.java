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

package art.arcane.iris.generation.decoration.ruin;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.decoration.tree.TreeFunctions;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuinGenerator {
    private RuinGenerator() {
    }

    public static IrisObject generate(IrisRuin ruin, int variantIndex, RNG rng, IrisData data) {
        int count = Math.max(1, ruin.getVariants());
        int height = pick(ruin.getHeightMin(), ruin.getHeightMax(), variantIndex, count, rng);
        int width = pick(ruin.getWidthMin(), ruin.getWidthMax(), variantIndex, count, rng);
        int length = pick(ruin.getLengthMin(), ruin.getLengthMax(), variantIndex, count, rng);

        RuinBlockCanvas canvas = new RuinBlockCanvas();
        switch (ruin.getForm()) {
            case PILLAR -> RuinShapes.pillar(canvas, Math.max(1, width), Math.max(2, height), rng);
            case WALL -> RuinShapes.wall(canvas, Math.max(1, width), Math.max(2, length), Math.max(2, height), rng);
            case ARCH -> RuinShapes.arch(canvas, Math.max(1, width), Math.max(3, length), Math.max(3, height), rng);
            case FLOOR_SLAB -> RuinShapes.floorSlab(canvas, Math.max(3, width), Math.max(3, length), Math.max(1, Math.min(height, 2)));
            case RUBBLE -> RuinShapes.rubble(canvas, Math.max(3, width), Math.max(3, length), Math.max(1, Math.min(height, 4)), rng);
        }

        applyBurial(ruin, canvas, height);
        applyErosion(ruin, canvas);
        applyAccents(ruin, canvas, rng, data);

        return resolve(ruin, canvas, data);
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

    private static void applyBurial(IrisRuin ruin, RuinBlockCanvas canvas, int height) {
        double fraction = clamp01(ruin.getBuriedFraction());
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

    private static void applyErosion(IrisRuin ruin, RuinBlockCanvas canvas) {
        double erosion = clamp01(ruin.getErosion());
        if (erosion <= 0.0) {
            return;
        }
        double scale = Math.max(0.0001, ruin.getErosionScale());
        long erosionSeed = ruin.getSeed() * 0x2545F4914F6CDD1DL + 17L;
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

    private static void applyAccents(IrisRuin ruin, RuinBlockCanvas canvas, RNG rng, IrisData data) {
        if (ruin.getAccents() == null || ruin.getAccents().isEmpty()) {
            return;
        }
        int decoratorIndex = 0;
        for (art.arcane.iris.generation.decoration.ruin.IrisRuinDecorator decorator : ruin.getAccents()) {
            RuinDecoratorApplier.apply(canvas, decorator, data, ruin.getSeed() + decoratorIndex * 911L, rng.nextParallelRNG(decoratorIndex + 1));
            decoratorIndex++;
        }
    }

    private static IrisObject resolve(IrisRuin ruin, RuinBlockCanvas canvas, IrisData data) {
        if (canvas.cells().isEmpty()) {
            return null;
        }
        double scale = Math.max(0.0001, ruin.getWeatheringScale());
        double mossiness = clamp01(ruin.getMossiness());
        long weatherSeed = ruin.getSeed() * 0x9E3779B97F4A7C15L + 101L;
        RNG paletteRng = new RNG(ruin.getSeed());

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Vector3i v : canvas.cells().keySet()) {
            minY = Math.min(minY, v.getBlockY());
            maxY = Math.max(maxY, v.getBlockY());
        }
        int span = Math.max(1, maxY - minY);

        Map<Vector3i, PlatformBlockState> blocks = new HashMap<>();
        for (Map.Entry<Vector3i, RuinBlockCanvas.Cell> entry : canvas.cells().entrySet()) {
            Vector3i v = entry.getKey();
            RuinBlockCanvas.Cell cell = entry.getValue();
            PlatformBlockState bd;
            if (cell.role() == RuinBlockCanvas.Role.ACCENT) {
                bd = cell.accentData();
            } else {
                bd = structuralBlock(ruin, cell, v, data, paletteRng, scale, mossiness, weatherSeed, minY, span);
            }
            if (bd != null) {
                blocks.put(v, bd);
            }
        }

        return IrisProceduralBlocks.assemble(blocks);
    }

    private static PlatformBlockState structuralBlock(IrisRuin ruin, RuinBlockCanvas.Cell cell, Vector3i v, IrisData data, RNG paletteRng, double scale, double mossiness, long weatherSeed, int minY, int span) {
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
            PlatformBlockState wd = IrisProceduralBlocks.resolve(ruin.getWeatheredBlock(), ruin.getWeatheringPalette(), data, v.getBlockX(), v.getBlockY(), v.getBlockZ(), paletteRng);
            if (wd != null) {
                return wd;
            }
        }
        return IrisProceduralBlocks.resolve(ruin.getBlock(), ruin.getBlockPalette(), data, v.getBlockX(), v.getBlockY(), v.getBlockZ(), paletteRng);
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
