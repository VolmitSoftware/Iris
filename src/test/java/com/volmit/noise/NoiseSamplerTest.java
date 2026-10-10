package com.volmit.noise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoiseSamplerTest {
    private static final double EXACT = 0D;

    @Test
    void signedMapsTheUnitSampleToMinusOneAndOne() {
        NoiseSampler noise = new NoiseSampler() {
            public double sample(double worldX, double worldZ) { return worldX / 10D; }
            public double sample(double worldX, double y, double worldZ) { return y / 10D; }
        };

        assertEquals(-1D, noise.signed(0D, 5D), EXACT);
        assertEquals(1D, noise.signed(10D, 5D), EXACT);
        assertEquals(0.5D * 2D - 1D, noise.signed(5D, 0D), EXACT);
        assertEquals(-1D, noise.signed(3D, 0D, 3D), EXACT);
        assertEquals(0.3D * 2D - 1D, noise.signed(3D, 3D, 3D), EXACT);
    }

    @Test
    void aHostMayAnswerSignedItself() {
        NoiseSampler noise = new NoiseSampler() {
            public double sample(double worldX, double worldZ) { return 0.25D; }
            public double sample(double worldX, double y, double worldZ) { return 0.25D; }
            @Override public double signed(double worldX, double worldZ) { return 7D; }
        };

        assertEquals(7D, noise.signed(1D, 1D), EXACT);
        assertEquals(0.25D * 2D - 1D, noise.signed(1D, 1D, 1D), EXACT);
    }
}
