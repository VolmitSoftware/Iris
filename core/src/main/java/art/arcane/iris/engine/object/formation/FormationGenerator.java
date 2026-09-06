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

package art.arcane.iris.engine.object.formation;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisFormation;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FormationGenerator {
    private FormationGenerator() {
    }

    public static IrisObject generate(IrisFormation f, int variantIndex, RNG rng, IrisData data) {
        int lo = Math.min(f.getHeightMin(), f.getHeightMax());
        int hi = Math.max(f.getHeightMin(), f.getHeightMax());
        int height = Math.max(3, rng.i(lo, hi + 1));

        int wLo = Math.min(f.getBaseWidthMin(), f.getBaseWidthMax());
        int wHi = Math.max(f.getBaseWidthMin(), f.getBaseWidthMax());
        double baseRadius = Math.max(1, rng.i(wLo, wHi + 1));

        FormationCanvas canvas = new FormationCanvas();

        switch (f.getForm()) {
            case SPIRE -> FormationShapeBuilder.spire(canvas, f, height, baseRadius, rng);
            case HOODOO -> FormationShapeBuilder.hoodoo(canvas, f, height, baseRadius, rng);
            case ARCH -> FormationShapeBuilder.arch(canvas, f, height, baseRadius, rng);
            case SEA_STACK -> FormationShapeBuilder.seaStack(canvas, f, height, baseRadius, rng);
            case BOULDER -> FormationShapeBuilder.boulder(canvas, f, height, baseRadius, rng);
            case BASALT_COLUMN -> FormationShapeBuilder.basaltColumns(canvas, f, height, baseRadius, rng);
            case ICEBERG -> FormationShapeBuilder.iceberg(canvas, f, height, baseRadius, rng);
            case FISSURE -> FormationShapeBuilder.fissure(canvas, f, height, baseRadius, rng);
            case SPIRAL -> FormationShapeBuilder.spiral(canvas, f, height, baseRadius, rng);
            case OVERHANG -> FormationShapeBuilder.overhang(canvas, f, height, baseRadius, rng);
        }

        if (canvas.isEmpty()) {
            return null;
        }

        Map<Vector3i, PlatformBlockState> resolved = new HashMap<>();
        FormationBlockResolver resolver = new FormationBlockResolver(f, data);
        for (Map.Entry<Vector3i, FormationCanvas.Role> entry : canvas.getCells().entrySet()) {
            PlatformBlockState bd = resolver.resolve(entry.getValue(), entry.getKey());
            if (bd == null) {
                continue;
            }
            resolved.put(entry.getKey(), bd);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }
}
