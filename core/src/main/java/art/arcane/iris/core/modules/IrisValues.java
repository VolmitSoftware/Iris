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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.IrisStyledRange;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.structure.IrisStructureYBand;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Range;
import com.volmit.noise.StyledRange;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.StructureStilts;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.YBand;
import com.volmit.world.BlockPosition;

import java.util.Map;
import java.util.Optional;

/**
 * Iris's own classes for IrisApi's value types. Each pair reads the same pack JSON, so Iris resolves a value the way
 * it resolves its own class (a {@link BlockSpec} as an {@link IrisBlockData}, a noise style as an
 * {@link IrisGeneratorStyle}) and studio describes the value with Iris's own description of that class.
 */
public final class IrisValues {
    private static final Map<String, Class<?>> IRIS_CLASSES = Map.ofEntries(
            Map.entry(BlockSpec.class.getName(), IrisBlockData.class),
            Map.entry(PaletteSpec.class.getName(), IrisMaterialPalette.class),
            Map.entry(com.volmit.noise.NoiseStyle.class.getName(), IrisGeneratorStyle.class),
            Map.entry(StyledRange.class.getName(), IrisStyledRange.class),
            Map.entry(Range.class.getName(), IrisRange.class),
            Map.entry(BlockPosition.class.getName(), IrisPosition.class),
            Map.entry(StructureTerrain.class.getName(), IrisStructureTerrain.class),
            Map.entry(StructureStilts.class.getName(), IrisStructureStiltSettings.class),
            Map.entry(YBand.class.getName(), IrisStructureYBand.class),
            Map.entry(JigsawOverrides.class.getName(), IrisJigsawConfiguration.class));
    private static final Gson SAME_JSON = new Gson();

    private IrisValues() {
    }

    /** The Iris class that reads the same JSON as the IrisApi value type named {@code apiTypeName}, if it is one. */
    public static Optional<Class<?>> irisClassOf(String apiTypeName) {
        return Optional.ofNullable(IRIS_CLASSES.get(apiTypeName));
    }

    /** The IrisApi value types and the Iris classes that read the same JSON. */
    static Map<String, Class<?>> irisClasses() {
        return IRIS_CLASSES;
    }

    /** {@code spec} as the block data Iris resolves: its block, properties, weight, backup and tile data. */
    static IrisBlockData blockData(BlockSpec spec) {
        IrisBlockData block = new IrisBlockData(spec.block());
        block.setData(irisMap(spec.data()));
        block.setWeight(spec.weight());
        block.setBackup(spec.backup().map(IrisValues::blockData).orElse(null));
        block.setTileData(irisMap(spec.tileData()));
        return block;
    }

    /** {@code spec} as the material palette Iris picks blocks from: its noise style, zoom and weighted blocks. */
    static IrisMaterialPalette materialPalette(PaletteSpec spec) {
        KList<IrisBlockData> blocks = new KList<>();
        spec.palette().forEach(block -> blocks.add(blockData(block)));
        return new IrisMaterialPalette().setStyle(generatorStyle(spec.style())).setZoom(spec.zoom()).setPalette(blocks);
    }

    /** {@code terrain} as the terrain fitting Iris's native structure placement applies. */
    public static IrisStructureTerrain structureTerrain(StructureTerrain terrain) {
        return sameJson(terrain, IrisStructureTerrain.class);
    }

    /** {@code stilts} as the foundation columns Iris's native structure placement builds. */
    public static IrisStructureStiltSettings structureStilts(StructureStilts stilts) {
        return sameJson(stilts, IrisStructureStiltSettings.class);
    }

    public static IrisStructureYBand yBand(YBand band) {
        return sameJson(band, IrisStructureYBand.class);
    }

    /** {@code overrides} as the jigsaw configuration Iris applies to a platform jigsaw definition. */
    public static IrisJigsawConfiguration jigsawConfiguration(JigsawOverrides overrides) {
        return sameJson(overrides, IrisJigsawConfiguration.class);
    }

    /** The structure fit classes are plain values whose JSON is the API type's, so the conversion is a JSON round trip. */
    private static <T> T sameJson(Object apiValue, Class<T> irisClass) {
        return SAME_JSON.fromJson(SAME_JSON.toJsonTree(apiValue), irisClass);
    }

    /** Iris's maps hold no null values; a JSON null in a map reads as absent, as it does for Iris's own fields. */
    private static KMap<String, Object> irisMap(Map<String, ?> values) {
        KMap<String, Object> map = new KMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                map.put(key, value);
            }
        });
        return map;
    }

    /** {@code style} as the generator style Iris builds noise from; a style name Iris does not know is flat. */
    static IrisGeneratorStyle generatorStyle(com.volmit.noise.NoiseStyle style) {
        IrisGeneratorStyle iris = new IrisGeneratorStyle(irisStyle(style.style()));
        iris.setCellularFrequency(style.cellularFrequency());
        iris.setCellularZoom(style.cellularZoom());
        iris.setZoom(style.zoom());
        iris.setExpression(style.expression().orElse(null));
        iris.setImageMap(style.imageMap().orElse(null));
        iris.setMultiplier(style.multiplier());
        iris.setFracture(style.fracture().map(IrisValues::generatorStyle).orElse(null));
        iris.setExponent(style.exponent());
        iris.setCacheSize(style.cacheSize());
        return iris;
    }

    private static NoiseStyle irisStyle(String name) {
        try {
            return NoiseStyle.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return NoiseStyle.FLAT;
        }
    }
}
