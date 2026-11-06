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
package art.arcane.iris.engine.procedural.part;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

import java.util.Collections;
import java.util.List;

@Desc("Blocks placed against the finished object, such as vines, moss, fruit or lanterns. Vines and lichen attach to the block they touch.")
public final class ProceduralDecoration {
    @Ref(Reference.BLOCK)
    @Desc("The block to place. Ignored when palette is set.")
    private String block = "minecraft:vine";

    @Desc("A noise palette to place.")
    private PaletteSpec palette;

    @Desc("The roles to decorate. Empty decorates every block.")
    private List<String> on = List.of();

    @Desc("Where on those blocks to place it.")
    private DecorationSpot at = DecorationSpot.SIDE;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance each spot gets one.")
    private double chance = 0.3;

    @Desc("How the chance is spread over the spots.")
    private PartPattern pattern = PartPattern.RANDOM;

    @MinNumber(1)
    @Desc("The size of the patches in blocks for the PATCHES pattern.")
    private double scale = 3;

    @Desc("How many blocks long a strand hangs for BELOW and BOTTOM.")
    private ProceduralValue length = ProceduralValue.constant(1);

    @Desc("How far from where the object stands GROUND decorations spread, in blocks.")
    private ProceduralValue radius = ProceduralValue.constant(3);

    public BlockChoice choice() {
        return new BlockChoice(block, palette, 0);
    }

    public List<String> on() {
        return on == null ? List.of() : Collections.unmodifiableList(on);
    }

    public DecorationSpot at() {
        return at == null ? DecorationSpot.SIDE : at;
    }

    public double chance() {
        return chance;
    }

    public PartPattern pattern() {
        return pattern == null ? PartPattern.RANDOM : pattern;
    }

    public double scale() {
        return scale;
    }

    public ProceduralValue length() {
        return length == null ? ProceduralValue.constant(1) : length;
    }

    public ProceduralValue radius() {
        return radius == null ? ProceduralValue.constant(3) : radius;
    }
}
