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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.data.VanillaBiomeColors;
import art.arcane.volmlib.util.inventorygui.RandomColor;

import java.awt.Color;

/**
 * Map / render color resolution for {@link IrisBiome}. IrisBiome is Gson deserialized from pack
 * JSON, so its fields stay put and only the behavior lives here.
 */
final class IrisBiomeColorRenderer {
    private IrisBiomeColorRenderer() {
    }

    static Color getColor(IrisBiome biome, Engine engine, RenderType type) {
        switch (type) {
            case BIOME, HEIGHT, CAVE_LAND, REGION, BIOME_SEA, BIOME_LAND, RIVER -> {
                return biome.getCacheColor().aquire(() -> {
                    if (biome.getColor() == null) {
                        RandomColor randomColor = new RandomColor(biome.getName().hashCode());
                        String vanillaKey = biome.getVanillaDerivativeKey();
                        RandomColor.Color col = vanillaKey == null ? null : VanillaBiomeColors.getColorType(vanillaKey);
                        if (col == null) {
                            IrisLogging.warn("No vanilla biome found for " + biome.getName());
                            return new Color(randomColor.randomColor());
                        }
                        RandomColor.Luminosity lum = VanillaBiomeColors.getColorLuminosity(vanillaKey);
                        RandomColor.SaturationType sat = VanillaBiomeColors.getColorSaturation(vanillaKey);
                        int newColorI = randomColor.randomColor(col, col == RandomColor.Color.MONOCHROME ? RandomColor.SaturationType.MONOCHROME : sat, lum);

                        return new Color(newColorI);
                    }

                    try {
                        return Color.decode(biome.getColor());
                    } catch (NumberFormatException e) {
                        IrisLogging.warn("Could not parse color \"" + biome.getColor() + "\" for biome " + biome.getName());
                        return new Color(new RandomColor(biome.getName().hashCode()).randomColor());
                    }
                });
            }
            case OBJECT_LOAD -> {
                return biome.getCacheColorObjectDensity().aquire(() -> {
                    double density = 0;

                    for (IrisObjectPlacement i : biome.getObjects()) {
                        density += i.getDensity() * i.getChance();
                    }

                    return Color.getHSBColor(0.225f, (float) (density / engine.getMaxBiomeObjectDensity()), 1f);
                });
            }
            case DECORATOR_LOAD -> {
                return biome.getCacheColorDecoratorLoad().aquire(() -> {
                    double density = 0;

                    for (IrisDecorator i : biome.getDecorators()) {
                        density += i.getChance() * Math.min(1, i.getStackMax()) * 256;
                    }

                    return Color.getHSBColor(0.41f, (float) (density / engine.getMaxBiomeDecoratorDensity()), 1f);
                });
            }
            case LAYER_LOAD -> {
                return biome.getCacheColorLayerLoad().aquire(() -> Color.getHSBColor(0.625f, (float) (biome.getLayers().size() / engine.getMaxBiomeLayerDensity()), 1f));
            }
        }

        return Color.black;
    }
}
