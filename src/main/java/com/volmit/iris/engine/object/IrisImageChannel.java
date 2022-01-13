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

@Desc("Determines a derived channel of an image to read")
public enum IrisImageChannel {
    @Desc("The red channel of the image")
    RED,
    @Desc("Thge green channel of the image")
    GREEN,
    @Desc("The blue channel of the image")
    BLUE,
    @Desc("The saturation as a channel of the image")
    SATURATION,
    @Desc("The hue as a channel of the image")
    HUE,
    @Desc("The brightness as a channel of the image")
    BRIGHTNESS,
    @Desc("The composite of RGB as a channel of the image. Takes the average channel value (adding)")
    COMPOSITE_ADD_RGB,
    @Desc("The composite of RGB as a channel of the image. Multiplies the channels")
    COMPOSITE_MUL_RGB,
    @Desc("The composite of RGB as a channel of the image. Picks the highest channel")
    COMPOSITE_MAX_RGB,
    @Desc("The composite of HSB as a channel of the image Takes the average channel value (adding)")
    COMPOSITE_ADD_HSB,
    @Desc("The composite of HSB as a channel of the image Multiplies the channels")
    COMPOSITE_MUL_HSB,
    @Desc("The composite of HSB as a channel of the image Picks the highest channel")
    COMPOSITE_MAX_HSB,
    @Desc("The raw value as a channel (probably doesnt look very good)")
    RAW
}
