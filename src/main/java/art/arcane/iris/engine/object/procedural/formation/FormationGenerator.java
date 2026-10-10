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

package art.arcane.iris.engine.object.procedural.formation;

import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FormationGenerator {
    private FormationGenerator() {
    }

    public static IrisObject generate(Formation f, int variantIndex, RNG rng, IrisData data) {
        int lo = Math.min(f.heightMin(), f.heightMax());
        int hi = Math.max(f.heightMin(), f.heightMax());
        int height = Math.max(3, rng.i(lo, hi + 1));

        int wLo = Math.min(f.baseWidthMin(), f.baseWidthMax());
        int wHi = Math.max(f.baseWidthMin(), f.baseWidthMax());
        double baseRadius = Math.max(1, rng.i(wLo, wHi + 1));

        FormationCanvas canvas = new FormationCanvas();

        switch (f.form()) {
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
        for (Map.Entry<Vector3i, FormationCanvas.Role> entry : canvas.getCells().entrySet()) {
            PlatformBlockState bd = FormationBlockResolver.resolve(f, data, entry.getValue(), entry.getKey());
            if (bd == null) {
                continue;
            }
            resolved.put(entry.getKey(), bd);
        }

        return IrisProceduralBlocks.assemble(resolved);
    }
}
