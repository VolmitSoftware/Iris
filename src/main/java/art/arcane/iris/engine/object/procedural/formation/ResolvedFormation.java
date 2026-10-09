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

package art.arcane.iris.engine.object.procedural.formation;

import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralPlacement;
import art.arcane.volmlib.util.math.RNG;

/** A rock formation made placeable: its variants are baked by {@link FormationGenerator}. */
public final class ResolvedFormation extends ResolvedProceduralPlacement {
    private final Formation settings;

    public ResolvedFormation(Formation settings) {
        super(new Shape(settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null), settings.vacuumSettings().orElse(null)));
        this.settings = settings;
    }

    @Override
    protected void customize(IrisObjectPlacement placement) {
        placement.setSurfaceSupportBuffer(settings.surfaceSupportBuffer());
    }

    @Override
    protected IrisObject generate(int index, RNG rng, IrisData data) {
        return FormationGenerator.generate(settings, index, rng, data);
    }
}
