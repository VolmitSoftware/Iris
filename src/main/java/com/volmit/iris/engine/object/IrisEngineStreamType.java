/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.EngineFramework;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.stream.ProceduralStream;

import java.util.function.Function;

@Desc("Represents a stream from the engine")
public enum IrisEngineStreamType {
    @Desc("Represents the given slope at the x, z coordinates")
    SLOPE((f) -> f.getComplex().getSlopeStream()),

    @Desc("Represents the real terrain height ignoring fluid, this includes carving, caves & noise features into this stream.")
    TRUE_HEIGHT((f) -> f.getComplex().getTrueHeightStream().forceDouble()),

    @Desc("Represents the real terrain height including fluid, this includes carving, caves & noise features into this stream.")
    TRUE_HEIGHT_OR_FLUID((f) -> f.getComplex().getTrueHeightStream().forceDouble().max(f.getComplex().getFluidHeight())),

    @Desc("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves.")
    HEIGHT((f) -> f.getComplex().getHeightStream()),

    @Desc("Represents the base generator height at the given position. This includes only the biome generators / interpolation and noise features but does not include carving, caves. with Max(height, fluidHeight).")
    HEIGHT_OR_FLUID((f) -> f.getComplex().getHeightFluidStream()),

    @Desc("Represents the overlay noise generators summed (dimension setting)")
    OVERLAY_NOISE((f) -> f.getComplex().getOverlayStream()),

    @Desc("Represents the overlay noise generators summed (dimension setting)")
    HEIGHT_NO_FEATURES((f) -> f.getComplex().getHeightStreamNoFeatures()),

    @Desc("Represents the object chance clip. If a noise feature alters the object chance in this area this number will drop below 1. (100%)")
    OBJECT_CHANCE_CLIP((f) -> f.getComplex().getObjectChanceStream()),

    @Desc("Represents the noise style of regions")
    REGION_STYLE((f) -> f.getComplex().getRegionStyleStream()),

    @Desc("Represents the identity of regions. Each region has a unique number (very large numbers)")
    REGION_IDENTITY((f) -> f.getComplex().getRegionIdentityStream());

    private final Function<EngineFramework, ProceduralStream<Double>> getter;

    IrisEngineStreamType(Function<EngineFramework, ProceduralStream<Double>> getter) {
        this.getter = getter;
    }

    public ProceduralStream<Double> get(EngineFramework engine) {
        return getter.apply(engine);
    }
}
