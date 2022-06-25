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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.noise.CNGFactory;
import com.volmit.iris.util.noise.NoiseType;
import com.volmit.iris.util.stream.ProceduralStream;

@Desc("Styles of noise")
public enum NoiseStyle {
    @Desc("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1)),

    @Desc("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_BILINEAR(rng -> new CNG(rng, NoiseType.WHITE_BILINEAR, 1D, 1)),

    @Desc("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_BICUBIC(rng -> new CNG(rng, NoiseType.WHITE_BICUBIC, 1D, 1)),

    @Desc("White Noise is like static. Useful for block scattering but not terrain.")
    STATIC_HERMITE(rng -> new CNG(rng, NoiseType.WHITE_HERMITE, 1D, 1)),

    @Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS(rng -> CNG.signature(rng).scale(1)),

    @Desc("Clover Noise")
    CLOVER(rng -> new CNG(rng, NoiseType.CLOVER, 1D, 1).scale(0.06).bake()),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_3, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_6, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_9, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_STARCAST_12, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BILINEAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_3, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BILINEAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_6, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BILINEAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_9, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BILINEAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR_STARCAST_12, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_HERMITE_STARCAST_3(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_3, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_HERMITE_STARCAST_6(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_6, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_HERMITE_STARCAST_9(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_9, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_HERMITE_STARCAST_12(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE_STARCAST_12, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BILINEAR(rng -> new CNG(rng, NoiseType.CLOVER_BILINEAR, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_BICUBIC(rng -> new CNG(rng, NoiseType.CLOVER_BICUBIC, 1D, 1)),

    @Desc("CLOVER noise creates the same noise level for cells, changes noise level on cell borders.")
    CLOVER_HERMITE(rng -> new CNG(rng, NoiseType.CLOVER_HERMITE, 1D, 1)),

    @Desc("Vascular noise gets higher as the position nears a cell border.")
    VASCULAR(rng -> new CNG(rng, NoiseType.VASCULAR, 1D, 1)),

    @Desc("It always returns 0.5")
    FLAT(rng -> new CNG(rng, NoiseType.FLAT, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR(rng -> new CNG(rng, NoiseType.CELLULAR, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_3, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_6, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_9, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_STARCAST_12, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_3, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_6, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_9, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR_STARCAST_12, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_3(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_3, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_6(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_6, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_9(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_9, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE_STARCAST_12(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE_STARCAST_12, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BILINEAR(rng -> new CNG(rng, NoiseType.CELLULAR_BILINEAR, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_BICUBIC(rng -> new CNG(rng, NoiseType.CELLULAR_BICUBIC, 1D, 1)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
    CELLULAR_HERMITE(rng -> new CNG(rng, NoiseType.CELLULAR_HERMITE, 1D, 1)),

    @Desc("Classic German Engineering")
    NOWHERE(rng -> CNG.signaturePerlin(rng).scale(0.776).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_CELLULAR(rng -> CNG.signaturePerlin(rng, NoiseType.CELLULAR).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_CLOVER(rng -> CNG.signaturePerlin(rng, NoiseType.CLOVER).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_SIMPLEX(rng -> CNG.signaturePerlin(rng, NoiseType.SIMPLEX).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_GLOB(rng -> CNG.signaturePerlin(rng, NoiseType.GLOB).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_VASCULAR(rng -> CNG.signaturePerlin(rng, NoiseType.VASCULAR).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_CUBIC(rng -> CNG.signaturePerlin(rng, NoiseType.CUBIC).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_SUPERFRACTAL(rng -> CNG.signaturePerlin(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX).scale(1).bake()),

    @Desc("Classic German Engineering")
    NOWHERE_FRACTAL(rng -> CNG.signaturePerlin(rng, NoiseType.FRACTAL_BILLOW_PERLIN).scale(1).bake()),

    @Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_DOUBLE(rng -> CNG.signatureDouble(rng).scale(1)),

    @Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_THICK(rng -> CNG.signatureThick(rng).scale(1)),

    @Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
    IRIS_HALF(rng -> CNG.signatureHalf(rng).scale(1)),

    @Desc("Basic, Smooth & Fast Simplex noise.")
    SIMPLEX(rng -> new CNG(rng, 1D, 1).scale(1)),

    @Desc("Very Detailed smoke using simplex fractured with fractal billow simplex at high octaves.")
    FRACTAL_SMOKE(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8).scale(0.2), 1000).scale(0.34)),

    @Desc("Thinner Veins.")
    VASCULAR_THIN(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.VASCULAR, 1D, 1).scale(1).pow(0.65)),

    @Desc("Cells of simplex noise")
    SIMPLEX_CELLS(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.CELLULAR, 1D, 1).scale(1), 200)),

    @Desc("Veins of simplex noise")
    SIMPLEX_VASCULAR(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.VASCULAR, 1D, 1).scale(1), 200)),

    @Desc("Very Detailed fluid using simplex fractured with fractal billow simplex at high octaves.")
    FRACTAL_WATER(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9).scale(0.03), 9900).scale(1.14)),

    @Desc("Perlin. Like simplex but more natural")
    PERLIN(rng -> new CNG(rng, NoiseType.PERLIN, 1D, 1).scale(1.15)),

    @Desc("Perlin. Like simplex but more natural")
    PERLIN_IRIS(rng -> CNG.signature(rng, NoiseType.PERLIN).scale(1.47)),

    @Desc("Perlin. Like simplex but more natural")
    PERLIN_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.PERLIN).scale(1.47)),

    @Desc("Perlin. Like simplex but more natural")
    PERLIN_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.PERLIN).scale(1.47)),

    @Desc("Perlin. Like simplex but more natural")
    PERLIN_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.PERLIN).scale(1.47)),

    @Desc("Billow Fractal Perlin Noise.")
    FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 1).scale(1.47)),

    @Desc("Billow Fractal Perlin Noise. 2 Octaves")
    BIOCTAVE_FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 2).scale(1.17)),

    @Desc("Billow Fractal Simplex Noise. Single octave.")
    FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 1)),

    @Desc("FBM Fractal Simplex Noise. Single octave.")
    FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 1)),

    @Desc("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

    @Desc("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

    @Desc("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

    @Desc("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

    @Desc("Billow Fractal Iris Noise. Single octave.")
    FRACTAL_BILLOW_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

    @Desc("FBM Fractal Iris Noise. Single octave.")
    FRACTAL_FBM_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

    @Desc("Rigid Multi Fractal Simplex Noise. Single octave.")
    FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 1)),

    @Desc("Billow Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 2)),

    @Desc("FBM Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 2)),

    @Desc("Rigid Multi Fractal Simplex Noise. 2 octaves.")
    BIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 2)),

    @Desc("Rigid Multi Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 3)),

    @Desc("Billow Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 3)),

    @Desc("FBM Fractal Simplex Noise. 3 octaves.")
    TRIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 3)),

    @Desc("Rigid Multi Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 4)),

    @Desc("Billow Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 4)),

    @Desc("FBM Fractal Simplex Noise. 4 octaves.")
    QUADOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 4)),

    @Desc("Rigid Multi Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 5)),

    @Desc("Billow Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 5)),

    @Desc("FBM Fractal Simplex Noise. 5 octaves.")
    QUINTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 5)),

    @Desc("Rigid Multi Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 6)),

    @Desc("Billow Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 6)),

    @Desc("FBM Fractal Simplex Noise. 6 octaves.")
    SEXOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 6)),

    @Desc("Rigid Multi Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 7)),

    @Desc("Billow Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 7)),

    @Desc("FBM Fractal Simplex Noise. 7 octaves.")
    SEPTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 7)),

    @Desc("Rigid Multi Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 8)),

    @Desc("Billow Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8)),

    @Desc("FBM Fractal Simplex Noise. 8 octaves.")
    OCTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 8)),

    @Desc("Rigid Multi Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 9)),

    @Desc("Billow Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 9)),

    @Desc("FBM Fractal Simplex Noise. 9 octaves.")
    NONOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9)),

    @Desc("Rigid Multi Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 10)),

    @Desc("Billow Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 10)),

    @Desc("FBM Fractal Simplex Noise. 10 octaves.")
    VIGOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 10)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 2 octaves")
    BIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 2).scale(1D / 2D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 3 octaves")
    TRIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 3).scale(1D / 3D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 4 octaves")
    QUADOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 4).scale(1D / 4D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 5 octaves")
    QUINTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 5).scale(1D / 5D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 6 octaves")
    SEXOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 6).scale(1D / 6D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 7 octaves")
    SEPTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 7).scale(1D / 12D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 8 octaves")
    OCTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 8).scale(1D / 25D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 9 octaves")
    NONOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 9).scale(1D / 50D)),

    @Desc("Basic, Smooth & Fast Simplex noise. Uses 10 octaves")
    VIGOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 10).scale(1D / 100D)),

    @Desc("Glob noise is like cellular, but with globs...")
    GLOB(rng -> new CNG(rng, NoiseType.GLOB, 1D, 1)),

    @Desc("Glob noise is like cellular, but with globs...")
    GLOB_IRIS(rng -> CNG.signature(rng, NoiseType.GLOB)),

    @Desc("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.GLOB)),

    @Desc("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.GLOB)),

    @Desc("Glob noise is like cellular, but with globs...")
    GLOB_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.GLOB)),

    @Desc("Cubic Noise")
    CUBIC(rng -> new CNG(rng, NoiseType.CUBIC, 1D, 1).scale(256)),

    @Desc("Fractal Cubic Noise")
    FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 1).scale(2)),

    @Desc("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

    @Desc("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

    @Desc("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

    @Desc("Fractal Cubic Noise With Iris Swirls")
    FRACTAL_CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

    @Desc("Fractal Cubic Noise, 2 Octaves")
    BIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 2).scale(2)),

    @Desc("Fractal Cubic Noise, 3 Octaves")
    TRIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 3).scale(1.5)),

    @Desc("Fractal Cubic Noise, 4 Octaves")
    QUADOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 4).scale(1)),

    @Desc("Cubic Noise")
    CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.CUBIC).scale(256)),

    @Desc("Cubic Noise")
    CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CUBIC).scale(256)),

    @Desc("Cubic Noise")
    CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CUBIC).scale(256)),

    @Desc("Cubic Noise")
    CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CUBIC).scale(256)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR)),

    @Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
    CELLULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR)),

    @Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell")
    CELLULAR_HEIGHT(rng -> new CNG(rng, NoiseType.CELLULAR_HEIGHT, 1D, 1)),

    @Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR_HEIGHT)),

    @Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR_HEIGHT)),

    @Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR_HEIGHT)),

    @Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
    CELLULAR_HEIGHT_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR_HEIGHT)),

    @Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS(rng -> CNG.signature(rng, NoiseType.VASCULAR)),

    @Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.VASCULAR)),

    @Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.VASCULAR)),

    @Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
    VASCULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.VASCULAR)),
    ;

    private final CNGFactory f;

    NoiseStyle(CNGFactory f) {
        this.f = f;
    }

    public ProceduralStream<Double> stream(RNG seed) {
        return create(seed).stream();
    }

    public ProceduralStream<Double> stream(long seed) {
        return create(new RNG(seed)).stream();
    }

    public CNG create(RNG seed) {
        CNG cng = f.create(seed).bake();
        cng.setLeakStyle(this);
        return cng;
    }

    public IrisGeneratorStyle style() {
        return new IrisGeneratorStyle(this);
    }
}
