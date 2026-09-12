/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.protocol.IrisMessage;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class IrisCursorResolverTest {
    @Test
    public void resolvesBiomeRegionCaveHeightAndDimensionKeys() {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey("iris:plains");
        IrisRegion region = new IrisRegion();
        region.setLoadKey("iris:temperate");
        IrisBiome cave = new IrisBiome();
        cave.setLoadKey("iris:lush_cave");
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        Engine engine = engine(biome, region, cave, 72, -64, dimension);

        IrisMessage.CursorInfo info = IrisCursorResolver.resolve(engine, 100, -200);

        assertEquals(100, info.blockX());
        assertEquals(-200, info.blockZ());
        assertEquals("iris:plains", info.biomeKey());
        assertEquals("iris:temperate", info.regionKey());
        assertEquals("iris:lush_cave", info.caveBiomeKey());
        assertEquals(8, info.height());
        assertEquals("overworld", info.dimensionKey());
    }

    @Test
    public void missingCaveAndNullKeysCollapseToEmptyStrings() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        Engine engine = engine(null, null, null, 0, -64, dimension);

        IrisMessage.CursorInfo info = IrisCursorResolver.resolve(engine, 0, 0);

        assertEquals("", info.biomeKey());
        assertEquals("", info.regionKey());
        assertEquals("", info.caveBiomeKey());
        assertEquals("overworld", info.dimensionKey());
    }

    private static Engine engine(IrisBiome biome, IrisRegion region, IrisBiome cave, int height, int minHeight, IrisDimension dimension) {
        return (Engine) Proxy.newProxyInstance(Engine.class.getClassLoader(), new Class[]{Engine.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getSurfaceBiome" -> biome;
            case "getRegion" -> region;
            case "getCaveBiome" -> cave;
            case "getHeight" -> height;
            case "getMinHeight" -> minHeight;
            case "getDimension" -> dimension;
            case "toString" -> "proxyEngine";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultReturn(method.getReturnType());
        });
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == boolean.class) {
            return false;
        }
        return null;
    }
}
