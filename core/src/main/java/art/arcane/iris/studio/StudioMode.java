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

package art.arcane.iris.studio;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.studio.generation.BiomeBuffetGenerator;
import art.arcane.iris.studio.generation.ObjectStudioGenerator;

@Description("Represents a studio mode")
public enum StudioMode {
    @Description("Installs no studio generator; the dimension generates normally.")
    NORMAL,

    @Description("Debug layout: every biome in the dimension on a square grid, one chunk per biome cell, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_1x1,

    @Description("Debug layout: every biome on a square grid with 3x3-chunk cells per biome, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_3x3,

    @Description("Debug layout: every biome on a square grid with 5x5-chunk cells per biome, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_5x5,

    @Description("Debug layout: every biome on a square grid with 9x9-chunk cells per biome, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_9x9,

    @Description("Debug layout: every biome on a square grid with 18x18-chunk cells per biome, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_18x18,

    @Description("Debug layout: every biome on a square grid with 36x36-chunk cells per biome, barrier floor past the last biome. Bukkit studio worlds only.")
    BIOME_BUFFET_36x36,

    @Description("Deprecated: generates exactly like NORMAL and will be removed in a future release.")
    REGION_BUFFET,

    @Description("Replaces terrain with the object studio: a flat polished-deepslate floor laying every pack object out on framed, end-rod-marked grid plinths. Bukkit studio worlds only.")
    OBJECT_BUFFET;

    public int biomeSizeChunks() {
        return switch (this) {
            case BIOME_BUFFET_1x1 -> 1;
            case BIOME_BUFFET_3x3 -> 3;
            case BIOME_BUFFET_5x5 -> 5;
            case BIOME_BUFFET_9x9 -> 9;
            case BIOME_BUFFET_18x18 -> 18;
            case BIOME_BUFFET_36x36 -> 36;
            default -> 0;
        };
    }

    public void inject(BukkitChunkGenerator c) {
        switch (this) {
            case NORMAL, REGION_BUFFET -> c.setStudioGenerator(null);
            case BIOME_BUFFET_1x1, BIOME_BUFFET_3x3, BIOME_BUFFET_5x5,
                 BIOME_BUFFET_9x9, BIOME_BUFFET_18x18, BIOME_BUFFET_36x36 ->
                    c.setStudioGenerator(new BiomeBuffetGenerator(c.getEngine()));
            case OBJECT_BUFFET -> c.setStudioGenerator(new ObjectStudioGenerator(c.getEngine()));
        }
    }
}
