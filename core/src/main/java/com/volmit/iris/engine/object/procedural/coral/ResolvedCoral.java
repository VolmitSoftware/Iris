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

package com.volmit.iris.engine.object.procedural.coral;

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.ResolvedProceduralPlacement;
import com.volmit.iris.util.math.RNG;

/** A coral reef made placeable: its variants are baked by {@link CoralGenerator}. */
public final class ResolvedCoral extends ResolvedProceduralPlacement {
    private final Coral settings;

    public ResolvedCoral(Coral settings, IrisData data) {
        super(new Shape("coral", settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null)), data);
        this.settings = settings;
    }

    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks) {
        return CoralGenerator.generate(settings, index, rng, blocks);
    }
}
