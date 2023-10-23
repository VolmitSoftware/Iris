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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("color")
@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a color")
@Data
public class IrisSeed {
    @Desc("The seed to use")
    private long seed = 1337;

    @Desc("To calculate a seed Iris passes in it's natural seed for the current feature, then mixes it with your seed. Setting this to true ignores the parent seed and always uses your exact seed ignoring the input of Iris feature seeds. You can use this to match seeds on other generators.")
    private boolean ignoreNaturalSeedInput = false;

    public long getSeed(long seed) {
        return (seed * 47) + getSeed() + 29334667L;
    }

    public RNG rng(long inseed) {
        return new RNG(getSeed(inseed));
    }
}
