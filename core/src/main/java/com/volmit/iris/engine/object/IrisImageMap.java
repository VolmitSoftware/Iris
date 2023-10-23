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

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("image-map")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an image map")
@Data
public class IrisImageMap {
    @RegistryListResource(IrisImage.class)
    @Desc("Define the png image to read in this noise map")
    private String image = "";

    @MinNumber(1)
    @Desc("The amount of distance a single pixel is when reading this map, reading x=13, would still read pixel 0 if the scale is 32. You can zoom this externally through noise styles for zooming out.")
    private double coordinateScale = 32;

    @Desc("The interpolation method if the coordinateScale is greater than 1. This blends the image into noise. For nearest neighbor, use NONE.")
    private InterpolationMethod interpolationMethod = InterpolationMethod.BILINEAR_STARCAST_6;

    @Desc("The channel of the image to read from. This basically converts image data into a number betwen 0 to 1 per pixel using a certain 'channel/filter'")
    private IrisImageChannel channel = IrisImageChannel.COMPOSITE_ADD_HSB;

    @Desc("Invert the channel input")
    private boolean inverted = false;

    @Desc("Tile the image coordinates")
    private boolean tiled = false;

    @Desc("Center 0,0 to the center of the image instead of the top left.")
    private boolean centered = true;

    private transient AtomicCache<IrisImage> imageCache = new AtomicCache<IrisImage>();

    public double getNoise(IrisData data, int x, int z) {
        IrisImage i = imageCache.aquire(() -> data.getImageLoader().load(image));
        if (i == null) {
            Iris.error("NULL IMAGE FOR " + image);
            return 0;
        }

        return IrisInterpolation.getNoise(interpolationMethod, x, z, coordinateScale, (xx, zz) -> rawNoise(i, xx, zz));
    }

    private double rawNoise(IrisImage i, double x, double z) {
        x /= coordinateScale;
        z /= coordinateScale;

        // X and Z are now scaled to the image

        // Add half the image width & height if centered
        if (isCentered()) {
            x += i.getWidth() / 2D;
            z += i.getHeight() / 2D;
        }

        // If tiled modulo over width and height
        if (isTiled()) {
            x = x % i.getWidth();
            x = x < 0 ? x + i.getWidth() : x; // Fix java's negative modulo shit
            z = z % i.getHeight();
            z = z < 0 ? z + i.getHeight() : z; // Fix java's negative modulo shit
        }

        // Retrieve value from image
        double v = i.getValue(getChannel(), (int) x, (int) z);

        // Return value, or 1 - value if inverted (value is in double set [0, 1] so this will return [0, 1])
        return isInverted() ? 1D - v : v;
    }
}
