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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.core.studio.map.MapLabel;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Saved biome keys as the map shows biomes. A key the open pack saves its custom biomes under, such as
 * {@code overworld:estranged_candlewood}, gets that biome's map colour and name; any other key a steady colour of its own.
 */
public final class SavedBiomeLabels {
    private static final int RGB = 0xFFFFFF;
    private static final float OTHER_SATURATION = 0.45F;
    private static final float OTHER_BRIGHTNESS = 0.78F;

    private final Map<String, MapLabel> byKey;

    private SavedBiomeLabels(Map<String, MapLabel> byKey) {
        this.byKey = byKey;
    }

    public static SavedBiomeLabels of(Engine engine) {
        Map<String, MapLabel> byKey = new HashMap<>();
        String namespace = engine.getDimension().getLoadKey();
        for (IrisBiome biome : engine.getAllBiomes()) {
            if (!biome.isCustom()) {
                continue;
            }
            MapLabel label = MapLabel.of(engine, biome, RenderType.BIOME);
            for (IrisBiomeCustom custom : biome.getCustomDerivitives()) {
                byKey.putIfAbsent(namespace + ":" + custom.getId(), label);
            }
        }
        return new SavedBiomeLabels(byKey);
    }

    public List<MapLabel> labelsOf(List<String> keys) {
        return keys.stream().map(this::labelOf).toList();
    }

    MapLabel labelOf(String key) {
        MapLabel known = byKey.get(key);
        return known != null ? known : new MapLabel(MapLabel.Kind.BIOME, key, displayName(key), steadyColor(key));
    }

    static String displayName(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return Arrays.stream(path.split("[_/]"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static int steadyColor(String key) {
        float hue = Math.floorMod(key.hashCode(), 360) / 360F;
        return Color.HSBtoRGB(hue, OTHER_SATURATION, OTHER_BRIGHTNESS) & RGB;
    }
}
