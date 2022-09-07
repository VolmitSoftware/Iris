package com.volmit.iris.util;

import art.arcane.source.NoisePlane;
import art.arcane.spatial.hunk.Hunk;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HunkedNoisePlane implements NoisePlane {
    private final Hunk<Double> noise;

    @Override
    public double noise(double x, double y, double z) {
        Double d = noise.get(Math.floorMod((int)x, noise.getWidth()), Math.floorMod((int)y, noise.getHeight()), Math.floorMod((int)z, noise.getDepth()));

        return d != null ? d : 0;
    }
}
