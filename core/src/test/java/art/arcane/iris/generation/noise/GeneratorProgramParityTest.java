package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;

public class GeneratorProgramParityTest {
    private static final NoiseStyle[] STYLES = {
            NoiseStyle.SIMPLEX, NoiseStyle.PERLIN, NoiseStyle.IRIS, NoiseStyle.IRIS_HALF, NoiseStyle.FRACTAL_SMOKE,
            NoiseStyle.FRACTAL_WATER, NoiseStyle.CLOVER, NoiseStyle.CELLULAR, NoiseStyle.NOWHERE,
            NoiseStyle.CELLULAR_IRIS_DOUBLE, NoiseStyle.SIMPLEX_VASCULAR, NoiseStyle.FLAT, NoiseStyle.STATIC,
            NoiseStyle.GLOB_IRIS_THICK, NoiseStyle.FRACTAL_BILLOW_PERLIN, NoiseStyle.PERLIN_IRIS
    };

    @Test
    public void programHeightsMatchScalarHeightsBitForBit() {
        SplittableRandom random = new SplittableRandom(0x5EED1E55L);
        TerrainBatchScratch scratch = new TerrainBatchScratch();
        for (int generatorIndex = 0; generatorIndex < 160; generatorIndex++) {
            long shapeSeed = random.nextLong();
            IrisGenerator scalar = generator(new SplittableRandom(shapeSeed));
            IrisGenerator batch = generator(new SplittableRandom(shapeSeed));
            long superSeed = random.nextLong();
            GeneratorProgram program = batch.compileProgram(superSeed);
            int count = 1 + random.nextInt(600);
            double[] xs = new double[count];
            double[] zs = new double[count];
            double[] out = new double[count];
            fillCoordinates(random, xs, zs, count);
            program.heights(scratch, xs, zs, count, out);
            for (int index = 0; index < count; index++) {
                assertEquals("generator " + generatorIndex + " point " + xs[index] + "," + zs[index],
                        Double.doubleToRawLongBits(scalar.getHeight(xs[index], zs[index], superSeed)),
                        Double.doubleToRawLongBits(out[index]));
            }
        }
    }

    @Test
    public void singlePointProgramHeightsMatchScalarHeightsBitForBit() {
        SplittableRandom random = new SplittableRandom(0xFEEDL);
        TerrainBatchScratch scratch = new TerrainBatchScratch();
        for (int generatorIndex = 0; generatorIndex < 160; generatorIndex++) {
            long shapeSeed = random.nextLong();
            IrisGenerator scalar = generator(new SplittableRandom(shapeSeed));
            IrisGenerator compiled = generator(new SplittableRandom(shapeSeed));
            long superSeed = random.nextLong();
            GeneratorProgram program = compiled.compileProgram(superSeed);
            for (int sample = 0; sample < 400; sample++) {
                double x = coordinate(random);
                double z = random.nextInt(5) == 0 ? x : coordinate(random);
                assertEquals("generator " + generatorIndex + " point " + x + "," + z,
                        Double.doubleToRawLongBits(scalar.getHeight(x, z, superSeed)),
                        Double.doubleToRawLongBits(program.height(scratch, x, z)));
            }
        }
    }

    @Test
    public void chunkGridsMatchScalarHeightsBitForBit() {
        SplittableRandom random = new SplittableRandom(0xC0FFEEL);
        TerrainBatchScratch scratch = new TerrainBatchScratch();
        double[] xs = new double[256];
        double[] zs = new double[256];
        double[] out = new double[256];
        for (int generatorIndex = 0; generatorIndex < 60; generatorIndex++) {
            long shapeSeed = random.nextLong();
            IrisGenerator scalar = generator(new SplittableRandom(shapeSeed));
            IrisGenerator batch = generator(new SplittableRandom(shapeSeed));
            long superSeed = random.nextLong();
            GeneratorProgram program = batch.compileProgram(superSeed);
            for (int chunk = 0; chunk < 4; chunk++) {
                int originX = random.nextInt(-200_000, 200_000) << 4;
                int originZ = random.nextInt(-200_000, 200_000) << 4;
                for (int index = 0; index < 256; index++) {
                    xs[index] = originX + (index & 15);
                    zs[index] = originZ + (index >> 4);
                }
                program.heights(scratch, xs, zs, 256, out);
                for (int index = 0; index < 256; index++) {
                    assertEquals(Double.doubleToRawLongBits(scalar.getHeight(xs[index], zs[index], superSeed)),
                            Double.doubleToRawLongBits(out[index]));
                }
            }
        }
    }

    private static void fillCoordinates(SplittableRandom random, double[] xs, double[] zs, int count) {
        for (int index = 0; index < count; index++) {
            xs[index] = coordinate(random);
            zs[index] = random.nextInt(5) == 0 ? xs[index] : coordinate(random);
        }
    }

    private static double coordinate(SplittableRandom random) {
        return switch (random.nextInt(6)) {
            case 0 -> random.nextInt(-4096, 4096);
            case 1 -> random.nextInt(-30_000_000, 30_000_000);
            case 2 -> random.nextInt(-512, 512) + random.nextDouble();
            case 3 -> random.nextInt(-64, 64) * 6;
            case 4 -> random.nextDouble(-30_000_000D, 30_000_000D);
            default -> random.nextInt(-16, 16) * 0.25D;
        };
    }

    private static IrisGenerator generator(SplittableRandom random) {
        int layers = 1 + random.nextInt(3);
        KList<IrisNoiseGenerator> composite = new KList<>();
        for (int layer = 0; layer < layers; layer++) {
            composite.add(layer(random, 0));
        }
        double[] details = {0D, 0.25D, 0.5D, 1D};
        IrisGenerator generator = new IrisGenerator().setComposite(composite)
                .setSurfaceDetail(details[random.nextInt(details.length)])
                .setMultiplicitive(random.nextInt(4) == 0)
                .setSeed(random.nextLong(-5000L, 5000L))
                .setZoom(0.2D + random.nextDouble() * 3D)
                .setOffsetX(random.nextInt(-40, 40) * 0.25D)
                .setOffsetZ(random.nextInt(-40, 40) * 0.25D)
                .setOpacity(0.1D + random.nextDouble());
        if (random.nextInt(3) == 0) {
            generator.setCliffHeightMin(random.nextDouble() * 4D).setCliffHeightMax(4D + random.nextDouble() * 12D)
                    .setCliffHeightGenerator(layer(random, 2));
        }
        if (random.nextInt(5) == 0) {
            generator.setCellFractureHeight(random.nextDouble()).setCellFractureZoom(0.5D + random.nextDouble());
        }
        return generator;
    }

    private static IrisNoiseGenerator layer(SplittableRandom random, int depth) {
        IrisNoiseGenerator layer = new IrisNoiseGenerator()
                .setStyle(STYLES[random.nextInt(STYLES.length)].style())
                .setSeed(random.nextLong(-100_000L, 100_000L))
                .setZoom(0.3D + random.nextDouble() * 4D)
                .setOpacity(0.05D + random.nextDouble())
                .setOffsetX(random.nextInt(-20, 20) * 0.5D)
                .setOffsetZ(random.nextInt(-20, 20) * 0.5D)
                .setOffsetY(random.nextInt(4) == 0 ? random.nextDouble() * 0.1D : 0D)
                .setNegative(random.nextInt(4) == 0)
                .setExponent(random.nextInt(4) == 0 ? 0.5D + random.nextDouble() : 1D)
                .setParametric(random.nextInt(6) == 0)
                .setBezier(random.nextInt(6) == 0)
                .setSinCentered(random.nextInt(8) == 0)
                .setOctaves(random.nextInt(5) == 0 ? 2 : 1)
                .setEnabled(depth == 0 || random.nextInt(8) != 0);
        if (depth < 4 && random.nextInt(3) != 0) {
            int children = random.nextInt(4) == 0 ? 2 + random.nextInt(2) : 1;
            KList<IrisNoiseGenerator> fracture = new KList<>();
            for (int child = 0; child < children; child++) {
                fracture.add(layer(random, depth + 1));
            }
            layer.setFracture(fracture);
        }
        return layer;
    }
}
