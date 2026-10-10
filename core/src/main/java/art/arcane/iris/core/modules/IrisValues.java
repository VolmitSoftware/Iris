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
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Range;
import com.volmit.noise.StyledRange;
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
            Map.entry(BlockPosition.class.getName(), IrisPosition.class));

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

    /**
     * {@code style} as the generator style Iris builds noise from; a style name Iris does not know is flat. For behaviour that
     * reads Iris's generator itself, such as the cave carvers' signed 3D reads, which {@code PackResources.noise()} does not offer.
     */
    public static IrisGeneratorStyle generatorStyle(com.volmit.noise.NoiseStyle style) {
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

    /** {@code range} as the range Iris reads the same JSON into. */
    public static IrisRange range(Range range) {
        return new IrisRange(range.min(), range.max());
    }

    /** {@code range} as the styled range Iris reads the same JSON into. */
    public static IrisStyledRange styledRange(StyledRange range) {
        return new IrisStyledRange(range.min(), range.max(), generatorStyle(range.style()));
    }

    private static NoiseStyle irisStyle(String name) {
        try {
            return NoiseStyle.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return NoiseStyle.FLAT;
        }
    }
}
