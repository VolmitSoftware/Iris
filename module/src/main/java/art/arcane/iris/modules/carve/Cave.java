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
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Range;
import com.volmit.config.ResourceRef;
import com.volmit.world.PackBiome;

import java.util.Optional;

/**
 * One kind of cave, a file of a pack's {@code caves/} folder that dimensions, regions and biomes list under {@code caves}.
 * It carves tunnels, caverns or both, within a depth range and under the surface rules it sets.
 */
@Desc("One kind of cave: tunnels, caverns or both, with where they may occur.")
public final class Cave {
    @Desc("Winding tunnels. Leave out for a cave without tunnels.")
    private CaveTunnel tunnel;

    @Desc("Large open rooms. Leave out for a cave without caverns.")
    private CaveCavern cavern;

    @Desc("World Y range this cave occurs in. Leave out to allow every height.")
    private Range depth;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Blocks over which the cave narrows to nothing at both ends of its depth range.")
    private int fade = 12;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Blocks of rock kept between the cave and the surface. Ignored when opensToSurface is set.")
    private int surfaceClearance = 8;

    @Desc("Let the cave cut through the surface, which makes entrances wherever it reaches the top.")
    private boolean opensToSurface = false;

    @ResourceRef(PackBiome.class)
    @Desc("Cave biome that paints this cave's walls. Leave out to use the cave biome of the place.")
    private String biome = "";

    public Cave() {
    }

    public Optional<CaveTunnel> tunnel() {
        return Optional.ofNullable(tunnel);
    }

    public Optional<CaveCavern> cavern() {
        return Optional.ofNullable(cavern);
    }

    public Optional<Range> depth() {
        return Optional.ofNullable(depth);
    }

    public int fade() {
        return fade;
    }

    public int surfaceClearance() {
        return surfaceClearance;
    }

    public boolean opensToSurface() {
        return opensToSurface;
    }

    public String biome() {
        return biome == null ? "" : biome;
    }
}
