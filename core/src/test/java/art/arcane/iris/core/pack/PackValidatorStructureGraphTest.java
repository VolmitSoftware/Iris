package art.arcane.iris.core.pack;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformStructureHooks.JigsawSourceMetadata;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PackValidatorStructureGraphTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsCompleteStructureGraph() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"castle\"]}]}");
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"castle/start\"}],\"fallback\":\"castle/end\"}");
        write(pack, "jigsaw-pools/castle/end.json", "{\"pieces\":[]}");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[{\"pool\":\"castle/end\"}]}");
        write(pack, "objects/castle/start.iob", "object");

        assertTrue(PackObjectSurfaceValidator.validateStructureGraph(pack).isEmpty());
    }

    @Test
    public void collectsOnlyStructuresReferencedByRuntimePlacements() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"active\"]},"
                + "{\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]}");
        write(pack, "regions/forest.json", "{\"structures\":[{\"structures\":[\"regional\"]}]}");
        write(pack, "structures/active.json", "{}");
        write(pack, "structures/regional.json", "{}");
        write(pack, "structures/library.json", "{}");

        assertEquals(Set.of("active", "regional"), PackObjectSurfaceValidator.collectPlacedStructureKeys(pack));
    }

    @Test
    public void acceptsNativeStructureBackendWithoutConvertedIrisResources() throws Exception {
        File pack = temporaryFolder.newFolder("native-structure");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\",\"weight\":2,"
                + "\"jigsaw\":{\"maxDepth\":7,\"maxDistanceHorizontal\":96,"
                + "\"maxDistanceVertical\":64,\"liquidSettings\":\"IGNORE_WATERLOGGING\"}}],"
                + "\"underground\":true,\"minHeight\":-48,\"maxHeight\":-20,"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":24,"
                + "\"ceilingPadding\":12,\"floorPadding\":2,"
                + "\"lobeFrequency\":0.02,\"lobeStrength\":0.85}}]}");

        assertTrue(PackObjectSurfaceValidator.validateStructureGraph(pack).isEmpty());
        assertTrue(PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of(), Map.of()).isEmpty());
    }

    @Test
    public void datapackBootstrapDefersOnlyLiveRegistryValidation() throws Exception {
        File pack = temporaryFolder.newFolder("bootstrap-native-structure");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"test:structure\"}]}],"
                + "\"regions\":[\"main\"]}");
        write(pack, "regions/main.json", "{\"landBiomes\":[\"main\"]}");
        write(pack, "biomes/main.json", "{\"name\":\"Main\"}");
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenThrow(
                new IllegalStateException("server unavailable"));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            PackValidationResult bootstrap = PackValidator.validateForDatapackBootstrap(pack);
            PackValidationResult live = PackValidator.validate(pack);

            assertTrue(bootstrap.getBlockingErrors().toString(), bootstrap.isLoadable());
            assertFalse(live.isLoadable());
            assertTrue(live.getBlockingErrors().toString(), live.getBlockingErrors().stream().anyMatch(
                    error -> error.contains("server unavailable")));
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void datapackBootstrapStillRejectsMalformedPlacementConfiguration() throws Exception {
        File pack = temporaryFolder.newFolder("bootstrap-invalid-placement");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"test:structure\"}],"
                + "\"spacing\":0}]}");

        PackValidationResult result = PackValidator.validateForDatapackBootstrap(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(
                error -> error.contains("spacing must be at least 1")));
    }

    @Test
    public void rejectsNativeTerrainLobeSettingsOutsideTheirRange() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-lobe");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"lobeFrequency\":1.5,"
                + "\"lobeStrength\":-0.2}}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("terrain.lobeFrequency must be at most 1")));
        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("terrain.lobeStrength must be at least 0")));
    }

    @Test
    public void rejectsNativeTerrainErosionSettingsOutsideTheirRange() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-erosion");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"erosionStrength\":1.4,"
                + "\"erosionFrequency\":0}}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("terrain.erosionStrength must be at most 1")));
        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("terrain.erosionFrequency must be at least 0.001")));
    }

    @Test
    public void rejectsNonNumericNativeTerrainLobeSettings() throws Exception {
        File pack = temporaryFolder.newFolder("non-numeric-lobe");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"lobeStrength\":\"strong\"}}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("terrain.lobeStrength must be a number")));
    }

    @Test
    public void rejectsMixedStructureBackends() throws Exception {
        File pack = temporaryFolder.newFolder("mixed-native");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"structures\":[\"city\"],"
                + "\"nativeStructures\":[{\"structure\":\"ancient_city\",\"weight\":0,"
                + "\"jigsaw\":{\"maxDepth\":21}}]}]}");
        write(pack, "structures/city.json", "{}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("exactly one non-empty backend")));
    }

    @Test
    public void rejectsInvalidNativeStructureSourceAndOverrides() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-native");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"ancient_city\",\"weight\":0,"
                + "\"jigsaw\":{\"maxDepth\":21}}]}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains(".structure must be a namespaced registry key")));
        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains(".weight must be at least 1")));
        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains(".maxDepth must be at most 20")));
    }

    @Test
    public void rejectsMalformedPlacementGridAndPolicyFields() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-placement-grid");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"placementId\":42,\"distribution\":\"UNKNOWN\",\"spacing\":0,"
                + "\"separation\":32,\"density\":\"often\",\"ringCount\":0,"
                + "\"minHeight\":90,\"maxHeight\":20,\"underwater\":\"yes\","
                + "\"nativeSuppression\":\"SOMETIMES\","
                + "\"terrain\":{\"mode\":\"SURFACE_FIT\",\"shape\":\"CUBE\"}}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("placementId must be a string")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("distribution must be one of")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("spacing must be at least 1")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("density must be a number")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("inverted height band")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("underwater must be a boolean")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("terrain.mode must be one of")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("terrain.shape must be one of")));
    }

    @Test
    public void rejectsDuplicatePlacementIdsAcrossResources() throws Exception {
        File pack = temporaryFolder.newFolder("duplicate-placement-id");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"placementId\":\"shared\","
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]}");
        write(pack, "regions/forest.json", "{\"structures\":[{\"placementId\":\"shared\","
                + "\"nativeStructures\":[{\"structure\":\"minecraft:village_plains\"}]}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("placementId duplicates 'shared'")));
    }

    @Test
    public void rejectsCanonicalAnonymousGridDuplicatesAcrossResources() throws Exception {
        File pack = temporaryFolder.newFolder("duplicate-anonymous-grid");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]}");
        write(pack, "regions/forest.json", "{\"structures\":[{"
                + "\"spacing\":32,\"separation\":8,\"density\":0.02,"
                + "\"nativeStructures\":[{\"weight\":1,\"structure\":\"minecraft:ancient_city\"}]}]}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("duplicates an anonymous placement grid")));
    }

    @Test
    public void rejectsDuplicateEditableStructureKeysIgnoringCase() throws Exception {
        File pack = temporaryFolder.newFolder("duplicate-editable-key");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":["
                + "\"castle\",\"CASTLE\"]}]}");
        write(pack, "structures/castle.json", "{}");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("duplicates Iris structure")));
    }

    @Test
    public void optionalJigsawTerrainEnvelopeOverflowDoesNotBlockThePack() throws Exception {
        File pack = temporaryFolder.newFolder("oversized-envelope");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\","
                + "\"jigsaw\":{\"maxDistanceHorizontal\":96}}],"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":40}}]}");

        assertTrue(PackObjectSurfaceValidator.validateStructureGraph(pack).isEmpty());
    }

    @Test
    public void sharedOverworldAncientCityTerrainEnvelopeDoesNotBlockThePack() throws Exception {
        File pack = nativeSourceEnvelopePack(
                "shared-overworld-ancient-city", "minecraft:ancient_city", "FORCE_CARVE", 24);

        assertTrue(validateWithJigsawMetadata(
                pack, "minecraft:ancient_city", 116, 12, 0, 0).isEmpty());
    }

    @Test
    public void sourceAndPreserveIgnoreConfiguredEnvelopePadding() throws Exception {
        File source = nativeEnvelopePack("source-padding", "SOURCE", 128, 128);
        File preserve = nativeEnvelopePack("preserve-padding", "PRESERVE", 128, 128);

        assertTrue(validateWithReferenceExpansion(source, 0).isEmpty());
        assertTrue(validateWithReferenceExpansion(preserve, 12).isEmpty());
    }

    @Test
    public void sourceTerrainAdjustmentOverflowDoesNotBlockThePack() throws Exception {
        File boundary = nativeEnvelopePack("source-adjustment-boundary", "SOURCE", 116, 64);
        File oversized = nativeEnvelopePack("source-adjustment-oversized", "SOURCE", 117, 0);

        assertTrue(validateWithReferenceExpansion(boundary, 12).isEmpty());
        assertTrue(validateWithReferenceExpansion(oversized, 12).isEmpty());
    }

    @Test
    public void customTerrainEnvelopeOverflowDoesNotBlockThePack() throws Exception {
        File boundary = nativeEnvelopePack("custom-envelope-boundary", "ENCASE", 96, 32);
        File oversized = nativeEnvelopePack("custom-envelope-oversized", "ENCASE", 96, 33);

        assertTrue(validateWithReferenceExpansion(boundary, 12).isEmpty());
        assertTrue(validateWithReferenceExpansion(oversized, 12).isEmpty());
    }

    @Test
    public void registeredJigsawOptionalPaddingDoesNotExpandItsBlockingSpan() throws Exception {
        File boundary = nativeSourceEnvelopePack("dnt-source-boundary", "ENCASE", 48);
        File oversized = nativeSourceEnvelopePack("dnt-source-oversized", "ENCASE", 49);

        assertTrue(validateWithJigsawMetadata(boundary, 80, 12).isEmpty());
        assertTrue(validateWithJigsawMetadata(oversized, 80, 12).isEmpty());
    }

    @Test
    public void jigsawOverrideReplacesLiveSourceDistance() throws Exception {
        File pack = nativeEnvelopePack("source-distance-override", "ENCASE", 80, 36);

        assertTrue(validateWithJigsawMetadata(pack, 120, 12).isEmpty());
    }

    @Test
    public void oversizedLiveStartElementCannotBypassAssemblyDistance() throws Exception {
        File pack = nativeSourceEnvelopePack("oversized-live-start", "SOURCE", 0);

        List<String> errors = validateWithJigsawMetadata(pack, 80, 0, 129, 0);

        assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                message.contains("129-block maximum start element")
                        && message.contains("128-block (8-chunk)")));
    }

    @Test
    public void liveStartElementSpanUsesInclusiveReferenceBoundary() throws Exception {
        File pack = nativeSourceEnvelopePack("live-start-boundary", "SOURCE", 0);

        assertTrue(validateWithJigsawMetadata(pack, 80, 12, 116, 0).isEmpty());
    }

    @Test
    public void overriddenStartPoolUsesItsLiveSpan() throws Exception {
        File pack = nativePoolOverrideEnvelopePack("oversized-pool-override");

        List<String> errors = validateWithJigsawMetadata(pack, 80, 0, 20, 129);

        assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                message.contains("129-block maximum start element")
                        && message.contains("128-block (8-chunk)")));
    }

    @Test
    public void smallerOverriddenStartPoolReplacesTheSourcePoolSpan() throws Exception {
        File pack = nativePoolOverrideEnvelopePack("smaller-pool-override");

        assertTrue(validateWithJigsawMetadata(pack, 80, 0, 129, 20).isEmpty());
    }

    @Test
    public void unresolvedOverriddenStartPoolSpanFailsClosed() throws Exception {
        File pack = nativePoolOverrideEnvelopePack("unresolved-pool-override");
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start", "test:override"));
        when(hooks.jigsawSourceMetadata("test:structure"))
                .thenReturn(new JigsawSourceMetadata(80, 0, 20));
        when(hooks.jigsawStartPoolHorizontalSpan("test:structure", "test:override"))
                .thenThrow(new IllegalStateException("missing template"));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

            assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                    message.contains("could not resolve a bounded live horizontal span")
                            && message.contains("missing template")));
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void registeredJigsawSourceAdjustmentOverflowDoesNotBlockThePack() throws Exception {
        File pack = nativeSourceEnvelopePack("source-adjustment-no-override", "SOURCE", 0);

        assertTrue(validateWithJigsawMetadata(pack, 117, 12).isEmpty());
    }

    @Test
    public void registeredJigsawAssemblyBeyondReferenceRangeStillBlocksThePack() throws Exception {
        File pack = nativeEnvelopePack("oversized-live-assembly", "ENCASE", 129, 0);

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                message.contains(".jigsaw.maxDistanceHorizontal")
                        && message.contains("129-block maximum assembly distance")
                        && message.contains("128-block (8-chunk)")));
    }

    @Test
    public void liveJigsawMetadataFailureBlocksValidation() throws Exception {
        File pack = nativeSourceEnvelopePack("missing-source-metadata", "SOURCE", 0);

        List<String> errors = validateWithUnavailableJigsawMetadata(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                message.contains("returned null jigsaw metadata for 'test:structure'")));
    }

    @Test
    public void validationResolvesOnlyReferencedJigsawMetadataAndCachesDuplicates() throws Exception {
        File pack = temporaryFolder.newFolder("lazy-jigsaw-metadata");
        write(pack, "dimensions/main.json", "{\"structures\":["
                + "{\"placementId\":\"first\",\"nativeStructures\":["
                + "{\"structure\":\"test:used\"}]},"
                + "{\"placementId\":\"second\",\"nativeStructures\":["
                + "{\"structure\":\"test:used\"}]}]}");
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("test:used", "test:unused"));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of("test:used", "test:unused"));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start"));
        when(hooks.jigsawSourceMetadata("test:used"))
                .thenReturn(new JigsawSourceMetadata(80, 0, 20));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

            assertTrue(errors.toString(), errors.isEmpty());
            verify(hooks, times(1)).jigsawSourceMetadata("test:used");
            verify(hooks, never()).jigsawSourceMetadata("test:unused");
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void optionalTerrainPaddingForNonJigsawSourceDoesNotBlockThePack() throws Exception {
        File pack = nativeSourceEnvelopePack("non-jigsaw-envelope", "ENCASE", 1);

        assertTrue(validateWithNonJigsawSource(pack).isEmpty());
    }

    @Test
    public void acceptsZeroReferencePaddingForRegisteredNonJigsawSource() throws Exception {
        File pack = nativeSourceEnvelopePack("non-jigsaw-zero-envelope", "ENCASE", 0);

        assertTrue(validateWithNonJigsawSource(pack).isEmpty());
    }

    @Test
    public void optionalTerrainPaddingForNaturalNonJigsawAdjustmentDoesNotBlockThePack() throws Exception {
        File pack = nativeAdjustmentEnvelopePack(
                "natural-non-jigsaw-envelope", "test:structure", "ENCASE", 1);

        assertTrue(validateWithNonJigsawSource(pack).isEmpty());
    }

    @Test
    public void optionalTerrainPaddingForNaturalJigsawAdjustmentDoesNotBlockThePack() throws Exception {
        File boundary = nativeAdjustmentEnvelopePack(
                "natural-jigsaw-envelope-boundary", "test:structure", "ENCASE", 48);
        File oversized = nativeAdjustmentEnvelopePack(
                "natural-jigsaw-envelope-oversized", "test:structure", "ENCASE", 49);

        assertTrue(validateWithJigsawMetadata(boundary, 80, 12).isEmpty());
        assertTrue(validateWithJigsawMetadata(oversized, 80, 12).isEmpty());
    }

    @Test
    public void actualNaturalJigsawContentBeyondReferenceRangeStillBlocksThePack() throws Exception {
        File pack = nativeAdjustmentEnvelopePack(
                "natural-jigsaw-oversized-content", "test:structure", "ENCASE", 1);

        List<String> errors = validateWithJigsawMetadata(pack, 80, 0, 129, 0);

        assertTrue(errors.toString(), errors.stream().anyMatch(message ->
                message.contains("importedStructures.adjustments[0]")
                        && message.contains("129-block maximum start element")
                        && message.contains("128-block (8-chunk)")));
    }

    @Test
    public void lastMatchingNaturalTerrainAdjustmentControlsEnvelopeValidation() throws Exception {
        File pack = temporaryFolder.newFolder("natural-adjustment-precedence");
        write(pack, "dimensions/main.json", "{\"importedStructures\":{\"adjustments\":["
                + "{\"match\":[\"test:structure\"],\"terrain\":{\"mode\":\"ENCASE\","
                + "\"horizontalPadding\":128}},"
                + "{\"match\":[\"test:structure\"],\"terrain\":{\"mode\":\"PRESERVE\"}}]}}}");

        assertTrue(validateWithNonJigsawSource(pack).isEmpty());
    }

    @Test
    public void reportsMissingReferencesInDeterministicGraphOrder() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"missing-structure\"]}]}");
        write(pack, "structures/castle.json", "{\"startPool\":\"missing-start\"}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"missing-piece\"}],\"fallback\":\"missing-fallback\"}");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[{\"pool\":\"missing-connector-pool\"}]}");
        write(pack, "objects/castle/start.iob", "object");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertEquals(List.of(
                "Dimension 'main' structures[0].structures[0] references missing structure 'missing-structure'.",
                "Structure 'castle' references missing start pool 'missing-start'.",
                "Jigsaw pool 'castle/start' pieces[0] references missing piece 'missing-piece'.",
                "Jigsaw pool 'castle/start' references missing fallback pool 'missing-fallback'.",
                "Jigsaw piece 'castle/start' connectors[0] references missing pool 'missing-connector-pool'."
        ), errors);
    }

    @Test
    public void ignoresLegacyGeneratedStructureIndex() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/structure-index.json", "{\"counts\":{},\"structureSets\":{},\"iris\":[]}");

        assertTrue(PackObjectSurfaceValidator.validateStructureGraph(pack).isEmpty());
    }

    @Test
    public void reportsMalformedStructureJson() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/castle.json", "{");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Structure 'castle' has invalid JSON:"));
    }

    @Test
    public void reportsMalformedJigsawPoolJson() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "jigsaw-pools/castle/start.json", "{");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Jigsaw pool 'castle/start' has invalid JSON:"));
    }

    @Test
    public void reportsMalformedJigsawPieceJson() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "jigsaw-pieces/castle/start.json", "{");

        List<String> errors = PackObjectSurfaceValidator.validateStructureGraph(pack);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Jigsaw piece 'castle/start' has invalid JSON:"));
    }

    @Test
    public void reportsMissingJigsawPieceObject() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/missing\"}");

        assertEquals(List.of(
                "Jigsaw piece 'castle/start' references missing object 'castle/missing'."
        ), PackObjectSurfaceValidator.validateStructureGraph(pack));
    }

    @Test
    public void acceptsViableDimensionLevelNativeReplacement() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\"}");

        assertTrue(PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, 0, 0)).isEmpty());
    }

    @Test
    public void acceptsDimensionLevelNativeStructureReplacementWithoutConversionGraph() throws Exception {
        File pack = temporaryFolder.newFolder("native-replacement");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");

        assertTrue(PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of(), Map.of()).isEmpty());
    }

    @Test
    public void rejectsNonViableNativeReplacementWithoutFallingBack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\"}");

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(pack, Set.of(), Map.of());

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("not runtime-viable")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("will not be used as a fallback")));
    }

    @Test
    public void rejectsReplacementWithoutValidVanillaSource() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"\"}");

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -1, 1));

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("vanillaSource is not a valid namespaced registry key")));
    }

    @Test
    public void rejectsReplacementOutsideDimensionPlacement() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "regions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\"}");

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -1, 1));

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("only valid on dimension-level placements")));
    }

    @Test
    public void rejectsUndergroundReplacementBelowDimensionWritableRange() throws Exception {
        File pack = replacementPack("below", "STRUCTURE_PIECE", true, -80, -80);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("sampled seed 0")
                && message.contains("piece envelope -5..4")
                && message.contains("placement band -80..-80")
                && message.contains("writable world -63..319")
                && message.contains("will not be used as a fallback")));
    }

    @Test
    public void rejectsUndergroundReplacementAboveDimensionWritableRange() throws Exception {
        File pack = replacementPack("above", "STRUCTURE_PIECE", true, 318, 318);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("sampled seed 0")
                && message.contains("piece envelope -5..4")
                && message.contains("placement band 318..318")
                && message.contains("writable world -63..319")));
    }

    @Test
    public void surfaceAlignsMultiPieceEnvelopeBeforeCheckingWorldBounds() throws Exception {
        File pack = replacementPack("surface-aligned", "CENTER_HEIGHT", false, 290, 290);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 2, -30, 10));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("surface-aligned")
                && message.contains("piece envelope 0..40")
                && message.contains("placement band 290..290")));
    }

    @Test
    public void doesNotApplyExactYEnvelopeGateToSingleSurfacePiece() throws Exception {
        File pack = replacementPack("surface-single", "CENTER_HEIGHT", false, -63, 319);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -500, 500));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void validatesEverySurfaceExactYAnchorAgainstWritableWorldBounds() throws Exception {
        File pack = replacementPack("surface-exact-range", "STRUCTURE_PIECE", false, -63, 319);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("sampled seed 0")
                && message.contains("piece envelope -5..4")
                && message.contains("placement band -63..319")
                && message.contains("writable world -63..319")));
    }

    @Test
    public void acceptsSurfaceExactYWhenEveryConfiguredAnchorFits() throws Exception {
        File pack = replacementPack("surface-exact-safe", "STRUCTURE_PIECE", false, -58, 315);

        List<String> errors = PackNativeStructureValidator.validateNativeStructureReplacements(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void fullPackValidationRejectsReplacementOutsideDimensionEnvelope() throws Exception {
        File pack = replacementPack("full-pack-below", "STRUCTURE_PIECE", true, -80, -80);
        write(pack, "dimensions/main.json", "{\"dimensionHeight\":{\"min\":-64,\"max\":320},"
                + "\"regions\":[\"region\"],\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\",\"underground\":true,"
                + "\"minHeight\":-80,\"maxHeight\":-80}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack, "structures/city.json", "{\"startPool\":\"city/start\","
                + "\"vanillaSource\":\"minecraft:ancient_city\",\"placeMode\":\"STRUCTURE_PIECE\"}");
        write(pack, "jigsaw-pools/city/start.json", "{\"pieces\":[{\"piece\":\"city/start\"}]}");
        write(pack, "jigsaw-pieces/city/start.json", "{\"object\":\"city/start\",\"connectors\":[]}");
        writeObjectHeader(pack, "objects/city/start.iob", 3, 9, 3);

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(
                message -> message.contains("REPLACE_SOURCE structure 'city'")
                        && message.contains("cannot fit placement band -80..-80")
                        && message.contains("will not be used as a fallback")));
    }

    @Test
    public void fullPackValidationPreservesMalformedActiveObjectPathAndReason() throws Exception {
        File pack = temporaryFolder.newFolder("malformed-active-object");
        write(pack, "dimensions/main.json", "{\"dimensionHeight\":{\"min\":-64,\"max\":320},"
                + "\"regions\":[\"region\"],\"structures\":[{\"structures\":[\"castle\"]}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        Path object = pack.toPath().resolve("objects/castle/start.iob");
        Files.createDirectories(object.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(object))) {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
        }

        PackValidationResult result = PackValidator.validate(pack);
        String expected = "Malformed Iris object resource " + object + ": EOFException";

        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().contains(expected));
        assertFalse(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(
                message -> message.contains("references missing object 'castle/start'")));
    }

    private File replacementPack(
            String name,
            String placeMode,
            boolean underground,
            int minimumY,
            int maximumY
    ) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"dimensionHeight\":{\"min\":-64,\"max\":320},"
                + "\"structures\":[{\"structures\":[\"city\"],\"nativeSuppression\":\"REPLACE_SOURCE\","
                + "\"underground\":" + underground + ",\"minHeight\":" + minimumY
                + ",\"maxHeight\":" + maximumY + "}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\","
                + "\"placeMode\":\"" + placeMode + "\"}");
        return pack;
    }

    private File nativeEnvelopePack(String name, String mode, int maximumDistance,
                                    int horizontalPadding) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"test:structure\","
                + "\"jigsaw\":{\"maxDistanceHorizontal\":" + maximumDistance + "}}],"
                + "\"terrain\":{\"mode\":\"" + mode + "\",\"horizontalPadding\":"
                + horizontalPadding + "}}]}");
        return pack;
    }

    private File nativeSourceEnvelopePack(String name, String mode,
                                          int horizontalPadding) throws Exception {
        return nativeSourceEnvelopePack(name, "test:structure", mode, horizontalPadding);
    }

    private File nativeSourceEnvelopePack(String name, String structureKey, String mode,
                                          int horizontalPadding) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"" + structureKey + "\"}],"
                + "\"terrain\":{\"mode\":\"" + mode + "\",\"horizontalPadding\":"
                + horizontalPadding + "}}]}");
        return pack;
    }

    private File nativePoolOverrideEnvelopePack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"test:structure\","
                + "\"jigsaw\":{\"startPool\":\"test:override\","
                + "\"maxDistanceHorizontal\":80}}],"
                + "\"terrain\":{\"mode\":\"SOURCE\"}}]}");
        return pack;
    }

    private File nativeAdjustmentEnvelopePack(String name, String match, String mode,
                                              int horizontalPadding) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"importedStructures\":{\"adjustments\":[{"
                + "\"match\":[\"" + match + "\"],\"terrain\":{\"mode\":\"" + mode
                + "\",\"horizontalPadding\":" + horizontalPadding + "}}]}}}");
        return pack;
    }

    private List<String> validateWithReferenceExpansion(File pack, int expansion) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start"));
        when(hooks.jigsawSourceMetadata("test:structure"))
                .thenReturn(new JigsawSourceMetadata(80, expansion));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            return PackObjectSurfaceValidator.validateStructureGraph(pack);
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    private List<String> validateWithJigsawMetadata(File pack, int maximumDistance, int expansion) {
        return validateWithJigsawMetadata(pack, maximumDistance, expansion, 0, 0);
    }

    private List<String> validateWithJigsawMetadata(File pack, int maximumDistance, int expansion,
                                                    int sourceSpan, int overrideSpan) {
        return validateWithJigsawMetadata(
                pack, "test:structure", maximumDistance, expansion, sourceSpan, overrideSpan);
    }

    private List<String> validateWithJigsawMetadata(
            File pack,
            String structureKey,
            int maximumDistance,
            int expansion,
            int sourceSpan,
            int overrideSpan
    ) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of(structureKey));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of(structureKey));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start", "test:override"));
        when(hooks.jigsawSourceMetadata(structureKey))
                .thenReturn(new JigsawSourceMetadata(maximumDistance, expansion, sourceSpan));
        when(hooks.jigsawStartPoolHorizontalSpan(
                structureKey, "test:override")).thenReturn(overrideSpan);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            return PackObjectSurfaceValidator.validateStructureGraph(pack);
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    private List<String> validateWithUnavailableJigsawMetadata(File pack) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of("test:structure"));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start"));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            return PackObjectSurfaceValidator.validateStructureGraph(pack);
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    private List<String> validateWithNonJigsawSource(File pack) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("test:structure", "test:jigsaw"));
        when(hooks.jigsawStructureKeys()).thenReturn(List.of("test:jigsaw"));
        when(hooks.templatePoolKeys()).thenReturn(List.of("test:start"));
        when(hooks.jigsawSourceMetadata("test:jigsaw"))
                .thenReturn(new JigsawSourceMetadata(80, 0));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            return PackObjectSurfaceValidator.validateStructureGraph(pack);
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    private Map<String, List<StructureGraphPackValidator.SampledVerticalEnvelope>> sampledEnvelope(
            String structureKey,
            int pieceCount,
            int minimumYOffset,
            int maximumYOffset
    ) {
        return Map.of(structureKey, List.of(
                new StructureGraphPackValidator.SampledVerticalEnvelope(
                        0L, pieceCount, minimumYOffset, maximumYOffset)));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void writeObjectHeader(
            File root,
            String relative,
            int width,
            int height,
            int depth
    ) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(0);
            output.writeInt(0);
            output.writeInt(0);
        }
    }
}
