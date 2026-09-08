package art.arcane.iris.spi;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PlatformValueContractTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    public void jigsawMetadataAcceptsTheWholeDocumentedRange() {
        PlatformStructureHooks.JigsawSourceMetadata smallest =
                new PlatformStructureHooks.JigsawSourceMetadata(1, 0, 0);
        PlatformStructureHooks.JigsawSourceMetadata largest =
                new PlatformStructureHooks.JigsawSourceMetadata(128, 7, 96);

        assertEquals(1, smallest.maxDistanceHorizontal());
        assertEquals(128, largest.maxDistanceHorizontal());
        assertEquals(7, largest.referenceExpansion());
        assertEquals(96, largest.maxStartElementHorizontalSpan());
    }

    @Test
    public void jigsawMetadataDefaultsTheStartElementSpanToZero() {
        PlatformStructureHooks.JigsawSourceMetadata metadata =
                new PlatformStructureHooks.JigsawSourceMetadata(64, 3);

        assertEquals(0, metadata.maxStartElementHorizontalSpan());
    }

    @Test
    public void jigsawMetadataRejectsOutOfRangeGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformStructureHooks.JigsawSourceMetadata(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformStructureHooks.JigsawSourceMetadata(129, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformStructureHooks.JigsawSourceMetadata(64, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformStructureHooks.JigsawSourceMetadata(64, 0, -1));
    }

    @Test
    public void unimplementedStructureHookDefaultsRefuseInsteadOfReturningNothing() {
        PlatformStructureHooks hooks = new MinimalStructureHooks();

        assertEquals(List.of(), hooks.jigsawStructureKeys());
        assertEquals(List.of(), hooks.templatePoolKeys());
        assertThrows(UnsupportedOperationException.class, () -> hooks.jigsawSourceMetadata("iris:village"));
        assertThrows(UnsupportedOperationException.class, () -> hooks.templatePoolHorizontalSpan("iris:pool"));
        assertThrows(UnsupportedOperationException.class,
                () -> hooks.jigsawStartPoolHorizontalSpan("iris:village", "iris:pool"));
    }

    @Test
    public void definitionFactoriesCarryTheirEncoding() {
        PlatformGenerationRegistry.Definition json = PlatformGenerationRegistry.Definition.exactJson("{}");
        PlatformGenerationRegistry.Definition identity =
                PlatformGenerationRegistry.Definition.conservativeIdentity("abc");

        assertEquals(PlatformGenerationRegistry.Encoding.JSON, json.encoding());
        assertEquals("{}", json.value());
        assertEquals(PlatformGenerationRegistry.Encoding.CONSERVATIVE_IDENTITY, identity.encoding());
        assertEquals("abc", identity.value());
    }

    @Test
    public void resourceIdentityTrimsItsPartsIntoAStableKey() {
        PlatformGenerationRegistry.Definition definition =
                PlatformGenerationRegistry.Definition.resourceIdentity("  worldgen/biome ", " iris:plains  ");

        assertEquals(PlatformGenerationRegistry.Encoding.CONSERVATIVE_IDENTITY, definition.encoding());
        assertEquals("registry-resource-key-v1|worldgen/biome|iris:plains", definition.value());
    }

    @Test
    public void definitionsRejectMissingAndBlankValues() {
        assertThrows(NullPointerException.class,
                () -> new PlatformGenerationRegistry.Definition(null, "value"));
        assertThrows(NullPointerException.class,
                () -> new PlatformGenerationRegistry.Definition(PlatformGenerationRegistry.Encoding.JSON, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformGenerationRegistry.Definition(PlatformGenerationRegistry.Encoding.JSON, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformGenerationRegistry.Definition.resourceIdentity("   ", "iris:plains"));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformGenerationRegistry.Definition.resourceIdentity("worldgen/biome", "   "));
    }

    @Test
    public void customBiomeResourceKeysAreContentAddressed() {
        assertEquals("iris:biomes/" + FINGERPRINT,
                PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(FINGERPRINT));
    }

    @Test
    public void customBiomeResourceKeysRejectAnythingButALowercaseSha256() {
        assertThrows(NullPointerException.class,
                () -> PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(null));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(FINGERPRINT.toUpperCase()));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey("a".repeat(63)));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey("z".repeat(64)));
    }

    @Test
    public void blockPropertiesReportWhetherTheyCarryANumericRange() {
        PlatformNumericRange range = new PlatformNumericRange(0D, 15D, false, true);
        PlatformBlockProperty numeric =
                new PlatformBlockProperty("level", "integer", 0, List.of(0, 15), range);
        PlatformBlockProperty enumerated =
                new PlatformBlockProperty("axis", "string", "y", List.of("x", "y", "z"), null);

        assertTrue(numeric.hasNumericRange());
        assertEquals(range, numeric.numericRange());
        assertEquals(0D, range.minimum(), 0D);
        assertEquals(15D, range.maximum(), 0D);
        assertFalse(range.exclusiveMinimum());
        assertTrue(range.exclusiveMaximum());
        assertFalse(enumerated.hasNumericRange());
        assertEquals(List.of("x", "y", "z"), enumerated.allowedValues());
    }

    private static final class MinimalStructureHooks implements PlatformStructureHooks {
        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> structureSetKeys() {
            return List.of();
        }

        @Override
        public List<String> structureBiomeKeys(String structureKey) {
            return List.of();
        }

        @Override
        public List<String> objectFeatureKeys() {
            return List.of();
        }

        @Override
        public List<String> reachableStructureKeys(PlatformWorld world) {
            return List.of();
        }

        @Override
        public List<String> possibleBiomeKeys(PlatformWorld world) {
            return List.of();
        }

        @Override
        public boolean placeFeature(PlatformWorld world, int x, int y, int z, String featureKey, long seed) {
            return false;
        }

        @Override
        public int[] placeStructure(PlatformWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
            return new int[0];
        }

        @Override
        public boolean supportsStructurePlacement() {
            return false;
        }
    }
}
