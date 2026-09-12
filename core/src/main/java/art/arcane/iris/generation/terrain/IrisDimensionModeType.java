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

package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.mode.ModeEnclosure;
import art.arcane.iris.generation.mode.ModeIslands;
import art.arcane.iris.generation.mode.ModeOverworld;
import art.arcane.iris.generation.mode.ModeSuperFlat;
import art.arcane.volmlib.util.documentation.Description;

import java.util.function.Function;

@Description("The type of dimension this is")
public enum IrisDimensionModeType {
    @Description("Typical dimensions. Has a fluid height, and all features of a biome based world")
    OVERWORLD(ModeOverworld::new),

    @Description("Ultra fast, but very limited in features. Only supports terrain & biomes. No decorations, mobs, objects, or anything of the sort!")
    SUPERFLAT(ModeSuperFlat::new),

    @Description("Like the nether, a ceiling & floor carved out")
    ENCLOSURE(ModeEnclosure::new),

    @Description("Floating islands of terrain")
    ISLANDS(ModeIslands::new),
    ;
    private final Function<Engine, EngineMode> factory;

    IrisDimensionModeType(Function<Engine, EngineMode> factory) {
        this.factory = factory;
    }

    public EngineMode create(Engine e) {
        return factory.apply(e);
    }
}
