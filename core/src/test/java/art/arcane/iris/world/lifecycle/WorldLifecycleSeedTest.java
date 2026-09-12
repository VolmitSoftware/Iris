package art.arcane.iris.world.lifecycle;

import org.junit.Test;

import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WorldLifecycleSeedTest {
    @Test
    public void appliesRequestedSeedToFreshWorldData() throws Exception {
        TestWorldOptions options = new TestWorldOptions(111L, true, false);
        Object dimensions = new Object();
        TestWorldGenSettings genSettings = new TestWorldGenSettings(options, dimensions);
        Object data = new Object();
        TestWorldDataAndGenSettings input = new TestWorldDataAndGenSettings(data, genSettings);

        Object result = WorldLifecycleSupport.applySeedToWorldDataAndGenSettings(input, 69420L);

        assertTrue(result instanceof TestWorldDataAndGenSettings);
        TestWorldDataAndGenSettings typed = (TestWorldDataAndGenSettings) result;
        assertSame(data, typed.data());
        assertSame(dimensions, typed.genSettings().dimensions());
        assertEquals(69420L, typed.genSettings().options().seed());
        assertTrue(typed.genSettings().options().generateStructures());
    }

    @Test
    public void returnsSameInstanceWhenSeedAlreadyMatches() throws Exception {
        TestWorldOptions options = new TestWorldOptions(69420L, true, false);
        TestWorldGenSettings genSettings = new TestWorldGenSettings(options, new Object());
        TestWorldDataAndGenSettings input = new TestWorldDataAndGenSettings(new Object(), genSettings);

        Object result = WorldLifecycleSupport.applySeedToWorldDataAndGenSettings(input, 69420L);

        assertSame(input, result);
    }

    public static final class TestWorldDataAndGenSettings {
        private final Object data;
        private final TestWorldGenSettings genSettings;

        public TestWorldDataAndGenSettings(Object data, TestWorldGenSettings genSettings) {
            this.data = data;
            this.genSettings = genSettings;
        }

        public Object data() {
            return data;
        }

        public TestWorldGenSettings genSettings() {
            return genSettings;
        }
    }

    public static final class TestWorldGenSettings {
        private final TestWorldOptions options;
        private final Object dimensions;

        public TestWorldGenSettings(TestWorldOptions options, Object dimensions) {
            this.options = options;
            this.dimensions = dimensions;
        }

        public TestWorldOptions options() {
            return options;
        }

        public Object dimensions() {
            return dimensions;
        }
    }

    public static final class TestWorldOptions {
        private final long seed;
        private final boolean generateStructures;
        private final boolean generateBonusChest;

        public TestWorldOptions(long seed, boolean generateStructures, boolean generateBonusChest) {
            this.seed = seed;
            this.generateStructures = generateStructures;
            this.generateBonusChest = generateBonusChest;
        }

        public long seed() {
            return seed;
        }

        public boolean generateStructures() {
            return generateStructures;
        }

        public boolean generateBonusChest() {
            return generateBonusChest;
        }

        public TestWorldOptions withSeed(OptionalLong newSeed) {
            return new TestWorldOptions(newSeed.orElse(seed), generateStructures, generateBonusChest);
        }
    }
}
