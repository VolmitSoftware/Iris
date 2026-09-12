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

package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.CNGFactory;
import art.arcane.volmlib.util.noise.NoiseType;
import art.arcane.volmlib.util.stream.ProceduralStream;
@Description("Styles of noise")
public enum NoiseStyle {
    @Description("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1), NoiseType.WHITE),

    @Description("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_BILINEAR(rng -> new CNG(rng, NoiseType.WHITE_BILINEAR, 1D, 1), NoiseType.WHITE_BILINEAR),

    @Description("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_BICUBIC(rng -> new CNG(rng, NoiseType.WHITE_BICUBIC, 1D, 1), NoiseType.WHITE_BICUBIC),

    @Description("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_HERMITE(rng -> new CNG(rng, NoiseType.WHITE_HERMITE, 1D, 1), NoiseType.WHITE_HERMITE),

    @Description("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS(rng -> CNG.signature(rng)),

    @Description("Clover Noise")
    CLOVER(rng -> new CNG(rng, NoiseType.CLOVER, 1D, 1).bake(), NoiseType.CLOVER),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_3, 1D, 1), NoiseType.CLOVER_STARCAST_3),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_6, 1D, 1), NoiseType.CLOVER_STARCAST_6),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_9, 1D, 1), NoiseType.CLOVER_STARCAST_9),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_12, 1D, 1), NoiseType.CLOVER_STARCAST_12),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BILINEAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_3, 1D, 1), NoiseType.CLOVER_BILINEAR_STARCAST_3),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BILINEAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_6, 1D, 1), NoiseType.CLOVER_BILINEAR_STARCAST_6),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BILINEAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_9, 1D, 1), NoiseType.CLOVER_BILINEAR_STARCAST_9),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BILINEAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_12, 1D, 1), NoiseType.CLOVER_BILINEAR_STARCAST_12),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_HERMITE_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_3, 1D, 1), NoiseType.CLOVER_HERMITE_STARCAST_3),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_HERMITE_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_6, 1D, 1), NoiseType.CLOVER_HERMITE_STARCAST_6),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_HERMITE_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_9, 1D, 1), NoiseType.CLOVER_HERMITE_STARCAST_9),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_HERMITE_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_12, 1D, 1), NoiseType.CLOVER_HERMITE_STARCAST_12),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BILINEAR(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR, 1D, 1), NoiseType.CLOVER_BILINEAR),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_BICUBIC(rng -> new CNG(rng, NoiseType.CLOVER_BICUBIC, 1D, 1), NoiseType.CLOVER_BICUBIC),

    @Description("Clover noise smoothed with the selected interpolation kernel.")
    CLOVER_HERMITE(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE, 1D, 1), NoiseType.CLOVER_HERMITE),

    @Description("Vascular noise gets higher as the position nears a cell border.")
    VASCULAR(rng -> new CNG(rng, NoiseType.VASCULAR, 1D, 1), NoiseType.VASCULAR),

    @Description("It always returns 1.0")
    FLAT(rng -> new CNG(rng, NoiseType.FLAT, 1D, 1), NoiseType.FLAT),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR(rng -> new CNG(rng, NoiseType.CELLULAR, 1D, 1), NoiseType.CELLULAR),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_3, 1D, 1), NoiseType.CELLULAR_STARCAST_3),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_6, 1D, 1), NoiseType.CELLULAR_STARCAST_6),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_9, 1D, 1), NoiseType.CELLULAR_STARCAST_9),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_12, 1D, 1), NoiseType.CELLULAR_STARCAST_12),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_3, 1D, 1), NoiseType.CELLULAR_BILINEAR_STARCAST_3),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_6, 1D, 1), NoiseType.CELLULAR_BILINEAR_STARCAST_6),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_9, 1D, 1), NoiseType.CELLULAR_BILINEAR_STARCAST_9),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_12, 1D, 1), NoiseType.CELLULAR_BILINEAR_STARCAST_12),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_3, 1D, 1), NoiseType.CELLULAR_HERMITE_STARCAST_3),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_6, 1D, 1), NoiseType.CELLULAR_HERMITE_STARCAST_6),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_9, 1D, 1), NoiseType.CELLULAR_HERMITE_STARCAST_9),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_12, 1D, 1), NoiseType.CELLULAR_HERMITE_STARCAST_12),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR, 1D, 1), NoiseType.CELLULAR_BILINEAR),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BICUBIC(rng -> new CNG(rng, NoiseType.CELLULAR_BICUBIC, 1D, 1), NoiseType.CELLULAR_BICUBIC),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE, 1D, 1), NoiseType.CELLULAR_HERMITE),

    @Description("Solid regular hexagons colored by a coherent simplex field.")
    HEXAGON(rng -> new CNG(rng, NoiseType.HEXAGON, 1D, 1), NoiseType.HEXAGON),

    @Description("Recursive contained hexagons with alternating subdivision probabilities and simplex colors.")
    HEX_JAMES(rng -> new CNG(rng, NoiseType.HEX_JAMES, 1D, 1), NoiseType.HEX_JAMES),

    @Description("Interlocked solid hex cells with per-cell values from a smooth simplex heatmap.")
    HEX_SIMPLEX(rng -> new CNG(rng, NoiseType.HEX_SIMPLEX, 1D, 1), NoiseType.HEX_SIMPLEX),

    @Description("Finite-depth hexagonal subdivision selected by a seeded field, with coherent simplex colors.")
    HEX_RANDOM_SIZE(rng -> new CNG(rng, NoiseType.HEX_RANDOM_SIZE, 1D, 1), NoiseType.HEX_RANDOM_SIZE),

    @Description("Finite-depth equilateral Sierpinski triangles colored by simplex heat.")
    SIERPINSKI_TRIANGLE(rng -> new CNG(rng, NoiseType.SIERPINSKI_TRIANGLE, 1D, 1), NoiseType.SIERPINSKI_TRIANGLE),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE(rng -> CNG.signaturePerlin(rng).bake()),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_CELLULAR(rng -> CNG.signaturePerlin(rng, NoiseType.CELLULAR).bake(), NoiseType.CELLULAR),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_CLOVER(rng -> CNG.signaturePerlin(rng, NoiseType.CLOVER).bake(), NoiseType.CLOVER),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_HEXAGON(rng -> CNG.signaturePerlin(rng, NoiseType.HEXAGON).bake(), NoiseType.HEXAGON),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_HEX_JAMES(rng -> CNG.signaturePerlin(rng, NoiseType.HEX_JAMES).bake(), NoiseType.HEX_JAMES),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_HEX_SIMPLEX(rng -> CNG.signaturePerlin(rng, NoiseType.HEX_SIMPLEX).bake(), NoiseType.HEX_SIMPLEX),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_HEX_RANDOM_SIZE(rng -> CNG.signaturePerlin(rng, NoiseType.HEX_RANDOM_SIZE).bake(), NoiseType.HEX_RANDOM_SIZE),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_SIERPINSKI_TRIANGLE(rng -> CNG.signaturePerlin(rng, NoiseType.SIERPINSKI_TRIANGLE).bake(), NoiseType.SIERPINSKI_TRIANGLE),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_SIMPLEX(rng -> CNG.signaturePerlin(rng, NoiseType.SIMPLEX).bake(), NoiseType.SIMPLEX),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_GLOB(rng -> CNG.signaturePerlin(rng, NoiseType.GLOB).bake(), NoiseType.GLOB),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_VASCULAR(rng -> CNG.signaturePerlin(rng, NoiseType.VASCULAR).bake(), NoiseType.VASCULAR),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_CUBIC(rng -> CNG.signaturePerlin(rng, NoiseType.CUBIC).bake(), NoiseType.CUBIC),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_SUPERFRACTAL(rng -> CNG.signaturePerlin(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX).bake(), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Perlin-warped noise with broad, flowing distortion.")
    NOWHERE_FRACTAL(rng -> CNG.signaturePerlin(rng, NoiseType.FRACTAL_BILLOW_PERLIN).bake(), NoiseType.FRACTAL_BILLOW_PERLIN),

    @Description("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_DOUBLE(rng -> CNG.signatureDouble(rng)),

    @Description("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_THICK(rng -> CNG.signatureThick(rng)),

    @Description("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_HALF(rng -> CNG.signatureHalf(rng)),

    @Description("Basic, Smooth & Fast Simplex noise.")
    SIMPLEX(rng -> new CNG(rng, 1D, 1)),

    @Description("Very Detailed smoke using simplex fractured with fractal billow simplex at high octaves.")
    FRACTAL_SMOKE(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8).scale(0.2), 1000), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("Thinner Veins.")
    VASCULAR_THIN(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.VASCULAR, 1D, 1).scale(1).pow(1D / 0.65D), NoiseType.VASCULAR),

    @Description("Cells of simplex noise")
    SIMPLEX_CELLS(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.CELLULAR, 1D, 1).scale(1), 200), NoiseType.SIMPLEX),

    @Description("Veins of simplex noise")
    SIMPLEX_VASCULAR(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.VASCULAR, 1D, 1).scale(1), 200), NoiseType.SIMPLEX),

    @Description("Very Detailed fluid using simplex fractured with fractal billow simplex at high octaves.")
    FRACTAL_WATER(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9).scale(0.03), 9900), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Perlin. Like simplex but more natural")
    PERLIN(rng -> new CNG(rng, NoiseType.PERLIN, 1D, 1), NoiseType.PERLIN),

    @Description("Perlin. Like simplex but more natural")
    PERLIN_IRIS(rng -> CNG.signature(rng, NoiseType.PERLIN), NoiseType.PERLIN),

    @Description("Perlin. Like simplex but more natural")
    PERLIN_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.PERLIN), NoiseType.PERLIN),

    @Description("Perlin. Like simplex but more natural")
    PERLIN_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.PERLIN), NoiseType.PERLIN),

    @Description("Perlin. Like simplex but more natural")
    PERLIN_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.PERLIN), NoiseType.PERLIN),

    @Description("Billow Fractal Perlin Noise.")
    FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 1), NoiseType.FRACTAL_BILLOW_PERLIN),

    @Description("Billow Fractal Perlin Noise. 2 Octaves")
    BIOCTAVE_FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 2), NoiseType.FRACTAL_BILLOW_PERLIN),

    @Description("Billow Fractal Simplex Noise. Single octave.")
    FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 1), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. Single octave.")
    FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 1), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_FBM_SIMPLEX), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_FBM_SIMPLEX), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_FBM_SIMPLEX), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Fractal hexagonal cell noise.")
    FRACTAL_HEXAGON(rng -> new CNG(rng, NoiseType.HEXAGON, 1D, 4), NoiseType.HEXAGON),

    @Description("Recursive contained hexagons colored by fractal simplex noise.")
    FRACTAL_HEX_JAMES(rng -> new CNG(rng, NoiseType.HEX_JAMES, 1D, 4), NoiseType.HEX_JAMES),

    @Description("Interlocked solid hex cells with per-cell fractal simplex heatmap values.")
    FRACTAL_HEX_SIMPLEX(rng -> new CNG(rng, NoiseType.HEX_SIMPLEX, 1D, 4), NoiseType.HEX_SIMPLEX),

    @Description("Recursive hexagonal subdivision colored by fractal simplex noise.")
    FRACTAL_HEX_RANDOM_SIZE(rng -> new CNG(rng, NoiseType.HEX_RANDOM_SIZE, 1D, 4), NoiseType.HEX_RANDOM_SIZE),

    @Description("Finite-depth equilateral Sierpinski triangles colored by fractal simplex heat.")
    FRACTAL_SIERPINSKI_TRIANGLE(rng -> new CNG(rng, NoiseType.SIERPINSKI_TRIANGLE, 1D, 4), NoiseType.SIERPINSKI_TRIANGLE),

    @Description("Rigid Multi Fractal Simplex Noise. Single octave.")
    FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 1), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 2), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 2), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 2), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 3), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 3), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 3), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 4), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 4), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 4), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 5), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 5), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 5), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 6), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 6), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 6), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 7), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 7), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 7), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 8), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 8), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 9), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 9), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Rigid Multi Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 10), NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX),

    @Description("Billow Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 10), NoiseType.FRACTAL_BILLOW_SIMPLEX),

    @Description("FBM Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 10), NoiseType.FRACTAL_FBM_SIMPLEX),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 2 octaves")
    BIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 2)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 3 octaves")
    TRIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 3)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 4 octaves")
    QUADOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 4)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 5 octaves")
    QUINTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 5)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 6 octaves")
    SEXOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 6)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 7 octaves")
    SEPTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 7)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 8 octaves")
    OCTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 8)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 9 octaves")
    NONOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 9)),

    @Description("Basic, Smooth & Fast Simplex noise. Uses 10 octaves")
    VIGOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 10)),

    @Description("Glob noise is like cellular, but with globs...")
    GLOB(rng -> new CNG(rng, NoiseType.GLOB, 1D, 1), NoiseType.GLOB),

    @Description("Glob noise is like cellular, but with globs...")
    GLOB_IRIS(rng -> CNG.signature(rng, NoiseType.GLOB), NoiseType.GLOB),

    @Description("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.GLOB), NoiseType.GLOB),

    @Description("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.GLOB), NoiseType.GLOB),

    @Description("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.GLOB), NoiseType.GLOB),

    @Description("Cubic Noise")
    CUBIC(rng -> new CNG(rng, NoiseType.CUBIC, 1D, 1), NoiseType.CUBIC),

    @Description("Fractal Cubic Noise")
    FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 1), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_CUBIC), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_CUBIC), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_CUBIC), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.FRACTAL_CUBIC), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise, 2 Octaves")
    BIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 2), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise, 3 Octaves")
    TRIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 3), NoiseType.FRACTAL_CUBIC),

    @Description("Fractal Cubic Noise, 4 Octaves")
    QUADOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 4), NoiseType.FRACTAL_CUBIC),

    @Description("Cubic Noise")
    CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.CUBIC), NoiseType.CUBIC),

    @Description("Cubic Noise")
    CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CUBIC), NoiseType.CUBIC),

    @Description("Cubic Noise")
    CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CUBIC), NoiseType.CUBIC),

    @Description("Cubic Noise")
    CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CUBIC), NoiseType.CUBIC),

    @Description("Hexagonal cell noise distorted using Iris styled wispy noise.")
    HEXAGON_IRIS(rng -> CNG.signature(rng, NoiseType.HEXAGON), NoiseType.HEXAGON),

    @Description("Hexagonal cell noise distorted using Iris styled wispy noise.")
    HEXAGON_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.HEXAGON), NoiseType.HEXAGON),

    @Description("Hexagonal cell noise distorted using Iris styled wispy noise.")
    HEXAGON_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.HEXAGON), NoiseType.HEXAGON),

    @Description("Hexagonal cell noise distorted using Iris styled wispy noise.")
    HEXAGON_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.HEXAGON), NoiseType.HEXAGON),

    @Description("Hex James substitution pattern distorted using Iris styled wispy noise.")
    HEX_JAMES_IRIS(rng -> CNG.signature(rng, NoiseType.HEX_JAMES), NoiseType.HEX_JAMES),

    @Description("Hex James substitution pattern distorted using Iris styled wispy noise.")
    HEX_JAMES_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.HEX_JAMES), NoiseType.HEX_JAMES),

    @Description("Hex James substitution pattern distorted using Iris styled wispy noise.")
    HEX_JAMES_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.HEX_JAMES), NoiseType.HEX_JAMES),

    @Description("Hex James substitution pattern distorted using Iris styled wispy noise.")
    HEX_JAMES_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.HEX_JAMES), NoiseType.HEX_JAMES),

    @Description("Interlocked solid hex-cell simplex heatmap distorted using Iris styled wispy noise.")
    HEX_SIMPLEX_IRIS(rng -> CNG.signature(rng, NoiseType.HEX_SIMPLEX), NoiseType.HEX_SIMPLEX),

    @Description("Interlocked solid hex-cell simplex heatmap distorted using Iris styled wispy noise.")
    HEX_SIMPLEX_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.HEX_SIMPLEX), NoiseType.HEX_SIMPLEX),

    @Description("Interlocked solid hex-cell simplex heatmap distorted using Iris styled wispy noise.")
    HEX_SIMPLEX_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.HEX_SIMPLEX), NoiseType.HEX_SIMPLEX),

    @Description("Interlocked solid hex-cell simplex heatmap distorted using Iris styled wispy noise.")
    HEX_SIMPLEX_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.HEX_SIMPLEX), NoiseType.HEX_SIMPLEX),

    @Description("Hexagonal random-size gradient noise and distorted using Iris styled wispy noise.")
    HEX_RANDOM_SIZE_IRIS(rng -> CNG.signature(rng, NoiseType.HEX_RANDOM_SIZE), NoiseType.HEX_RANDOM_SIZE),

    @Description("Hexagonal random-size gradient noise and distorted using Iris styled wispy noise.")
    HEX_RANDOM_SIZE_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.HEX_RANDOM_SIZE), NoiseType.HEX_RANDOM_SIZE),

    @Description("Hexagonal random-size gradient noise and distorted using Iris styled wispy noise.")
    HEX_RANDOM_SIZE_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.HEX_RANDOM_SIZE), NoiseType.HEX_RANDOM_SIZE),

    @Description("Hexagonal random-size gradient noise and distorted using Iris styled wispy noise.")
    HEX_RANDOM_SIZE_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.HEX_RANDOM_SIZE), NoiseType.HEX_RANDOM_SIZE),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR), NoiseType.CELLULAR),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR), NoiseType.CELLULAR),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR), NoiseType.CELLULAR),

    @Description("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR), NoiseType.CELLULAR),

    @Description("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell")
    CELLULAR_HEIGHT(rng -> new CNG(rng, NoiseType.CELLULAR_HEIGHT, 1D, 1), NoiseType.CELLULAR_HEIGHT),

    @Description("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR_HEIGHT), NoiseType.CELLULAR_HEIGHT),

    @Description("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR_HEIGHT), NoiseType.CELLULAR_HEIGHT),

    @Description("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR_HEIGHT), NoiseType.CELLULAR_HEIGHT),

    @Description("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR_HEIGHT), NoiseType.CELLULAR_HEIGHT),

    @Description("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS(rng -> CNG.signature(rng, NoiseType.VASCULAR), NoiseType.VASCULAR),

    @Description("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.VASCULAR), NoiseType.VASCULAR),

    @Description("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.VASCULAR), NoiseType.VASCULAR),

    @Description("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.VASCULAR), NoiseType.VASCULAR),

    @Description("Warped gyroid sheets form rounded mazes in 2D and interconnected labyrinths in 3D.")
    GYROID(rng -> new CNG(rng, NoiseType.GYROID, 1D, 1), NoiseType.GYROID),

    @Description("Fivefold wave interference forms non-repeating stars, rosettes, and crystalline contours.")
    QUASICRYSTAL(rng -> new CNG(rng, NoiseType.QUASICRYSTAL, 1D, 1), NoiseType.QUASICRYSTAL),

    @Description("Connected quarter-circle ribbons form tiled loops that twist continuously through height.")
    TRUCHET(rng -> new CNG(rng, NoiseType.TRUCHET, 1D, 1), NoiseType.TRUCHET),

    @Description("Scattered bowls with raised rims form crater fields in 2D and hollow shells in 3D.")
    CRATER(rng -> new CNG(rng, NoiseType.CRATER, 1D, 1), NoiseType.CRATER),

    @Description("Seeded spiral arms form swirling eddies in 2D and winding funnels in 3D.")
    VORTEX(rng -> new CNG(rng, NoiseType.VORTEX, 1D, 1), NoiseType.VORTEX),

    @Description("Seeded crescent dunes with asymmetric slopes and smooth height-dependent drift.")
    DUNE(rng -> new CNG(rng, NoiseType.DUNE, 1D, 1), NoiseType.DUNE),

    @Description("Folded sedimentary bands with varying thickness through the volume.")
    STRATA(rng -> new CNG(rng, NoiseType.STRATA, 1D, 1), NoiseType.STRATA),

    @Description("Distorted growth rings and knot-like forms with variation through height.")
    WOOD(rng -> new CNG(rng, NoiseType.WOOD, 1D, 1), NoiseType.WOOD),

    @Description("Seeded oriented wave packets form a sparse directional ripple texture.")
    GABOR(rng -> new CNG(rng, NoiseType.GABOR, 1D, 1), NoiseType.GABOR),

    @Description("Turbulent stone veins separate broad smooth regions.")
    MARBLE(rng -> new CNG(rng, NoiseType.MARBLE, 1D, 1), NoiseType.MARBLE),

    @Description("Overlapping scalloped scales form a seeded tiled surface.")
    SCALES(rng -> new CNG(rng, NoiseType.SCALES, 1D, 1), NoiseType.SCALES),

    @Description("Standing-wave nodes form plate-like figures that change through height.")
    CHLADNI(rng -> new CNG(rng, NoiseType.CHLADNI, 1D, 1), NoiseType.CHLADNI),

    @Description("Mirrored wedge motifs repeat at a local scale with seeded variation.")
    KALEIDOSCOPE(rng -> new CNG(rng, NoiseType.KALEIDOSCOPE, 1D, 1), NoiseType.KALEIDOSCOPE),

    @Description("Recursive cubic voids form a three-dimensional sponge with square-hole slices.")
    MENGER_SPONGE(rng -> new CNG(rng, NoiseType.MENGER_SPONGE, 1D, 1), NoiseType.MENGER_SPONGE),

    @Description("Orthogonal tracks and ring pads form connected circuits that shift through height.")
    CIRCUIT(rng -> new CNG(rng, NoiseType.CIRCUIT, 1D, 1), NoiseType.CIRCUIT),
    ;

    private final CNGFactory f;
    private final NoiseType type;

    NoiseStyle(CNGFactory f) {
        this(f, NoiseType.SIMPLEX);
    }

    NoiseStyle(CNGFactory f, NoiseType type) {
        this.f = f;
        this.type = type;
    }

    public ProceduralStream<Double> stream(RNG seed) {
        return create(seed).stream();
    }

    public ProceduralStream<Double> stream(long seed) {
        return create(new RNG(seed)).stream();
    }

    public CNG create(RNG seed) {
        CNG cng = f.create(seed).bake().scale(type.getCoordinateScale()).bake();
        return cng;
    }

    public IrisGeneratorStyle style() {
        return new IrisGeneratorStyle(this);
    }
}
