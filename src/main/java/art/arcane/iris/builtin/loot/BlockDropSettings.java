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

package art.arcane.iris.builtin.loot;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;

import java.util.Collections;
import java.util.List;

/**
 * The block drops one dimension, region or biome declares, written as {@code "blockDrops": [...]}, the list of
 * {@link BlockDrop}s. A broken block drops those of its biome, then, unless one of them skips its parents, those of its
 * region and dimension.
 */
@Desc("Define custom block drops for this area")
@Feature(id = "iris:loot/drops", name = "Block drops", key = "blockDrops", root = "drops", targets = {Target.DIMENSION, Target.REGION, Target.BIOME})
public final class BlockDropSettings implements IrisFeature {
    @MinItems(1)
    @Desc("Define custom block drops for this area")
    private List<BlockDrop> drops = List.of();

    public BlockDropSettings() {
    }

    public List<BlockDrop> drops() {
        return drops == null ? List.of() : Collections.unmodifiableList(drops);
    }
}
