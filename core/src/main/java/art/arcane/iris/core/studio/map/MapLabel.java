/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;

/**
 * A biome or region as the map shows it.
 *
 * @param key   the pack key, such as {@code plains/meadow}
 * @param color its colour on the map, as {@code 0xRRGGBB}
 */
public record MapLabel(Kind kind, String key, String name, int color) {
    private static final int RGB = 0xFFFFFF;

    public static MapLabel of(Engine engine, IrisBiome biome, RenderType colorType) {
        return new MapLabel(Kind.BIOME, biome.getLoadKey(), biome.getName(), biome.getColor(engine, colorType).getRGB() & RGB);
    }

    public static MapLabel of(Engine engine, IrisRegion region) {
        return new MapLabel(Kind.REGION, region.getLoadKey(), region.getName(),
                region.getColor(engine.getComplex(), RenderType.REGION).getRGB() & RGB);
    }

    public enum Kind {
        BIOME,
        REGION
    }
}
