package art.arcane.iris.core.pack;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackValidationCacheTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheRoundTripsSuccessfulAndFailedResultsInStableOrder() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("round-trip"), "validation.json").toPath();
        PackValidationResult valid = new PackValidationResult(
                "z-valid", List.of(), List.of("warning"), 20L);
        PackValidationResult invalid = new PackValidationResult(
                "a-invalid", List.of("broken reference"), List.of(), 10L);

        PackValidationCache.save(cache, "content", "context", List.of(valid, invalid));
        List<PackValidationResult> loaded = PackValidationCache.load(
                cache,
                "content",
                "context",
                List.of("z-valid", "a-invalid")).orElseThrow();

        assertEquals(List.of("a-invalid", "z-valid"), loaded.stream()
                .map(PackValidationResult::getPackName)
                .toList());
        assertFalse(loaded.getFirst().isLoadable());
        assertEquals(List.of("broken reference"), loaded.getFirst().getBlockingErrors());
        assertTrue(loaded.getLast().isLoadable());
        assertEquals(List.of("warning"), loaded.getLast().getWarnings());
    }

    @Test
    public void cacheRejectsContentContextAndPackSetChanges() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("mismatch"), "validation.json").toPath();
        PackValidationCache.save(cache, "content", "context", List.of(
                new PackValidationResult("overworld", List.of(), List.of(), 1L)));

        assertTrue(PackValidationCache.load(
                cache, "changed", "context", List.of("overworld")).isEmpty());
        assertTrue(PackValidationCache.load(
                cache, "content", "changed", List.of("overworld")).isEmpty());
        assertTrue(PackValidationCache.load(
                cache, "content", "context", List.of("other")).isEmpty());
    }

    @Test
    public void cacheRoundTripsCompatFindingsAndMinecraftVersion() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("compat-round-trip"), "validation.json").toPath();
        CompatFinding excluded = new CompatFinding(
                CompatRegistry.BLOCK, "minecraft:sulfur", CompatAction.EXCLUDED,
                "Biome", "cave/sulfur-grotto", "layers[0].palette[1]");
        CompatFinding dropped = new CompatFinding(
                CompatRegistry.ENTITY, "minecraft:camel", CompatAction.DROPPED, "Spawner", "desert", "spawns[0]");
        PackValidationCache.save(cache, "content", "context", List.of(new PackValidationResult(
                "overworld", List.of(), List.of("warning"), 7L, List.of(excluded, dropped), "26.1.2")));

        PackValidationResult loaded = PackValidationCache.load(
                cache, "content", "context", List.of("overworld")).orElseThrow().getFirst();

        assertEquals("26.1.2", loaded.getMinecraftVersion());
        assertEquals(List.of(excluded, dropped), loaded.getCompatFindings());
    }

    @Test
    public void resultsWithoutGatingRoundTripAsEmptyFindings() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("compat-empty"), "validation.json").toPath();
        PackValidationCache.save(cache, "content", "context", List.of(
                new PackValidationResult("overworld", List.of(), List.of(), 1L)));

        PackValidationResult loaded = PackValidationCache.load(
                cache, "content", "context", List.of("overworld")).orElseThrow().getFirst();

        assertTrue(loaded.getCompatFindings().isEmpty());
        assertNull(loaded.getMinecraftVersion());
    }

    @Test
    public void cacheWithAnUnreadableFindingIsIgnored() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("compat-corrupt"), "validation.json").toPath();
        Files.writeString(cache, """
                {
                  "schemaVersion": 3,
                  "contentFingerprint": "content",
                  "contextFingerprint": "context",
                  "results": [
                    {"packName":"overworld","blockingErrors":[],"warnings":[],"validatedAtMillis":1,
                     "compatFindings":[{"registry":"NOT_A_REGISTRY","key":"minecraft:sulfur","action":"EXCLUDED"}]}
                  ]
                }
                """, StandardCharsets.UTF_8);

        assertTrue(PackValidationCache.load(cache, "content", "context", List.of("overworld")).isEmpty());
    }

    @Test
    public void corruptCacheIsIgnored() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("corrupt"), "validation.json").toPath();
        Files.writeString(cache, "not-json", StandardCharsets.UTF_8);

        Optional<List<PackValidationResult>> loaded = PackValidationCache.load(
                cache, "content", "context", List.of());

        assertTrue(loaded.isEmpty());
    }

    @Test
    public void duplicateResultsAreRejected() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("duplicate"), "validation.json").toPath();
        Files.writeString(cache, """
                {
                  "schemaVersion": 3,
                  "contentFingerprint": "content",
                  "contextFingerprint": "context",
                  "results": [
                    {"packName":"overworld","blockingErrors":[],"warnings":[],"validatedAtMillis":1},
                    {"packName":"overworld","blockingErrors":[],"warnings":[],"validatedAtMillis":2}
                  ]
                }
                """, StandardCharsets.UTF_8);

        assertTrue(PackValidationCache.load(
                cache, "content", "context", List.of("overworld")).isEmpty());
    }

    @Test
    public void previousValidationSchemaIsRejected() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("previous-schema"), "validation.json").toPath();
        Files.writeString(cache, """
                {
                  "schemaVersion": 1,
                  "contentFingerprint": "content",
                  "contextFingerprint": "context",
                  "results": [
                    {"packName":"overworld","blockingErrors":[],"warnings":[],"validatedAtMillis":1}
                  ]
                }
                """, StandardCharsets.UTF_8);

        assertTrue(PackValidationCache.load(
                cache, "content", "context", List.of("overworld")).isEmpty());
    }

    @Test
    public void symbolicLinkCacheIsRejected() throws Exception {
        Path directory = temporaryFolder.newFolder("symbolic-cache").toPath();
        Path target = directory.resolve("target.json");
        Files.writeString(target, "{}", StandardCharsets.UTF_8);
        Path link = directory.resolve("validation.json");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (Exception unavailable) {
            Assume.assumeNoException(unavailable);
        }

        assertTrue(PackValidationCache.load(
                link, "content", "context", List.of()).isEmpty());
    }

    @Test
    public void contentFingerprintChangesWhenSizeAndTimestampAreRestored() throws Exception {
        File packsRoot = temporaryFolder.newFolder("content-fingerprint");
        File pack = new File(packsRoot, "overworld");
        assertTrue(pack.mkdirs());
        Path dimension = new File(pack, "dimensions/overworld.json").toPath();
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "alpha", StandardCharsets.UTF_8);
        FileTime originalTime = Files.getLastModifiedTime(dimension);
        String before = PackValidationCache.contentFingerprint(packsRoot);

        Files.writeString(dimension, "bravo", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(dimension, originalTime);
        String after = PackValidationCache.contentFingerprint(packsRoot);

        assertNotEquals(before, after);
    }

    @Test
    public void cachedFailureRemainsFailClosedWhenPublished() throws Exception {
        Path cache = new File(temporaryFolder.newFolder("failed-result"), "validation.json").toPath();
        PackValidationCache.save(cache, "content", "context", List.of(
                new PackValidationResult("overworld", List.of("missing structure"), List.of(), 1L)));
        PackValidationResult loaded = PackValidationCache.load(
                cache, "content", "context", List.of("overworld")).orElseThrow().getFirst();
        PackValidationRegistry.clear();
        PackValidationRegistry.publish(loaded);
        try {
            PackValidationRegistry.requireLoadable("overworld");
            fail("Expected a persisted failed validation to remain blocking");
        } catch (BrokenPackException expected) {
            assertEquals(List.of("missing structure"), expected.getReasons());
        } finally {
            PackValidationRegistry.clear();
        }
    }

    @Test
    public void externalBlockAvailabilityChangesValidationContext() {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(platform.structureHooks()).thenReturn(mock(PlatformStructureHooks.class));
        when(registries.blockKeys()).thenReturn(List.of("minecraft:stone"));
        when(registries.blockTypeKeys()).thenReturn(List.of("minecraft:stone"));
        IrisPlatforms.bind(platform);
        try {
            String before = PackValidationCache.contextFingerprint();
            when(registries.blockTypeKeys()).thenReturn(List.of("minecraft:stone", "oraxen:oraxen/caveblock"));

            assertNotEquals(before, PackValidationCache.contextFingerprint());
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }
}
