package art.arcane.iris.pack.datapack.export;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VanillaResourceIdentifierTest {
    @Test
    public void namespacesAcceptOnlyTheVanillaCharacterSet() {
        assertTrue(VanillaResourceIdentifier.validNamespace("minecraft"));
        assertTrue(VanillaResourceIdentifier.validNamespace("iris_pack-1.0"));
        assertFalse(VanillaResourceIdentifier.validNamespace(null));
        assertFalse(VanillaResourceIdentifier.validNamespace(""));
        assertFalse(VanillaResourceIdentifier.validNamespace("Minecraft"));
        assertFalse(VanillaResourceIdentifier.validNamespace("iris/pack"));
    }

    @Test
    public void pathsAllowNestingButNeverTraversal() {
        assertTrue(VanillaResourceIdentifier.validPath("village/plains/houses"));
        assertTrue(VanillaResourceIdentifier.validPath("empty"));
        assertFalse(VanillaResourceIdentifier.validPath("/village"));
        assertFalse(VanillaResourceIdentifier.validPath("village/"));
        assertFalse(VanillaResourceIdentifier.validPath("village//houses"));
        assertFalse(VanillaResourceIdentifier.validPath("village/../secrets"));
        assertFalse(VanillaResourceIdentifier.validPath("village/./houses"));
        assertFalse(VanillaResourceIdentifier.validPath("Village"));
    }

    @Test
    public void identifiersRequireExactlyOneSeparatorWithBothHalvesPresent() {
        assertTrue(VanillaResourceIdentifier.validIdentifier("minecraft:village/plains"));
        assertFalse(VanillaResourceIdentifier.validIdentifier("village"));
        assertFalse(VanillaResourceIdentifier.validIdentifier(":village"));
        assertFalse(VanillaResourceIdentifier.validIdentifier("minecraft:"));
        assertFalse(VanillaResourceIdentifier.validIdentifier("minecraft:village:plains"));
        assertFalse(VanillaResourceIdentifier.validIdentifier(null));
    }

    @Test
    public void anAbsentConnectorTargetBecomesTheVanillaEmptyPool() {
        assertEquals("minecraft:empty", VanillaResourceIdentifier.normalizeConnectorIdentifier(null));
        assertEquals("minecraft:empty", VanillaResourceIdentifier.normalizeConnectorIdentifier(""));
        assertEquals("minecraft:empty", VanillaResourceIdentifier.normalizeConnectorIdentifier("   "));
    }

    @Test
    public void abareConnectorNameIsQualifiedIntoTheMinecraftNamespace() {
        assertEquals("minecraft:bottom", VanillaResourceIdentifier.normalizeConnectorIdentifier("bottom"));
        assertEquals("minecraft:bottom", VanillaResourceIdentifier.normalizeConnectorIdentifier("  bottom  "));
        assertEquals("iris:side", VanillaResourceIdentifier.normalizeConnectorIdentifier("iris:side"));
    }

    @Test
    public void aMalformedConnectorTargetIsRejectedWithItsOriginalText() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> VanillaResourceIdentifier.normalizeConnectorIdentifier("Iris:Side"));

        assertTrue(failure.getMessage(), failure.getMessage().contains("Iris:Side"));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaResourceIdentifier.normalizeConnectorIdentifier("iris:../escape"));
    }

    @Test
    public void requireChecksReturnTheValueAndNameTheRoleOnFailure() {
        assertEquals("iris_export", VanillaResourceIdentifier.requireNamespace("iris_export", "export namespace"));
        assertEquals("caves/deep", VanillaResourceIdentifier.requirePath("caves/deep", "biome id"));
        assertEquals("minecraft:stone", VanillaResourceIdentifier.requireIdentifier("minecraft:stone", "block id"));

        IllegalArgumentException path = assertThrows(IllegalArgumentException.class,
                () -> VanillaResourceIdentifier.requirePath("Caves", "biome id"));
        assertEquals("Invalid biome id 'Caves'", path.getMessage());
        IllegalArgumentException namespace = assertThrows(IllegalArgumentException.class,
                () -> VanillaResourceIdentifier.requireNamespace(null, "export namespace"));
        assertEquals("Invalid export namespace 'null'", namespace.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> VanillaResourceIdentifier.requireIdentifier("stone", "block id"));
    }

    @Test
    public void sanitizedPathsAreLowercaseValidAndKeepFolders() {
        assertEquals("forest_biomes/dark_oak", VanillaResourceIdentifier.sanitizePath("Forest Biomes/Dark Oak"));
        assertEquals("caves/deep", VanillaResourceIdentifier.sanitizePath("caves\\deep"));
        assertEquals("a/b", VanillaResourceIdentifier.sanitizePath("/a//b/"));
        assertEquals("__/x", VanillaResourceIdentifier.sanitizePath("../x"));
        assertEquals("caf_", VanillaResourceIdentifier.sanitizePath("Caf\u00e9"));
        assertTrue(VanillaResourceIdentifier.validPath(VanillaResourceIdentifier.sanitizePath("../Odd Name!/x.y")));
        assertThrows(IllegalArgumentException.class, () -> VanillaResourceIdentifier.sanitizePath("///"));
        assertThrows(IllegalArgumentException.class, () -> VanillaResourceIdentifier.sanitizePath(" "));
    }
}
