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

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.mode.ModeEnclosure;
import art.arcane.iris.engine.mode.ModeIslands;
import art.arcane.iris.engine.mode.ModeOverworld;
import art.arcane.iris.engine.mode.ModeSuperFlat;
import art.arcane.iris.engine.object.annotations.Desc;

import java.util.function.Function;

@Desc("The type of dimension this is")
public enum IrisDimensionModeType {
    @Desc("Typical dimensions. Has a fluid height, and all features of a biome based world")
    OVERWORLD(ModeOverworld::new),

    @Desc("Ultra fast, but very limited in features. Only supports terrain & biomes. No decorations, mobs, objects, or anything of the sort!")
    SUPERFLAT(ModeSuperFlat::new),

    @Desc("Like the nether, a ceiling & floor carved out")
    ENCLOSURE(ModeEnclosure::new),

    @Desc("Floating islands of terrain")
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
