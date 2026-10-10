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

package art.arcane.iris.builtin.structure;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

import java.util.List;
import java.util.Optional;

/** One entry of {@link StructureSettings#adjustments()}: how the platform's own starts of the structures it matches are moved and fitted. */
@Desc("A per-structure adjustment applied to vanilla, mod, and datapack structures that still generate natively (those NOT suppressed by an Iris 'structures' placement). Vertical shifts move the structure start, pieces, bounds, and jigsaw metadata together before references and placement. Intersecting trees are cleared automatically inside structure piece envelopes; optional postprocessing can build palette-driven foundation columns.")
public final class StructureAdjustment {
    @MinItems(1)
    @Ref(Reference.STRUCTURE_PREFIX)
    @Desc("Structure keys this adjustment applies to, e.g. 'minecraft:stronghold'. A namespace:path prefix also matches, so 'minecraft:village' adjusts every village variant and 'minecraft:ruined_portal' adjusts every ruined portal. Empty matches nothing.")
    private List<String> match = List.of();

    @MinNumber(-512)
    @MaxNumber(512)
    @Desc("Vertical block offset. Negative pushes the structure down, positive lifts it. The resolved shift is clamped so the structure remains inside the world's vertical build bounds.")
    private int yShift = 0;

    @Desc("Optional absolute world Y band. The structure is relocated so its vertical midpoint lands inside the band, deterministically from its start chunk and clamped to the world's build bounds. Precedence: preserveSourceY wins over yBand, and yBand wins over both yShift and Iris burial repositioning. Structures with a fixed vanilla alignment (ocean monument sea level, desert and jungle pyramid surface fit) ignore it.")
    private StructureYBand yBand = null;

    @Desc("When true, skip Iris burial repositioning so the structure keeps the Y its own vanilla placement chose. Underground structures are otherwise pushed below the lowest solid column across their whole footprint, which hides terrain-aware structures such as mineshafts that intentionally breach cliffs and surfaces. Vertical shifts still apply on top of the preserved Y: this dimension's undergroundYShift plus every matching adjustment's yShift.")
    private boolean preserveSourceY = false;

    @Desc("Optional foundation columns placed beneath the native structure piece bases after placement.")
    private StructureStilts stilt = null;

    @Desc("Optional terrain integration override. VACUUM raises surface terrain from processed solid rigid-template foundations at or below each authored ground plane with a 12-block falloff without lowering existing ground. BORE and FORCE_CARVE clear every intersecting chunk before native pieces are placed, while ENCASE fills it with solid blocks instead. Left unset, SOURCE replays the registered structure's authored terrain adaptation, including surface fitting, burial, and encapsulation.")
    private StructureTerrain terrain = null;

    public StructureAdjustment() {
    }

    public StructureAdjustment(List<String> match, int yShift, StructureYBand yBand, boolean preserveSourceY, StructureStilts stilt,
                               StructureTerrain terrain) {
        this.match = List.copyOf(match);
        this.yShift = yShift;
        this.yBand = yBand;
        this.preserveSourceY = preserveSourceY;
        this.stilt = stilt;
        this.terrain = terrain;
    }

    /** Family patterns of the structures this adjustment applies to. */
    public List<String> match() {
        return StructureSettings.listOrEmpty(match);
    }

    public int yShift() {
        return yShift;
    }

    public Optional<StructureYBand> yBand() {
        return Optional.ofNullable(yBand);
    }

    public boolean preserveSourceY() {
        return preserveSourceY;
    }

    public Optional<StructureStilts> stilt() {
        return Optional.ofNullable(stilt);
    }

    public Optional<StructureTerrain> terrain() {
        return Optional.ofNullable(terrain);
    }
}
