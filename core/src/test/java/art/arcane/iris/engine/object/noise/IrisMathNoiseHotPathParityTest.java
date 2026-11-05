package art.arcane.iris.engine.object.noise;

import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.util.project.interpolation.InterpolationMethod3D;
import art.arcane.iris.util.project.interpolation.Interpolation3D;
import art.arcane.iris.util.project.noise.HexJamesNoise;
import art.arcane.iris.util.project.noise.HexRandomSizeNoise;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.NoiseProvider3;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisMathNoiseHotPathParityTest {
    @Test
    public void hexNoiseSamplesRemainBitExact() {
        HexRandomSizeNoise randomSize = new HexRandomSizeNoise(12345L);
        HexJamesNoise james = new HexJamesNoise(54321L);

        assertEquals(0.4670864519829246D, randomSize.noise(12.5D, -8.25D), 0D);
        assertEquals(0.4935590260372404D, randomSize.noise(-31.75D, 44.5D, 9.125D), 0D);
        assertEquals(0.5185375921658786D, james.noise(12.5D, -8.25D), 0D);
        assertEquals(0.4557738681128938D, james.noise(-31.75D, 44.5D, 9.125D), 0D);
    }

    @Test
    public void generatorNoiseSamplesRemainBitExact() {
        IrisNoiseGenerator noiseGenerator = new IrisNoiseGenerator()
                .setZoom(7.25D)
                .setOffsetX(1.75D)
                .setOffsetZ(-3.5D)
                .setOpacity(0.82D)
                .setStyle(NoiseStyle.PERLIN_IRIS.style())
                .setExponent(1.15D);

        assertEquals(0.5652709414703923D, noiseGenerator.getNoise(9988L, 31.25D, -17.5D, null), 0D);

        IrisGeneratorStyle style = NoiseStyle.IRIS_DOUBLE.style();
        assertEquals(0.9127657585776476D, style.createNoCache(new RNG(112233L), null).noiseFast2D(42.5D, -19.75D), 0D);

        IrisGenerator generator = new IrisGenerator()
                .setZoom(9.5D)
                .setOpacity(0.91D)
                .setComposite(new KList<IrisNoiseGenerator>().qadd(noiseGenerator));

        assertEquals(0.44868786951463424D, generator.getHeight(63.0D, -27.0D, 445566L), 0D);
    }

    @Test
    public void interpolationSamplesRemainBitExact() {
        NoiseProvider3 provider = (x, y, z) -> {
            double angle = (x * 0.017D) + (y * 0.011D) + (z * 0.023D);
            return 0.5D + (Math.sin(angle) * 0.25D);
        };

        assertEquals(
                0.5231950552025616D,
                Interpolation3D.getNoise3D(InterpolationMethod3D.TRILINEAR, 5, 7, -3, 2.5D, 3.5D, 4.5D, provider),
                0D
        );
        assertEquals(
                0.5259208842929466D,
                Interpolation3D.getNoise3D(InterpolationMethod3D.TRICUBIC, 5, 7, -3, 2.5D, 3.5D, 4.5D, provider),
                0D
        );
    }
}
