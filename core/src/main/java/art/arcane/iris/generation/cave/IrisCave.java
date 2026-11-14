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

package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.documentation.Description;
import com.google.gson.GsonBuilder;
import lombok.EqualsAndHashCode;

import java.util.Optional;

/**
 * One kind of cave, a file of a pack's {@code caves/} folder that dimensions, regions and biomes list under
 * {@code caves}. It carves tunnels, caverns or both, within a depth range and under the surface rules it sets. Every
 * number that shapes the cave is a {@link CaveValue}, so it may vary across the world.
 */
@EqualsAndHashCode(callSuper = false)
@Description("One kind of cave: tunnels, caverns or both, with where they may occur.")
public final class IrisCave extends IrisRegistrant {
    /** Without a style, a value of the whole cave changes over about this many blocks. */
    public static final int VALUE_CHANGE_BLOCKS = 256;
    static final String FOLDER_NAME = "caves";

    @Description("Winding tunnels. Leave out for a cave without tunnels.")
    private CaveTunnel tunnel;

    @Description("Large open rooms. Leave out for a cave without caverns.")
    private CaveCavern cavern;

    @Description("World Y range this cave occurs in. Leave out to allow every height.")
    private CaveDepth depth;

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Blocks over which the cave narrows to nothing at both ends of its depth range, 0 to 128. Default 12. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue fade = CaveValue.of(12);

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Blocks of rock kept between the cave and the surface, 0 to 128. Default 8. Ignored when opensToSurface is set. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue surfaceClearance = CaveValue.of(8);

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Blocks over which the cave closes under the rock it keeps, instead of ending in a flat ceiling, 0 to 128. Default 8. Ignored where it opens to the surface. A range changes over about "
            + VALUE_CHANGE_BLOCKS + " blocks.")
    private CaveValue ceilingFade = CaveValue.of(8);

    @Description("Let the cave cut through the surface, which makes entrances wherever it reaches the top.")
    private boolean opensToSurface = false;

    @RegistryListResource(IrisBiome.class)
    @Description("Cave biome that paints this cave's walls. Leave out to use the cave biome of the place.")
    private String biome = "";

    @Description("Allow pockets of the dimension's fluid in this cave below the dimension's fluid height.")
    private boolean allowFluid = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum depth below the surface before this cave may hold fluid.")
    private int fluidMinDepthBelowSurface = 12;

    @Description("Only hold fluid where the cave has a solid floor under it, which keeps fluid from running out of the cave.")
    private boolean fluidRequiresFloor = true;

    @Description("Allow lava below the dimension's cave lava height. When false that depth stays air.")
    private boolean allowLava = true;

    public IrisCave() {
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

    public boolean allowFluid() {
        return allowFluid;
    }

    public int fluidMinDepthBelowSurface() {
        return Math.max(0, fluidMinDepthBelowSurface);
    }

    public boolean fluidRequiresFloor() {
        return fluidRequiresFloor;
    }

    public boolean allowLava() {
        return allowLava;
    }

    @Override
    public String getFolderName() {
        return FOLDER_NAME;
    }

    @Override
    public String getTypeName() {
        return "Cave";
    }

    @Override
    public void registerTypeAdapters(GsonBuilder builder) {
        CaveJson.register(builder);
    }
}
