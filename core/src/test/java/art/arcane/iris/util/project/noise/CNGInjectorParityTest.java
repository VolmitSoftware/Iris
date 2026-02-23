package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.function.NoiseInjector;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CNGInjectorParityTest {
    @BeforeClass
    public static void setupBukkit() {
        if (Bukkit.getServer() != null) {
            return;
        }

        Server server = mock(Server.class);
        BlockData emptyBlockData = mock(BlockData.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn("IrisTestServer").when(server).getName();
        doReturn("1.0").when(server).getVersion();
        doReturn("1.0").when(server).getBukkitVersion();
        doReturn(emptyBlockData).when(server).createBlockData(any(Material.class));
        doReturn(emptyBlockData).when(server).createBlockData(anyString());
        Bukkit.setServer(server);
    }

    @Test
    public void builtInInjectorsMatchLegacyCombineFor1D() {
        List<NoiseInjector> injectors = builtInInjectors();
        for (NoiseInjector injector : injectors) {
            CompositeFixture fixture = createFixture(injector);
            for (int x = -300; x <= 300; x += 17) {
                double expected = legacyCombined1D(fixture, x);
                double actual = fixture.root.noise(x);
                assertEquals("injector=" + injector + " x=" + x, expected, actual, 1.0E-12D);
            }
        }
    }

    @Test
    public void builtInInjectorsMatchLegacyCombineFor2D() {
        List<NoiseInjector> injectors = builtInInjectors();
        for (NoiseInjector injector : injectors) {
            CompositeFixture fixture = createFixture(injector);
            for (int x = -160; x <= 160; x += 19) {
                for (int z = -160; z <= 160; z += 23) {
                    double expected = legacyCombined2D(fixture, x, z);
                    double actual = fixture.root.noise(x, z);
                    assertEquals("injector=" + injector + " x=" + x + " z=" + z, expected, actual, 1.0E-12D);
                }
            }
        }
    }

    @Test
    public void builtInInjectorsMatchLegacyCombineFor3D() {
        List<NoiseInjector> injectors = builtInInjectors();
        for (NoiseInjector injector : injectors) {
            CompositeFixture fixture = createFixture(injector);
            for (int x = -64; x <= 64; x += 11) {
                for (int y = -32; y <= 32; y += 13) {
                    for (int z = -64; z <= 64; z += 17) {
                        double expected = legacyCombined3D(fixture, x, y, z);
                        double actual = fixture.root.noise(x, y, z);
                        assertEquals("injector=" + injector + " x=" + x + " y=" + y + " z=" + z, expected, actual, 1.0E-12D);
                    }
                }
            }
        }
    }

    private CompositeFixture createFixture(NoiseInjector injector) {
        DeterministicNoiseGenerator rootGenerator = new DeterministicNoiseGenerator(0.17D);
        DeterministicNoiseGenerator childGeneratorA = new DeterministicNoiseGenerator(0.43D);
        DeterministicNoiseGenerator childGeneratorB = new DeterministicNoiseGenerator(0.79D);

        CNG childA = new CNG(new RNG(11L), childGeneratorA, 1.0D, 1).bake();
        CNG childB = new CNG(new RNG(12L), childGeneratorB, 1.0D, 1).bake();

        CNG root = new CNG(new RNG(9L), rootGenerator, 1.0D, 1).bake();
        root.child(childA);
        root.child(childB);
        root.injectWith(injector);

        return new CompositeFixture(root, rootGenerator, childA, childB, injector);
    }

    private List<NoiseInjector> builtInInjectors() {
        List<NoiseInjector> injectors = new ArrayList<>();
        injectors.add(CNG.ADD);
        injectors.add(CNG.SRC_SUBTRACT);
        injectors.add(CNG.DST_SUBTRACT);
        injectors.add(CNG.MULTIPLY);
        injectors.add(CNG.MAX);
        injectors.add(CNG.MIN);
        injectors.add(CNG.SRC_MOD);
        injectors.add(CNG.SRC_POW);
        injectors.add(CNG.DST_MOD);
        injectors.add(CNG.DST_POW);
        return injectors;
    }

    private double legacyCombined1D(CompositeFixture fixture, double x) {
        double n = fixture.rootGenerator.noise(x, 0D, 0D);
        double m = 1D;

        double valueA = fixture.childA.noise(x);
        double[] combinedA = fixture.injector.combine(n, valueA);
        n = combinedA[0];
        m += combinedA[1];

        double valueB = fixture.childB.noise(x);
        double[] combinedB = fixture.injector.combine(n, valueB);
        n = combinedB[0];
        m += combinedB[1];

        return n / m;
    }

    private double legacyCombined2D(CompositeFixture fixture, double x, double z) {
        double n = fixture.rootGenerator.noise(x, z, 0D);
        double m = 1D;

        double valueA = fixture.childA.noise(x, z);
        double[] combinedA = fixture.injector.combine(n, valueA);
        n = combinedA[0];
        m += combinedA[1];

        double valueB = fixture.childB.noise(x, z);
        double[] combinedB = fixture.injector.combine(n, valueB);
        n = combinedB[0];
        m += combinedB[1];

        return n / m;
    }

    private double legacyCombined3D(CompositeFixture fixture, double x, double y, double z) {
        double n = fixture.rootGenerator.noise(x, y, z);
        double m = 1D;

        double valueA = fixture.childA.noise(x, y, z);
        double[] combinedA = fixture.injector.combine(n, valueA);
        n = combinedA[0];
        m += combinedA[1];

        double valueB = fixture.childB.noise(x, y, z);
        double[] combinedB = fixture.injector.combine(n, valueB);
        n = combinedB[0];
        m += combinedB[1];

        return n / m;
    }

    private static class CompositeFixture {
        private final CNG root;
        private final DeterministicNoiseGenerator rootGenerator;
        private final CNG childA;
        private final CNG childB;
        private final NoiseInjector injector;

        private CompositeFixture(CNG root, DeterministicNoiseGenerator rootGenerator, CNG childA, CNG childB, NoiseInjector injector) {
            this.root = root;
            this.rootGenerator = rootGenerator;
            this.childA = childA;
            this.childB = childB;
            this.injector = injector;
        }
    }

    private static class DeterministicNoiseGenerator implements NoiseGenerator {
        private final double offset;

        private DeterministicNoiseGenerator(double offset) {
            this.offset = offset;
        }

        @Override
        public double noise(double x) {
            double angle = (x * 0.013D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double z) {
            double angle = (x * 0.011D) + (z * 0.017D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double y, double z) {
            double angle = (x * 0.007D) + (y * 0.013D) + (z * 0.019D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }
    }
}
