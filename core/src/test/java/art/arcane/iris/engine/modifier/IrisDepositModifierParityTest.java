package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDepositGenerator;
import art.arcane.iris.engine.object.IrisDepositHeightDistribution;
import art.arcane.iris.engine.object.IrisDepositShape;
import art.arcane.iris.engine.object.IrisDepositVariant;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisDepositModifierParityTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final Map<String, PlatformBlockState> states = new HashMap<>();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void configuredShapesKeepRecordedBlocksAcrossScopesAndCavePolicies() {
        long[] seeds = {101L, -73L, 90181L};
        long[][] expected = {
                {-4609692219192155345L, 784L, 2118L, 53L, 67L, 11L},
                {-331296667371868787L, 818L, 2413L, 94L, 49L, 20L},
                {-3365014205342608518L, 641L, 2340L, 58L, 45L, 27L}
        };
        for (int i = 0; i < seeds.length; i++) {
            for (boolean multicore : new boolean[]{false, true}) {
                assertArrayEquals("seed=" + seeds[i] + ", multicore=" + multicore,
                        expected[i], generateConfigured(seeds[i], multicore));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private long[] generateConfigured(long seed, boolean multicore) {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        ChunkContext context = mock(ChunkContext.class, RETURNS_DEEP_STUBS);
        IrisDimension dimension = new IrisDimension();
        IrisRegion region = new IrisRegion();
        IrisBiome surface = new IrisBiome();
        IrisBiome cave = new IrisBiome();
        cave.setLoadKey("allowed");
        cave.setOreDepositFrequencyMultiplier(0.65D);
        cave.setOreDepositSizeMultiplier(1.5D);
        IrisBiome denied = new IrisBiome();
        denied.setLoadKey("denied");
        IrisDepositVariant variant = new IrisDepositVariant().setMinHeight(-64).setMaxHeight(64);
        variant.getRemap().put("minecraft:gold_ore", "minecraft:redstone_ore");
        cave.getDepositVariants().add(variant);
        dimension.getDeposits().add(generator("granite", IrisDepositShape.IRIS, 25, 70,
                IrisDepositHeightDistribution.CLIPPED_UNIFORM, "stone", "deepslate"));
        region.getDeposits().add(generator("iron_ore", IrisDepositShape.VANILLA_ELLIPSOID, 20, 110,
                IrisDepositHeightDistribution.TRIANGLE, "stone", "granite", "deepslate"));
        IrisDepositGenerator diamond = generator("diamond_ore", IrisDepositShape.VANILLA_SCATTERED, 12, 100,
                IrisDepositHeightDistribution.UNIFORM, "iron_ore", "deepslate_iron_ore", "granite");
        diamond.setIncludedBiomes(new KList<>("allowed"));
        diamond.setDiscardChanceOnAirExposure(0.5D);
        surface.getDeposits().add(diamond);
        IrisDepositGenerator gold = generator("gold_ore", IrisDepositShape.VANILLA_ELLIPSOID, 9, 70,
                IrisDepositHeightDistribution.TRIANGLE, "stone", "granite");
        gold.setExcludedBiomes(new KList<>("allowed"));
        gold.setDiscardChanceOnAirExposure(0.3D);
        surface.getDeposits().add(gold);
        when(engine.getHeight()).thenReturn(48);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getSeedManager().getDeposit()).thenReturn(seed);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            return ((Math.floorDiv(x, 8) + Math.floorDiv(z, 8) + Math.floorDiv(y, 8)) & 1) == 0 ? cave : denied;
        });
        when(context.getRoundedHeight(anyInt(), anyInt())).thenReturn(40);
        when(context.getBiome().get(anyInt(), anyInt())).thenReturn(surface);
        when(context.getRegion().get(anyInt(), anyInt())).thenReturn(region);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        doReturn(chunk).when(mantle).getChunk(-3, 5);
        when(chunk.use()).thenReturn(chunk);
        MatterCavern cavern = new MatterCavern(true, "allowed", (byte) 0);
        when(chunk.get(anyInt(), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            return x == 4 && y % 7 == 0 ? cavern : null;
        });
        Hunk<PlatformBlockState> terrain = Hunk.newArrayHunk(16, 48, 16);
        for (int y = 0; y < 48; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    terrain.set(x, y, z, state(y >= 40 ? "air" : y == 0 ? "bedrock"
                            : x == 7 && z % 4 == 0 && y % 9 == 0 ? "cave_air"
                            : y < 12 ? "deepslate" : "stone"));
                }
            }
        }
        MultiBurst burst = new MultiBurst("Deposit parity", () -> 4);
        when(engine.burst()).thenReturn(burst);
        try (MockedStatic<B> blocks = mockStatic(B.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> B.getStateOrNull(anyString(), anyBoolean()))
                    .thenAnswer(invocation -> state(invocation.getArgument(0)));
            blocks.when(() -> B.toDeepSlateOre(any(), any())).thenAnswer(invocation -> {
                PlatformBlockState host = invocation.getArgument(0);
                PlatformBlockState ore = invocation.getArgument(1);
                return host.isDeepSlate() && ore.isOre()
                        ? state("deepslate_" + ore.key().substring(10)) : ore;
            });
            new IrisDepositModifier(engine).generateDeposits(terrain, -3, 5, multicore, context);
        } finally {
            burst.close();
        }
        long[] result = {0xcbf29ce484222325L, 0L, 0L, 0L, 0L, 0L};
        for (int y = 0; y < 48; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    PlatformBlockState block = terrain.get(x, y, z);
                    result[0] = (result[0] ^ block.key().hashCode()) * 0x100000001b3L;
                    if (block.key().endsWith("granite")) {
                        result[1]++;
                    }
                    if (block.key().endsWith("iron_ore")) {
                        result[2]++;
                    }
                    if (block.key().endsWith("diamond_ore")) {
                        result[3]++;
                    }
                    if (block.key().endsWith("gold_ore")) {
                        result[4]++;
                    }
                    if (block.key().endsWith("redstone_ore")) {
                        result[5]++;
                    }
                    if (x == 4 && y % 7 == 0 && y > 0 && y < 40) {
                        assertEquals(y < 12 ? "minecraft:deepslate" : "minecraft:stone", block.key());
                    }
                }
            }
        }
        return result;
    }

    private IrisDepositGenerator generator(String block, IrisDepositShape shape, int size, int attempts,
                                          IrisDepositHeightDistribution distribution, String... hosts) {
        IrisDepositGenerator generator = new PaletteGenerator(state(block));
        return generator.setShape(shape).setMinSize(size).setMaxSize(size)
                .setMinPerChunk(attempts).setMaxPerChunk(attempts)
                .setMinHeight(-4).setMaxHeight(44).setHeightDistribution(distribution)
                .setSurfaceClearance(0).setPerClumpSpawnChance(0.7D)
                .setReplaceableBlocks(new KList<>(hosts));
    }

    private PlatformBlockState state(String block) {
        String key = block.contains(":") ? block : "minecraft:" + block;
        return states.computeIfAbsent(key, KeyedBlockState::new);
    }

    private static final class KeyedBlockState implements PlatformBlockState {
        private final String key;
        private final boolean ore;
        private final boolean deepSlate;
        private final boolean air;

        private KeyedBlockState(String key) {
            this.key = key;
            this.ore = key.endsWith("_ore");
            this.deepSlate = key.contains("deepslate");
            this.air = key.endsWith("air");
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            return null;
        }

        @Override
        public String materialKey() {
            return key;
        }

        @Override
        public boolean isAir() {
            return air;
        }

        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public String deferredPlacementKey() {
            return null;
        }

        @Override
        public PlatformBlockState placementBaseState() {
            return null;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return ore;
        }

        @Override
        public boolean isDeepSlate() {
            return deepSlate;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            return false;
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return false;
        }

        @Override
        public boolean isAirOrFluid() {
            return false;
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            return null;
        }

        @Override
        public Object nativeHandle() {
            return null;
        }
    }

    private static final class PaletteGenerator extends IrisDepositGenerator {
        private final KList<PlatformBlockState> blocks;

        private PaletteGenerator(PlatformBlockState block) {
            blocks = new KList<>(block);
        }

        @Override
        public KList<PlatformBlockState> getBlockData(IrisData data) {
            return blocks;
        }
    }
}
