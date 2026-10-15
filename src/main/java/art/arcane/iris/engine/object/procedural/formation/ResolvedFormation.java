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
import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.engine.object.procedural.ProceduralShape;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralPlacement;
import art.arcane.volmlib.util.math.RNG;

/** A rock formation made placeable: its variants are baked by {@link FormationGenerator}. */
public final class ResolvedFormation extends ResolvedProceduralPlacement {
    private final Formation settings;

    public ResolvedFormation(Formation settings, ObjectHost host) {
        super(new Shape(settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null), settings.vacuumSettings().orElse(null)), host);
        this.settings = settings;
    }

    @Override
    public int surfaceSupportBuffer() {
        return settings.surfaceSupportBuffer();
    }

    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks) {
        return FormationGenerator.generate(settings, index, rng, blocks);
    }
}
