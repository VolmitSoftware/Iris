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

package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.noise.NoiseGenerator;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapValidationException;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapType;

public class ImageNoise implements NoiseGenerator {
    private final CompiledIrisImageMap compiled;

    public ImageNoise(IrisData data, String imageMapKey) {
        IrisImageMap definition = data.getImageMapLoader().load(imageMapKey);
        if (definition == null) {
            throw new IrisImageMapValidationException("Missing image-map resource '" + imageMapKey + "'");
        }
        if (definition.getType() == IrisImageMapType.COLOR_MAP) {
            throw new IrisImageMapValidationException(
                    "Generator-style image-map resource '" + imageMapKey + "' must produce normalized scalar data"
            );
        }
        IrisImage image = data.getImageLoader().load(definition.getSource());
        if (image == null) {
            throw new IrisImageMapValidationException(
                    "Image-map resource '" + imageMapKey + "' references missing or invalid PNG '"
                            + definition.getSource() + "'"
            );
        }
        try {
            compiled = CompiledIrisImageMap.compile(definition, image);
        } finally {
            data.getImageLoader().unload(definition.getSource());
        }
    }

    public String getContentHash() {
        return compiled.getContentHash();
    }

    @Override
    public double noise(double x) {
        return noise(x, 0);
    }

    @Override
    public double noise(double x, double z) {
        return compiled.sampleNormalized(x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return noise(x, z + y);
    }
}
