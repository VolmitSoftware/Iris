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
package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.engine.decorator.DecoratedBiome;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.world.BiomeKind;

/** The decorators' view of a biome Iris's carving and floating islands hold as an {@link IrisBiome}. */
final class IrisDecoratedBiomes {
    private IrisDecoratedBiomes() {
    }

    static DecoratedBiome of(IrisBiome biome, BiomeKind kind) {
        return new DecoratedBiome(biome.getLoadKey(), biome.getModuleSettings().find(DecorantSettings.class).orElse(null), kind);
    }
}
