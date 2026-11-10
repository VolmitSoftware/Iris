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

package art.arcane.iris.modules.carve;

import com.volmit.config.Desc;
import com.volmit.config.Range;
import com.volmit.config.ResourceRef;
import com.volmit.world.PackBiome;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;

import java.util.Collections;
import java.util.List;

/**
 * One dimension-level cave biome override, an entry of {@code carving}: a pack biome that replaces the cave biome within a band
 * of world Y, optionally split into child entries by a noise pattern. IrisApi types only; {@code CarvingResolver} picks
 * the entry for a position.
 */
@Snippet("iris/carve/dimension-carving-entry")
@Desc("Dimension-level cave biome override with absolute world Y bounds.")
public final class CarvingEntry {
    private static final double DEFAULT_WORLD_Y_MIN = -64;
    private static final double DEFAULT_WORLD_Y_MAX = 320;
    private static final double DEFAULT_CHILD_SHRINK_FACTOR = 1.5;
    private static final int DEFAULT_CHILD_RECURSION_DEPTH = 3;

    @Desc("Stable id for this carving entry")
    private String id = "";

    @Desc("Enable or disable this carving entry")
    private boolean enabled = true;

    @ResourceRef(PackBiome.class)
    @Desc("Cave biome to apply when world Y falls within worldYRange")
    private String biome = "";

    @Desc("Absolute world Y bounds where this carving entry applies")
    private Range worldYRange = Range.of(DEFAULT_WORLD_Y_MIN, DEFAULT_WORLD_Y_MAX);

    @Desc("If this carving entry has child carving entries, this controls how small those child carving patches are.")
    private double childShrinkFactor = DEFAULT_CHILD_SHRINK_FACTOR;

    @Desc("If this carving entry has child carving entries, this controls the shape pattern used to pick them.")
    private NoiseStyle childStyle = NoiseStyle.of("CELLULAR_IRIS_DOUBLE");

    @Desc("What portions of this entry turn into: the child noise picks along this list in the order it is written. List this entry's own id where it should stay itself; an entry that is not listed does not stay anywhere. Child ids can point back to parent ids to create cycles; recursion is bounded by childRecursionDepth.")
    private List<CarvingChild> children = List.of();

    @Desc("Maximum recursion depth when resolving child carving entries from this entry.")
    private int childRecursionDepth = DEFAULT_CHILD_RECURSION_DEPTH;

    public CarvingEntry() {
    }

    public String id() {
        return id == null ? "" : id;
    }

    public boolean enabled() {
        return enabled;
    }

    public String biome() {
        return biome == null ? "" : biome;
    }

    public Range worldYRange() {
        return worldYRange;
    }

    public double childShrinkFactor() {
        return childShrinkFactor;
    }

    public NoiseStyle childStyle() {
        return childStyle;
    }

    public List<CarvingChild> children() {
        return children == null ? List.of() : Collections.unmodifiableList(children);
    }

    public int childRecursionDepth() {
        return childRecursionDepth;
    }
}
