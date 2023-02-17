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

package com.volmit.iris.core.gui.components;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeGeneratorLink;
import com.volmit.iris.util.interpolation.IrisInterpolation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.BiFunction;

public class IrisRenderer {
    private final Engine renderer;

    public IrisRenderer(Engine renderer) {
        this.renderer = renderer;
    }

    public BufferedImage render(double sx, double sz, double size, int resolution, RenderType currentType) {
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        BiFunction<Double, Double, Integer> colorFunction = (d, dx) -> Color.black.getRGB();

        switch (currentType) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD ->
                    colorFunction = (x, z) -> renderer.getComplex().getTrueBiomeStream().get(x, z).getColor(renderer, currentType).getRGB();
            case BIOME_LAND ->
                    colorFunction = (x, z) -> renderer.getComplex().getLandBiomeStream().get(x, z).getColor(renderer, currentType).getRGB();
            case BIOME_SEA ->
                    colorFunction = (x, z) -> renderer.getComplex().getSeaBiomeStream().get(x, z).getColor(renderer, currentType).getRGB();
            case REGION ->
                    colorFunction = (x, z) -> renderer.getComplex().getRegionStream().get(x, z).getColor(renderer.getComplex(), currentType).getRGB();
            case CAVE_LAND ->
                    colorFunction = (x, z) -> renderer.getComplex().getCaveBiomeStream().get(x, z).getColor(renderer, currentType).getRGB();
            case HEIGHT ->
                    colorFunction = (x, z) -> Color.getHSBColor(renderer.getComplex().getHeightStream().get(x, z).floatValue(), 100, 100).getRGB();
            case CONTINENT ->
                    colorFunction = (x, z) -> {
                        IrisBiome b = renderer.getBiome((int) Math.round(x), renderer.getMaxHeight() - 1, (int) Math.round(z));
                        IrisBiomeGeneratorLink g = b.getGenerators().get(0);
                        Color c;
                        if (g.getMax() <= 0) {
                            // Max is below water level, so it is most likely an ocean biome
                            c = Color.BLUE;
                        } else if (g.getMin() < 0) {
                            // Min is below water level, but max is not, so it is most likely a shore biome
                            c = Color.YELLOW;
                        } else {
                            // Both min and max are above water level, so it is most likely a land biome
                            c = Color.GREEN;
                        }
                        return c.getRGB();
                    };
        }

        double x, z;
        int i, j;
        for (i = 0; i < resolution; i++) {
            x = IrisInterpolation.lerp(sx, sx + size, (double) i / (double) (resolution));

            for (j = 0; j < resolution; j++) {
                z = IrisInterpolation.lerp(sz, sz + size, (double) j / (double) (resolution));
                image.setRGB(i, j, colorFunction.apply(x, z));
            }
        }

        return image;
    }
}
