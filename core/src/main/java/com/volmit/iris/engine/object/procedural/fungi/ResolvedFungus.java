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

package com.volmit.iris.engine.object.procedural.fungi;

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.ResolvedProceduralPlacement;
import com.volmit.iris.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

/** A fungus made placeable: its variants are baked by {@link FungusGenerator}. */
public final class ResolvedFungus extends ResolvedProceduralPlacement {
    private final Fungus settings;

    public ResolvedFungus(Fungus settings, IrisData data) {
        super(new Shape("fungus", settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null)), data);
        this.settings = settings;
    }

    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks) {
        throw new UnsupportedOperationException("A fungus bakes its variants itself, from the stem heights");
    }

    @Override
    protected List<IrisObject> bake(ObjectBuilder blocks) {
        List<IrisObject> baked = new ArrayList<>();
        int count = variantCount();
        int lo = Math.min(settings.stemHeightMin(), settings.stemHeightMax());
        int hi = Math.max(settings.stemHeightMin(), settings.stemHeightMax());
        RNG heightRng = new RNG(seed());

        for (int i = 0; i < count; i++) {
            int height;
            if (count == 1 || lo == hi) {
                height = heightRng.i(lo, hi + 1);
            } else {
                double step = (hi - lo) / (double) (count - 1);
                double base = lo + step * i;
                double jitter = heightRng.d(-step * 0.3, step * 0.3);
                height = (int) Math.round(Math.max(lo, Math.min(hi, base + jitter)));
            }

            ProceduralShape generated = FungusGenerator.generate(settings, Math.max(1, height), variantRng(i), blocks);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            baked.add(build(blocks, variantKey(i), generated));
        }

        return baked;
    }
}
