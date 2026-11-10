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

package art.arcane.iris.engine.object.structure.jigsaw;

import com.volmit.config.Desc;
import com.volmit.config.ResourceRef;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A jigsaw pool. A pool is a weighted set of pieces the assembler chooses from when a connector targets this pool.")
@Data
public class IrisJigsawPool {
    @Desc("The weighted pieces in this pool.")
    private List<IrisJigsawPieceEntry> pieces = new KList<>();

    @ResourceRef(IrisJigsawPool.class)
    @Desc("The direct fallback pool tried after this pool, or used alone at maximum depth. The fallback pool's own fallback is not part of the same selection. Leave empty to stop expanding at maximum depth.")
    private String fallback = "";

    @Desc("Whether failure to place from this pool must be resolved through its direct fallback even when the structure does not require every branch to be capped.")
    private boolean mandatoryFallback = false;

    public boolean requiresFallback(boolean structureRequiresCaps) {
        return structureRequiresCaps || mandatoryFallback;
    }
}
