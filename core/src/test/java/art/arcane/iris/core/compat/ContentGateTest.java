package art.arcane.iris.core.compat;

import art.arcane.iris.core.compat.ContentGate.BlockResolution;
import art.arcane.iris.core.compat.ContentGate.BlockResolution.Source;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ContentGateTest {
    private static ContentGate gate(CompatFixtures.FakeRegistries registries, Map<String, String> fallbacks) {
        return new ContentGate(registries, fallbacks, new PackCompatReport());
    }

    @Test
    public void presentBlockResolvesFromRegistry() {
        ContentGate gate = gate(CompatFixtures.registries(), Map.of());
        BlockResolution resolution = gate.resolveBlock("STONE");
        assertNotNull(resolution);
        assertEquals("minecraft:stone", resolution.resolvedKey());
        assertEquals("minecraft:stone", resolution.state().key());
        assertFalse(resolution.substituted());
        assertNull(resolution.substitutedFrom());
        assertEquals(Source.REGISTRY, resolution.source());
        assertEquals(KeyStatus.PRESENT, gate.block("minecraft:stone[axis=y]"));
    }

    @Test
    public void missingBlockResolvesToNull() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:sulfur"), Map.of());
        assertNull(gate.resolveBlock("minecraft:sulfur"));
        assertNull(gate.resolveBlock("sulfur[lit=true]"));
        assertEquals(KeyStatus.MISSING, gate.block("minecraft:sulfur"));
    }

    @Test
    public void unknownWhenRegistriesAbsentOrEmpty() {
        ContentGate unbound = gate(null, Map.of());
        assertFalse(unbound.ready());
        assertNull(unbound.resolveBlock("minecraft:stone"));
        assertEquals(KeyStatus.UNKNOWN, unbound.block("minecraft:stone"));
        assertEquals(KeyStatus.UNKNOWN, unbound.entity("minecraft:cow"));

        CompatFixtures.FakeRegistries empty = CompatFixtures.registries();
        empty.blockKeys.clear();
        ContentGate notReady = gate(empty, Map.of());
        assertFalse(notReady.ready());
        assertEquals(KeyStatus.UNKNOWN, notReady.block("minecraft:stone"));
        assertEquals(KeyStatus.UNKNOWN, notReady.item("minecraft:diamond"));
    }

    @Test
    public void perRegistryUnknownWhenThatKeyListIsEmpty() {
        CompatFixtures.FakeRegistries registries = CompatFixtures.registries();
        registries.biomes.clear();
        registries.structures.clear();
        ContentGate gate = gate(registries, Map.of());
        assertTrue(gate.ready());
        assertEquals(KeyStatus.UNKNOWN, gate.biome("minecraft:plains"));
        assertEquals(KeyStatus.UNKNOWN, gate.structure("minecraft:village_plains"));
        assertEquals(KeyStatus.PRESENT, gate.entity("COW"));
        assertEquals(KeyStatus.MISSING, gate.entity("minecraft:camel"));
    }

    @Test
    public void nonBlockRegistriesNormalizeKeys() {
        ContentGate gate = gate(CompatFixtures.registries(), Map.of());
        assertEquals(KeyStatus.PRESENT, gate.item("DIAMOND"));
        assertEquals(KeyStatus.MISSING, gate.item("minecraft:netherite_upgrade_template"));
        assertEquals(KeyStatus.PRESENT, gate.biome("minecraft:plains"));
        assertEquals(KeyStatus.MISSING, gate.biome("minecraft:pale_garden"));
        assertEquals(KeyStatus.PRESENT, gate.enchantment("sharpness"));
        assertEquals(KeyStatus.MISSING, gate.enchantment("minecraft:wind_burst"));
        assertEquals(KeyStatus.PRESENT, gate.potionEffect("SPEED"));
        assertEquals(KeyStatus.PRESENT, gate.potionEffect("INCREASE_DAMAGE"));
        assertEquals(KeyStatus.MISSING, gate.potionEffect("minecraft:infested"));
        assertEquals(KeyStatus.MISSING, gate.item(" "));
    }

    @Test
    public void dimensionFallbackSubstitutesAndIsReported() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:sulfur"), Map.of("minecraft:sulfur", "minecraft:stone"));
        BlockResolution resolution = gate.resolveBlock("minecraft:sulfur[lit=true]");
        assertNotNull(resolution);
        assertTrue(resolution.substituted());
        assertEquals(Source.FALLBACK, resolution.source());
        assertEquals("minecraft:stone", resolution.resolvedKey());
        assertEquals("minecraft:sulfur[lit=true]", resolution.substitutedFrom());
        assertEquals("minecraft:stone", gate.fallbacks().get("minecraft:sulfur"));
    }

    @Test
    public void fallbackThatIsItselfMissingCountsAsMissing() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:sulfur", "minecraft:brimstone"),
                Map.of("minecraft:sulfur", "minecraft:brimstone"));
        assertNull(gate.resolveBlock("minecraft:sulfur"));
    }

    @Test
    public void legacyRenameHitIsSubstitutedButNotReported() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:grass"), Map.of());
        BlockResolution resolution = gate.resolveBlock("minecraft:grass");
        assertNotNull(resolution);
        assertEquals("minecraft:short_grass", resolution.resolvedKey());
        assertEquals(Source.RENAME, resolution.source());
        assertFalse(resolution.substituted());
        assertNull(resolution.substitutedFrom());
        assertTrue(gate.report().isEmpty());
    }

    @Test
    public void legacyRenameFollowsChainsAndGivesUpOnCycles() {
        ContentGate chained = gate(CompatFixtures.registries("minecraft:soul_soil", "minecraft:soulsand"), Map.of());
        assertNull(chained.resolveBlock("minecraft:soul_soil"));

        ContentGate cyclic = gate(CompatFixtures.registries("minecraft:grass", "minecraft:short_grass"), Map.of());
        assertNull(cyclic.resolveBlock("minecraft:grass"));
    }

    @Test
    public void legacyRenameExactEntriesMatchFullState() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:barrel"), Map.of());
        BlockResolution resolution = gate.resolveBlock("minecraft:barrel[facing=east]");
        assertNotNull(resolution);
        assertEquals("minecraft:hay_bale[axis=x]", resolution.resolvedKey());
        assertEquals(Source.RENAME, resolution.source());
    }

    @Test
    public void renameRunsBeforeFallback() {
        ContentGate gate = gate(CompatFixtures.registries("minecraft:grass"), Map.of("minecraft:grass", "minecraft:sand"));
        BlockResolution resolution = gate.resolveBlock("minecraft:grass");
        assertNotNull(resolution);
        assertEquals(Source.RENAME, resolution.source());
        assertEquals("minecraft:short_grass", resolution.resolvedKey());
    }

    @Test
    public void resolutionsAreCachedPerNormalizedState() {
        CompatFixtures.FakeRegistries registries = CompatFixtures.registries("minecraft:sulfur");
        ContentGate gate = gate(registries, Map.of());
        BlockResolution first = gate.resolveBlock("minecraft:stone");
        int after = registries.blockLookups;
        BlockResolution second = gate.resolveBlock("STONE");
        assertSame(first, second);
        assertEquals(after, registries.blockLookups);
        assertNull(gate.resolveBlock("minecraft:sulfur"));
        int misses = registries.blockLookups;
        assertNull(gate.resolveBlock("minecraft:sulfur"));
        assertEquals(misses, registries.blockLookups);
        gate.invalidate();
        gate.resolveBlock("minecraft:stone");
        assertTrue(registries.blockLookups > after);
    }

    @Test
    public void normalizeStateAndBaseKey() {
        assertEquals("minecraft:stone", ContentGate.normalizeState(" STONE "));
        assertEquals("minecraft:oak_log[axis=y]", ContentGate.normalizeState("Oak_Log[AXIS=Y]"));
        assertEquals("create:cogwheel", ContentGate.normalizeState("create:cogwheel"));
        assertEquals("craftengine:forest/chair[randomYaw=true,variant=OakLarge]",
                ContentGate.normalizeState("CraftEngine:Forest/Chair[randomYaw=true,variant=OakLarge]"));
        assertNull(ContentGate.normalizeState(null));
        assertNull(ContentGate.normalizeState("   "));
        assertEquals("minecraft:oak_log", ContentGate.baseKey("minecraft:oak_log[axis=y]"));
        assertEquals("minecraft:oak_log", ContentGate.baseKey("minecraft:oak_log"));
        assertNull(ContentGate.baseKey(null));
    }

    @Test
    public void readBlockFallbacksMergesDimensionFiles() throws Exception {
        java.io.File pack = java.nio.file.Files.createTempDirectory("iris-compat-fallbacks").toFile();
        java.io.File dimensions = new java.io.File(pack, "dimensions");
        assertTrue(dimensions.mkdirs());
        java.nio.file.Files.writeString(new java.io.File(dimensions, "overworld.json").toPath(),
                "{\"name\":\"x\",\"blockFallbacks\":{\"minecraft:sulfur\":\"minecraft:stone\"}}");
        java.nio.file.Files.writeString(new java.io.File(dimensions, "other.json").toPath(),
                "{\"name\":\"y\",\"blockFallbacks\":{\"minecraft:sulfur\":\"minecraft:sand\",\"minecraft:x\":\"minecraft:dirt\"}}");
        Map<String, String> read = ContentGate.readBlockFallbacks(pack);
        assertEquals(2, read.size());
        assertTrue(List.of("minecraft:stone", "minecraft:sand").contains(read.get("minecraft:sulfur")));
        assertEquals("minecraft:dirt", read.get("minecraft:x"));
        assertTrue(ContentGate.readBlockFallbacks(new java.io.File(pack, "nope")).isEmpty());
    }
}
