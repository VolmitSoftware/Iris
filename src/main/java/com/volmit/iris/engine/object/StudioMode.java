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
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.engine.platform.studio.generators.BiomeBuffetGenerator;

import java.util.function.Consumer;

@Desc("Represents a studio mode")
public enum StudioMode {
    NORMAL(i -> i.setStudioGenerator(null)),
    BIOME_BUFFET_1x1(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 1))),
    BIOME_BUFFET_3x3(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 3))),
    BIOME_BUFFET_5x5(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 5))),
    BIOME_BUFFET_9x9(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 9))),
    BIOME_BUFFET_18x18(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 18))),
    BIOME_BUFFET_36x36(i -> i.setStudioGenerator(new BiomeBuffetGenerator(i.getEngine(), 36))),
    REGION_BUFFET(i -> i.setStudioGenerator(null)),
    OBJECT_BUFFET(i -> i.setStudioGenerator(null)),

    ;

    private final Consumer<BukkitChunkGenerator> injector;

    StudioMode(Consumer<BukkitChunkGenerator> injector) {
        this.injector = injector;
    }

    public void inject(BukkitChunkGenerator c) {
        injector.accept(c);
    }
}
