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

package art.arcane.iris.builtin.carve;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.LinkedBiomes;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

import java.util.Set;

/** A biome's {@code carvingBiome}, written as the biome key: it registers that biome with the pack's layout and is not applied as a substitute under carvings. */
@Desc("Registers the referenced biome with this pack's reachable biome set. It is NOT applied as a substitute under carvings; cave biomes come from the region's caveBiomes list.")
@Feature(id = "iris:carve/biome", name = "Carving biome", key = "carvingBiome", root = "biome", targets = Target.BIOME)
public final class CarvingBiomeSettings implements IrisFeature, LinkedBiomes {
    @Ref(Reference.PACK_BIOME)
    @Desc("Registers the referenced biome with this pack's reachable biome set. It is NOT applied as a substitute under carvings; cave biomes come from the region's caveBiomes list.")
    private String biome = "";

    public CarvingBiomeSettings() {
    }

    public CarvingBiomeSettings(String biome) {
        this.biome = biome;
    }

    public String biome() {
        return biome == null ? "" : biome;
    }

    @Override
    public Set<String> treeBiomes() {
        return Set.of(biome());
    }
}
