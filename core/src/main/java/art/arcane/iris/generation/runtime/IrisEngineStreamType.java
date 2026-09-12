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

package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.stream.ProceduralStream;
import java.util.function.Function;

@Description("Represents a stream from the engine")
public enum IrisEngineStreamType {
    @Description("Represents the given slope at the x, z coordinates")
    SLOPE((f) -> f.getComplex().getSlopeStream()),

    @Description("Represents terrain height before river incision and river biome replacement.")
    NATURAL_HEIGHT((f) -> f.getComplex().getNaturalHeightStream()),

    @Description("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves.")
    HEIGHT((f) -> f.getComplex().getHeightStream()),

    @Description("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves. with Max(height, fluidHeight).")
    HEIGHT_OR_FLUID((f) -> f.getComplex().getHeightFluidStream()),

    @Description("Represents the overlay noise generators summed (dimension setting)")
    OVERLAY_NOISE((f) -> f.getComplex().getOverlayStream()),

    @Description("Represents the noise style of regions")
    REGION_STYLE((f) -> f.getComplex().getRegionStyleStream()),

    @Description("Represents the identity of regions. Each region has a unique number (very large numbers)")
    REGION_IDENTITY((f) -> f.getComplex().getRegionIdentityStream()),

    @Description("Represents block distance from the nearest active river centerline.")
    RIVER_DISTANCE((f) -> f.getComplex().getRiverDistanceStream()),

    @Description("Represents the merged upstream flow carried by the active river reach.")
    RIVER_FLOW((f) -> f.getComplex().getRiverFlowStream()),

    @Description("Represents the normalized river terrain-incision weight.")
    RIVER_CARVE_WEIGHT((f) -> f.getComplex().getRiverCarveWeightStream()),

    @Description("Represents the solved river water-surface height.")
    RIVER_WATER_SURFACE((f) -> f.getComplex().getRiverWaterSurfaceStream());

    private final Function<Engine, ProceduralStream<Double>> getter;

    IrisEngineStreamType(Function<Engine, ProceduralStream<Double>> getter) {
        this.getter = getter;
    }

    public ProceduralStream<Double> get(Engine engine) {
        return getter.apply(engine);
    }
}
