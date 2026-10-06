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

package art.arcane.iris.engine.object.procedural.ruin;

import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListBlockType;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("ruin-decorator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("An accent block applied to a generated ruin after it is built and eroded, such as moss carpets on broken tops, vines on the faces, or rubble scattered around the base.")
@Data
public class IrisRuinDecorator {
    @Desc("Where on the ruin this accent is placed. TOP sits on the highest block of each column, SURFACE clings to air-facing vertical faces, BASE_SCATTER rings the ground footprint around the base.")
    private IrisRuinDecoratorTarget target = IrisRuinDecoratorTarget.TOP;

    @Required
    @Desc("The block id to place, e.g. minecraft:moss_carpet or minecraft:vine. Ignored when palette is set.")
    @RegistryListBlockType
    private String block = "";

    @Desc("A noise-driven palette for this decorator. When set this overrides the single block, letting the accent mix blocks by noise. Resolved through IrisProceduralBlocks.resolve so the palette wins over the string.")
    private IrisMaterialPalette palette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance (0-1) per candidate position for this decorator to actually place. 1 covers every candidate, lower values leave sparse, patchy accents.")
    private double chance = 0.4;

    @MinNumber(1)
    @Desc("For the BASE_SCATTER target, how many blocks beyond the ruin footprint the scatter ring extends outward in each direction. Higher values fling rubble and growth further from the structure.")
    private int scatterRadius = 2;
}
