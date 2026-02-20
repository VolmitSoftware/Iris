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

package art.arcane.iris.util.project.noise;

import art.arcane.iris.util.project.interpolation.InterpolationMethod;
public enum NoiseType {
    WHITE(WhiteNoise::new),
    WHITE_BILINEAR((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BILINEAR)),
    WHITE_BICUBIC((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.BICUBIC)),
    WHITE_HERMITE((s) -> new InterpolatedNoise(s, WHITE, InterpolationMethod.HERMITE)),
    SIMPLEX(SimplexNoise::new),
    PERLIN(seed -> new PerlinNoise(seed).hermite()),
    FRACTAL_BILLOW_SIMPLEX(FractalBillowSimplexNoise::new),
    FRACTAL_BILLOW_PERLIN(FractalBillowPerlinNoise::new),
    FRACTAL_FBM_SIMPLEX(FractalFBMSimplexNoise::new),
    FRACTAL_RIGID_MULTI_SIMPLEX(FractalRigidMultiSimplexNoise::new),
    FLAT(FlatNoise::new),
    CELLULAR(CellularNoise::new),
    CELLULAR_BILINEAR((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR)),
    CELLULAR_BILINEAR_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_3)),
    CELLULAR_BILINEAR_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_6)),
    CELLULAR_BILINEAR_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_9)),
    CELLULAR_BILINEAR_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BILINEAR_STARCAST_12)),
    CELLULAR_BICUBIC((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.BICUBIC)),
    CELLULAR_HERMITE((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE)),
    CELLULAR_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_3)),
    CELLULAR_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_6)),
    CELLULAR_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_9)),
    CELLULAR_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.STARCAST_12)),
    CELLULAR_HERMITE_STARCAST_3((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_3)),
    CELLULAR_HERMITE_STARCAST_6((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_6)),
    CELLULAR_HERMITE_STARCAST_9((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_9)),
    CELLULAR_HERMITE_STARCAST_12((s) -> new InterpolatedNoise(s, CELLULAR, InterpolationMethod.HERMITE_STARCAST_12)),
    GLOB(GlobNoise::new),
    CUBIC(CubicNoise::new),
    FRACTAL_CUBIC(FractalCubicNoise::new),
    CELLULAR_HEIGHT(CellHeightNoise::new),
    CLOVER(CloverNoise::new),
    CLOVER_BILINEAR((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR)),
    CLOVER_BILINEAR_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_3)),
    CLOVER_BILINEAR_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_6)),
    CLOVER_BILINEAR_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_9)),
    CLOVER_BILINEAR_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BILINEAR_STARCAST_12)),
    CLOVER_BICUBIC((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.BICUBIC)),
    CLOVER_HERMITE((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE)),
    CLOVER_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_3)),
    CLOVER_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_6)),
    CLOVER_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_9)),
    CLOVER_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.STARCAST_12)),
    CLOVER_HERMITE_STARCAST_3((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_3)),
    CLOVER_HERMITE_STARCAST_6((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_6)),
    CLOVER_HERMITE_STARCAST_9((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_9)),
    CLOVER_HERMITE_STARCAST_12((s) -> new InterpolatedNoise(s, CLOVER, InterpolationMethod.HERMITE_STARCAST_12)),
    HEXAGON(HexagonNoise::new),
    HEXAGON_BILINEAR((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BILINEAR)),
    HEXAGON_BICUBIC((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.BICUBIC)),
    HEXAGON_HERMITE((s) -> new InterpolatedNoise(s, HEXAGON, InterpolationMethod.HERMITE)),
    HEX_JAMES(HexJamesNoise::new),
    HEX_JAMES_BILINEAR((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BILINEAR)),
    HEX_JAMES_BICUBIC((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.BICUBIC)),
    HEX_JAMES_HERMITE((s) -> new InterpolatedNoise(s, HEX_JAMES, InterpolationMethod.HERMITE)),
    HEX_SIMPLEX(HexSimplexNoise::new),
    HEX_SIMPLEX_BILINEAR((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BILINEAR)),
    HEX_SIMPLEX_BICUBIC((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.BICUBIC)),
    HEX_SIMPLEX_HERMITE((s) -> new InterpolatedNoise(s, HEX_SIMPLEX, InterpolationMethod.HERMITE)),
    HEX_RANDOM_SIZE(HexRandomSizeNoise::new),
    HEX_RANDOM_SIZE_BILINEAR((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BILINEAR)),
    HEX_RANDOM_SIZE_BICUBIC((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.BICUBIC)),
    HEX_RANDOM_SIZE_HERMITE((s) -> new InterpolatedNoise(s, HEX_RANDOM_SIZE, InterpolationMethod.HERMITE)),
    SIERPINSKI_TRIANGLE(SierpinskiTriangleNoise::new),
    SIERPINSKI_TRIANGLE_BILINEAR((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BILINEAR)),
    SIERPINSKI_TRIANGLE_BICUBIC((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.BICUBIC)),
    SIERPINSKI_TRIANGLE_HERMITE((s) -> new InterpolatedNoise(s, SIERPINSKI_TRIANGLE, InterpolationMethod.HERMITE)),
    VASCULAR(VascularNoise::new);

    private final NoiseFactory f;

    NoiseType(NoiseFactory f) {
        this.f = f;
    }

    public NoiseGenerator create(long seed) {
        return f.create(seed).offset(seed);
    }
}
