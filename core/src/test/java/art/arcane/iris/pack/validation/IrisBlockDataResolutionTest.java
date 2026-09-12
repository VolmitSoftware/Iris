package art.arcane.iris.pack.validation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.spi.PlatformBlockState;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisBlockDataResolutionTest {
    private static CompatFixtures.FakeRegistries registries;

    @BeforeClass
    public static void bindPlatform() {
        registries = CompatFixtures.registries("minecraft:sulfur", "minecraft:brimstone", "minecraft:grass");
        CompatFixtures.bind(registries);
    }

    @AfterClass
    public static void unbindPlatform() {
        CompatFixtures.unbind();
    }

    private static IrisData pack(String dimensionJson, String customBlockJson) throws Exception {
        File folder = Files.createTempDirectory("iris-compat-blockdata").toFile();
        if (dimensionJson != null) {
            File dimensions = new File(folder, "dimensions");
            assertTrue(dimensions.mkdirs());
            Files.writeString(new File(dimensions, "overworld.json").toPath(), dimensionJson);
        }
        if (customBlockJson != null) {
            File blocks = new File(folder, "blocks");
            assertTrue(blocks.mkdirs());
            Files.writeString(new File(blocks, "fancy.json").toPath(), customBlockJson);
        }
        return IrisData.get(folder);
    }

    @Test
    public void presentBlockResolvesWithProperties() throws Exception {
        IrisData data = pack(null, null);
        IrisBlockData entry = new IrisBlockData("minecraft:oak_log");
        entry.getData().put("axis", "y");
        assertEquals("minecraft:oak_log[axis=y]", entry.getBlockData(data).key());
        assertEquals("minecraft:stone", new IrisBlockData("STONE").getBlockData(data).key());
    }

    @Test
    public void backupRevivesMissingBlock() throws Exception {
        IrisData data = pack(null, null);
        IrisBlockData entry = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:sand"));
        assertEquals("minecraft:sand", entry.getBlockData(data).key());

        IrisBlockData chained = new IrisBlockData("minecraft:sulfur")
                .setBackup(new IrisBlockData("minecraft:brimstone").setBackup(new IrisBlockData("minecraft:sand")));
        assertEquals("minecraft:sand", chained.getBlockData(data).key());
    }

    @Test
    public void missingBlockWithoutBackupFallsBackToAir() throws Exception {
        IrisData data = pack(null, null);
        IrisBlockData entry = new IrisBlockData("minecraft:sulfur");
        assertTrue(entry.getBlockData(data).isAir());
        assertTrue(new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:brimstone")).getBlockData(data).isAir());
    }

    @Test
    public void dimensionFallbackAppliesBeforeBackup() throws Exception {
        IrisData data = pack("{\"name\":\"overworld\",\"blockFallbacks\":{\"minecraft:sulfur\":\"minecraft:stone\"}}", null);
        IrisBlockData entry = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:sand"));
        assertEquals("minecraft:stone", entry.getBlockData(data).key());
    }

    @Test
    public void legacyRenameAppliesOnEveryPlatform() throws Exception {
        IrisData data = pack(null, null);
        assertEquals("minecraft:short_grass", new IrisBlockData("minecraft:grass").getBlockData(data).key());
        assertTrue(data.getCompatReport().isEmpty());
    }

    @Test
    public void customPackBlockStillResolvesFirstAndUsesItsOwnBackup() throws Exception {
        IrisData data = pack(null, "{\"block\":\"minecraft:sulfur\",\"backup\":{\"block\":\"minecraft:sand\"}}");
        IrisBlockData entry = new IrisBlockData("fancy");
        assertEquals("minecraft:sand", entry.getBlockData(data).key());
        assertFalse(data.getCompatReport().isEmpty());
        assertEquals(CompatAction.SUBSTITUTED, data.getCompatReport().findings().getFirst().action());
        assertEquals("block", data.getCompatReport().findings().getFirst().subjectType());
    }

    @Test
    public void nestedPackBlocksPreserveInheritedProviderProperties() throws Exception {
        IrisData data = pack(null, "{\"block\":\"base\",\"data\":{\"variant\":\"large\"}}");
        File blocks = new File(data.getDataFolder(), "blocks");
        Files.writeString(new File(blocks, "base.json").toPath(),
                "{\"block\":\"craftengine:forest/amber_log\",\"data\":{\"axis\":\"y\",\"yaw\":22.5}}");
        IrisBlockData entry = new IrisBlockData("fancy");
        entry.getData().put("axis", "x");

        PlatformBlockState resolved = entry.getBlockData(data);
        IrisBlockData parsed = IrisBlockData.from(resolved.key());

        assertEquals("craftengine:forest/amber_log", parsed.getBlock());
        assertEquals("x", parsed.getData().get("axis"));
        assertEquals("large", parsed.getData().get("variant"));
        assertEquals(22.5, (Double) parsed.getData().get("yaw"), 0.0);
    }

    @Test
    public void providerPropertiesKeepTheirCaseThroughDirectResolution() throws Exception {
        IrisData data = pack(null, null);
        IrisBlockData entry = new IrisBlockData("craftengine:forest/chair");
        entry.getData().put("randomYaw", true);
        entry.getData().put("variant", "OakLarge");

        PlatformBlockState resolved = entry.getBlockData(data);
        IrisBlockData parsed = IrisBlockData.from(resolved.key());

        assertEquals(true, parsed.getData().get("randomYaw"));
        assertEquals("OakLarge", parsed.getData().get("variant"));
        assertTrue(resolved.key().contains("randomYaw=true"));
        assertTrue(resolved.key().contains("variant=OakLarge"));
    }

    @Test
    public void nestedPackBlocksKeepInheritedProviderPropertyCase() throws Exception {
        IrisData data = pack(null, "{\"block\":\"base\",\"data\":{\"yaw\":22.5}}");
        File blocks = new File(data.getDataFolder(), "blocks");
        Files.writeString(new File(blocks, "base.json").toPath(),
                "{\"block\":\"craftengine:forest/chair\",\"data\":{\"randomYaw\":true,\"variant\":\"OakLarge\"}}");
        IrisBlockData entry = new IrisBlockData("fancy");
        entry.getData().put("pitch", 12.75);

        IrisBlockData parsed = IrisBlockData.from(entry.getBlockData(data).key());

        assertEquals(true, parsed.getData().get("randomYaw"));
        assertEquals("OakLarge", parsed.getData().get("variant"));
        assertEquals(22.5, (Double) parsed.getData().get("yaw"), 0.0);
        assertEquals(12.75, (Double) parsed.getData().get("pitch"), 0.0);
    }

    @Test
    public void parsedVanillaPropertiesStillNormalizeCase() {
        IrisBlockData entry = IrisBlockData.from("OAK_LOG[AXIS=Y]");

        assertEquals("minecraft:oak_log[axis=y]", entry.stateKey());
    }

    @Test
    public void decimalProviderPropertiesRetainPrecision() {
        IrisBlockData entry = IrisBlockData.from("craftengine:forest/lantern[yaw=22.5,pitch=-12.75]");

        assertEquals(22.5, (Double) entry.getData().get("yaw"), 0.0);
        assertEquals(-12.75, (Double) entry.getData().get("pitch"), 0.0);
        assertTrue(entry.stateKey().contains("yaw=22.5"));
        assertTrue(entry.stateKey().contains("pitch=-12.75"));
    }

    @Test
    public void wholeNumberPropertiesKeepIntegerSyntax() {
        IrisBlockData entry = new IrisBlockData("minecraft:wheat");
        entry.getData().put("age", 4.0);

        assertEquals("minecraft:wheat[age=4]", entry.stateKey());
    }

    @Test
    public void providerReadinessClearsAuthoringBlockAndGateMisses() throws Exception {
        String blockKey = "lateblocks:example/stone";
        registries.missingBlocks.add(blockKey);
        IrisData data = pack(null, "{\"block\":\"" + blockKey + "\"}");
        try {
            IrisBlockData missing = data.getBlockLoader().load("fancy", false);
            assertTrue(missing.getBlockData(data).isAir());
            ContentGate originalGate = data.getContentGate();
            registries.missingBlocks.remove(blockKey);

            IrisData.invalidateLoadedContentRegistries(data.getDataFolder());

            IrisBlockData resolved = data.getBlockLoader().load("fancy", false);
            assertNotSame(missing, resolved);
            assertNotSame(originalGate, data.getContentGate());
            assertEquals(blockKey, resolved.getBlockData(data).key());
        } finally {
            registries.missingBlocks.remove(blockKey);
        }
    }

    @Test
    public void providerReadinessLeavesEngineAttachedCachesIntact() throws Exception {
        IrisData data = pack(null, "{\"block\":\"minecraft:stone\"}");
        IrisBlockData block = data.getBlockLoader().load("fancy", false);
        ContentGate gate = data.getContentGate();
        Engine engine = mock(Engine.class);
        data.registerEngine(engine);
        try {
            IrisData.invalidateLoadedContentRegistries(data.getDataFolder());

            assertSame(block, data.getBlockLoader().load("fancy", false));
            assertSame(gate, data.getContentGate());
        } finally {
            data.unregisterEngine(engine);
        }
    }
}
