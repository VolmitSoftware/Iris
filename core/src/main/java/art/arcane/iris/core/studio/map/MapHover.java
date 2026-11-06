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

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBiome;

import java.nio.file.Path;
import java.util.List;

/**
 * What the map knows about the column under the cursor.
 *
 * @param biome        the base biome, as the biome view shows it; null where the stream has none, as for the others
 * @param vanillaBiome the platform biome the column gets, such as {@code minecraft:plains}
 * @param slopeDegrees how steep the terrain is, 0 for flat
 * @param biomeFile    the biome's file within the pack, such as {@code biomes/plains/meadow.json}; null when unknown
 * @param pickPath     how the column got the region and the biome the view shows, region first
 */
public record MapHover(double worldX, double worldZ, MapLabel biome, MapLabel caveBiome, MapLabel region,
                       String vanillaBiome, double height, double fluidHeight, double slopeDegrees,
                       String biomeFile, String regionFile, List<MapPickPath.PickStep> pickPath) {
    // IrisComplex measures slope as the height change over this many blocks.
    private static final double SLOPE_STREAM_RANGE_BLOCKS = 3D;

    /** In a region preview, the biome and region are the previewed ones; the rest is the world as it is. */
    public static MapHover sample(Engine engine, MapLayer layer, double worldX, double worldZ) {
        IrisComplex complex = engine.getComplex();
        IrisBiome biome = layer.previews()
                ? complex.surfacePickAsIfIn(layer.previewRegion(), worldX, worldZ).biome()
                : complex.getBaseBiomeStream().get(worldX, worldZ);
        IrisBiome caveBiome = complex.getCaveBiomeStream().get(worldX, worldZ);
        IrisRegion region = layer.previews() ? layer.previewRegion() : complex.getRegionStream().get(worldX, worldZ);
        PlatformBiome vanillaBiome = complex.getTrueBiomeDerivativeStream().get(worldX, worldZ);
        double slope = complex.getSlopeStream().getDouble(worldX, worldZ);
        IrisDimension dimension = engine.getDimension();
        return new MapHover(
                worldX,
                worldZ,
                biome == null ? null : MapLabel.of(engine, biome, RenderType.BIOME),
                caveBiome == null ? null : MapLabel.of(engine, caveBiome, RenderType.CAVE_LAND),
                region == null ? null : MapLabel.of(engine, region),
                vanillaBiome == null ? null : vanillaBiome.key(),
                complex.getHeightStream().getDouble(worldX, worldZ),
                dimension == null ? 0D : dimension.getFluidHeight(),
                Math.toDegrees(Math.atan(slope / SLOPE_STREAM_RANGE_BLOCKS)),
                packPath(engine, biome),
                packPath(engine, region),
                MapPickPath.explain(engine, layer, worldX, worldZ)
        );
    }

    private static String packPath(Engine engine, IrisRegistrant registrant) {
        if (registrant == null || registrant.getLoadFile() == null || engine.getData() == null) {
            return null;
        }
        Path pack = engine.getData().getDataFolder().toPath().toAbsolutePath();
        Path file = registrant.getLoadFile().toPath().toAbsolutePath();
        return file.startsWith(pack) ? pack.relativize(file).toString().replace('\\', '/') : null;
    }
}
