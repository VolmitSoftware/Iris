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

import art.arcane.iris.modules.carve.CaveValue.Bounds;
import com.volmit.config.Desc;
import com.volmit.config.ResourceRef;
import com.volmit.world.PackBiome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One kind of cave, a file of a pack's {@code caves/} folder that dimensions, regions and biomes list under {@code caves}.
 * It carves tunnels, caverns or both, within a depth range and under the surface rules it sets. Every number that
 * shapes the cave is a {@link CaveValue}, so it may vary across the world.
 */
@Desc("One kind of cave: tunnels, caverns or both, with where they may occur.")
public final class Cave {
    /** Without a style, a value of the whole cave changes over about this many blocks. */
    public static final int VALUE_CHANGE_BLOCKS = 256;
    private static final Bounds FADE_BLOCKS = new Bounds(0, 128);
    private static final Bounds SURFACE_CLEARANCE_BLOCKS = new Bounds(0, 128);
    private static final Bounds CEILING_FADE_BLOCKS = new Bounds(0, 128);

    @Desc("Winding tunnels. Leave out for a cave without tunnels.")
    private CaveTunnel tunnel;

    @Desc("Large open rooms. Leave out for a cave without caverns.")
    private CaveCavern cavern;

    @Desc("World Y range this cave occurs in. Leave out to allow every height.")
    private CaveDepth depth;

    @Desc("Blocks over which the cave narrows to nothing at both ends of its depth range, 0 to 128. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue fade = CaveValue.of(12);

    @Desc("Blocks of rock kept between the cave and the surface, 0 to 128. Ignored when opensToSurface is set. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue surfaceClearance = CaveValue.of(8);

    @Desc("Blocks over which the cave closes under the rock it keeps, instead of ending in a flat ceiling, 0 to 128. Ignored where it opens to the surface. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue ceilingFade = CaveValue.of(8);

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

    public Optional<CaveDepth> depth() {
        return Optional.ofNullable(depth);
    }

    public CaveValue fade() {
        return fade;
    }

    public CaveValue surfaceClearance() {
        return surfaceClearance;
    }

    public CaveValue ceilingFade() {
        return ceilingFade;
    }

    public boolean opensToSurface() {
        return opensToSurface;
    }

    public String biome() {
        return biome == null ? "" : biome;
    }

    /** Each value of the cave outside the numbers its key allows, such as {@code fade is 200; it must be at most 128}. */
    public List<String> valuesOutOfBounds() {
        List<String> problems = new ArrayList<>();
        fade.requireWithin(FADE_BLOCKS, "fade", problems);
        surfaceClearance.requireWithin(SURFACE_CLEARANCE_BLOCKS, "surfaceClearance", problems);
        ceilingFade.requireWithin(CEILING_FADE_BLOCKS, "ceilingFade", problems);
        tunnel().ifPresent(caveTunnel -> caveTunnel.requireValuesWithinBounds("tunnel", problems));
        cavern().ifPresent(caveCavern -> caveCavern.requireValuesWithinBounds("cavern", problems));
        return problems;
    }
}
