package art.arcane.iris.core.pack;

import art.arcane.iris.engine.framework.structure.NativeReplacementValidation;
import art.arcane.iris.engine.framework.structure.StructureGraphValidation;
import art.arcane.iris.engine.framework.structure.StructureValidator;
import com.volmit.validation.Problem;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
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
import static org.mockito.Mockito.when;

public class PackValidatorStructureGraphTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsCompleteStructureGraph() throws Exception {
        File pack = pack("\"structures\":[{\"structures\":[\"castle\"]}]");
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[]}");
        writeObjectHeader(pack, "objects/castle/start.iob", 3, 9, 3);

        List<String> errors = blockingErrors(pack);

        assertTrue(errors.toString(), errors.isEmpty());
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

        assertEquals(Set.of("active", "regional"), StructureValidator.placedStructureKeys(pack));
    }

    @Test
    public void acceptsNativeStructureBackendWithoutConvertedIrisResources() throws Exception {
        File pack = pack("\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\",\"weight\":2,"
                + "\"jigsaw\":{\"maxDepth\":7,\"maxDistanceHorizontal\":96,"
                + "\"maxDistanceVertical\":64,\"liquidSettings\":\"IGNORE_WATERLOGGING\"}}],"
                + "\"underground\":true,\"minHeight\":-48,\"maxHeight\":-20,"
                + "\"terrain\":{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":24,"
                + "\"ceilingPadding\":12,\"floorPadding\":2,"
                + "\"lobeFrequency\":0.02,\"lobeStrength\":0.85}}]");

        List<String> errors = blockingErrors(pack);

        assertTrue(errors.toString(), errors.isEmpty());
        assertTrue(nativeReplacementErrors(pack, Set.of(), Map.of()).isEmpty());
    }

    @Test
    public void datapackBootstrapDefersOnlyLiveRegistryValidation() throws Exception {
        File pack = pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"test:structure\"}]}]");
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.structureKeys()).thenThrow(new IllegalStateException("server unavailable"));
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
    public void liveValidationRejectsAnUnregisteredNativeStructure() throws Exception {
        File pack = pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"test:missing\"}]}]");
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.structureKeys()).thenReturn(List.of("test:structure"));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            assertEquals(List.of("pack:dimension/main: structures[0].nativeStructures[0].structure 'test:missing'"
                    + " is not a registered structure"), PackValidator.validate(pack).getBlockingErrors());
            assertTrue(PackValidator.validateForDatapackBootstrap(pack).isLoadable());
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void datapackBootstrapStillRejectsMalformedPlacementConfiguration() throws Exception {
        File pack = pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"test:structure\"}],\"spacing\":0}]");

        PackValidationResult result = PackValidator.validateForDatapackBootstrap(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(
                error -> error.contains("structures[0].spacing is 0; it must be at least 1")));
    }

    @Test
    public void rejectsANativeTerrainLobeFrequencyAboveItsRange() throws Exception {
        assertBlockedNaming("structures[0].terrain.lobeFrequency is 1.5; it must be at most 1",
                nativePlacement("\"terrain\":{\"mode\":\"FORCE_CARVE\",\"lobeFrequency\":1.5}"));
    }

    @Test
    public void rejectsANativeTerrainLobeStrengthBelowItsRange() throws Exception {
        assertBlockedNaming("structures[0].terrain.lobeStrength is -0.2; it must be at least 0",
                nativePlacement("\"terrain\":{\"mode\":\"FORCE_CARVE\",\"lobeStrength\":-0.2}"));
    }

    @Test
    public void rejectsANativeTerrainErosionStrengthAboveItsRange() throws Exception {
        assertBlockedNaming("structures[0].terrain.erosionStrength is 1.4; it must be at most 1",
                nativePlacement("\"terrain\":{\"mode\":\"FORCE_CARVE\",\"erosionStrength\":1.4}"));
    }

    @Test
    public void rejectsANativeTerrainErosionFrequencyBelowItsRange() throws Exception {
        assertBlockedNaming("structures[0].terrain.erosionFrequency is 0",
                nativePlacement("\"terrain\":{\"mode\":\"FORCE_CARVE\",\"erosionFrequency\":0}"));
    }

    @Test
    public void rejectsNonNumericNativeTerrainLobeSettings() throws Exception {
        assertBlockedNaming("in Dimension main: structures[0].terrain.lobeStrength must be a number",
                nativePlacement("\"terrain\":{\"mode\":\"FORCE_CARVE\",\"lobeStrength\":\"strong\"}"));
    }

    @Test
    public void rejectsMixedStructureBackends() throws Exception {
        File pack = pack("\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]");
        write(pack, "structures/city.json", "{}");

        assertTrue(blockingErrors(pack).contains("pack:dimension/main: structures[0] must declare exactly one non-empty"
                + " backend: structures or nativeStructures"));
    }

    @Test
    public void rejectsANativeStructureWithoutNamespace() throws Exception {
        assertEquals(List.of("pack:dimension/main: structures[0].nativeStructures[0].structure must be a namespaced registry key"),
                blockingErrors(pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"ancient_city\"}]}]")));
    }

    @Test
    public void rejectsANativeSourceWeightBelowItsRange() throws Exception {
        assertBlockedNaming("structures[0].nativeStructures[0].weight is 0; it must be at least 1",
                pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\",\"weight\":0}]}]"));
    }

    @Test
    public void rejectsANativeJigsawDepthAboveItsRange() throws Exception {
        assertBlockedNaming("structures[0].nativeStructures[0].jigsaw.maxDepth is 21; it must be at most 20",
                pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\","
                        + "\"jigsaw\":{\"maxDepth\":21}}]}]"));
    }

    @Test
    public void rejectsAnUnknownDistribution() throws Exception {
        assertBlockedNaming("\"UNKNOWN\" is not one of", nativePlacement("\"distribution\":\"UNKNOWN\""));
    }

    @Test
    public void rejectsAnInvertedHeightBandAndSeparationNotBelowSpacing() throws Exception {
        List<String> errors = blockingErrors(nativePlacement("\"spacing\":16,\"separation\":16,\"minHeight\":90,\"maxHeight\":20"));

        assertEquals(List.of(
                "pack:dimension/main: structures[0].separation must be smaller than spacing",
                "pack:dimension/main: structures[0] has an inverted height band: minHeight must not exceed maxHeight"), errors);
    }

    @Test
    public void rejectsDuplicatePlacementIdsAcrossResources() throws Exception {
        File pack = pack("\"structures\":[{\"placementId\":\"shared\","
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]");
        write(pack, "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}],\"structures\":[{\"placementId\":\"shared\","
                + "\"nativeStructures\":[{\"structure\":\"minecraft:village_plains\"}]}]}");

        List<String> errors = blockingErrors(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("placementId duplicates 'shared'")));
    }

    @Test
    public void rejectsCanonicalAnonymousGridDuplicatesAcrossResources() throws Exception {
        File pack = pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}]}]");
        write(pack, "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}],\"structures\":[{"
                + "\"spacing\":32,\"separation\":8,\"density\":0.02,"
                + "\"nativeStructures\":[{\"weight\":1,\"structure\":\"minecraft:ancient_city\"}]}]}");

        List<String> errors = blockingErrors(pack);

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("duplicates an anonymous placement grid")));
    }

    @Test
    public void rejectsDuplicateEditableStructureKeysIgnoringCase() throws Exception {
        File pack = pack("\"structures\":[{\"structures\":[\"castle\",\"CASTLE\"]}]");
        write(pack, "structures/castle.json", "{}");

        List<String> errors = blockingErrors(pack);

        assertTrue(errors.toString(), errors.contains(
                "pack:dimension/main: structures[0].structures[1] duplicates Iris structure 'CASTLE'"));
    }

    @Test
    public void reportsMissingReferencesOfPlacedAndUnplacedResources() throws Exception {
        File pack = pack("\"structures\":[{\"structures\":[\"missing-structure\"]}]");
        write(pack, "structures/castle.json", "{\"startPool\":\"missing-start\"}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"missing-piece\"}],\"fallback\":\"missing-fallback\"}");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[{\"pool\":\"missing-connector-pool\"}]}");
        writeObjectHeader(pack, "objects/castle/start.iob", 3, 9, 3);

        assertEquals(List.of(
                "structures/castle.json: Structure 'castle' references missing start pool 'missing-start'.",
                "jigsaw-pools/castle/start.json: Jigsaw pool 'castle/start' pieces[0] references missing piece 'missing-piece'.",
                "jigsaw-pools/castle/start.json: Jigsaw pool 'castle/start' references missing fallback pool 'missing-fallback'.",
                "jigsaw-pieces/castle/start.json: Jigsaw piece 'castle/start' connectors[0] references missing pool"
                        + " 'missing-connector-pool'.",
                "pack:dimension/main: structures[0].structures[0] references missing structure 'missing-structure'"
        ), blockingErrors(pack));
    }

    @Test
    public void reportsMalformedStructureJson() throws Exception {
        assertSingleErrorStartsWith("structures/castle.json: Structure 'castle' cannot be loaded: ", "structures/castle.json");
    }

    @Test
    public void reportsMalformedJigsawPoolJson() throws Exception {
        assertSingleErrorStartsWith("jigsaw-pools/castle/start.json: Jigsaw pool 'castle/start' cannot be loaded: ",
                "jigsaw-pools/castle/start.json");
    }

    @Test
    public void reportsMalformedJigsawPieceJson() throws Exception {
        assertSingleErrorStartsWith("jigsaw-pieces/castle/start.json: Jigsaw piece 'castle/start' cannot be loaded: ",
                "jigsaw-pieces/castle/start.json");
    }

    @Test
    public void reportsMissingJigsawPieceObject() throws Exception {
        File pack = pack("");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/missing\"}");

        assertEquals(List.of(
                "jigsaw-pieces/castle/start.json: Jigsaw piece 'castle/start' references missing object 'castle/missing'."
        ), blockingErrors(pack));
    }

    @Test
    public void acceptsViableDimensionLevelNativeReplacement() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\"}");

        assertTrue(nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, 0, 0)).isEmpty());
    }

    @Test
    public void acceptsDimensionLevelNativeStructureReplacementWithoutConversionGraph() throws Exception {
        File pack = temporaryFolder.newFolder("native-replacement");
        write(pack, "dimensions/main.json", "{\"structures\":[{"
                + "\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");

        assertTrue(nativeReplacementErrors(
                pack, Set.of(), Map.of()).isEmpty());
    }

    @Test
    public void rejectsNonViableNativeReplacementWithoutFallingBack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"minecraft:ancient_city\"}");

        List<String> errors = nativeReplacementErrors(pack, Set.of(), Map.of());

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("not runtime-viable")));
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("will not be used as a fallback")));
    }

    @Test
    public void rejectsReplacementWithoutValidVanillaSource() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\"}]}");
        write(pack, "structures/city.json", "{\"vanillaSource\":\"\"}");

        List<String> errors = nativeReplacementErrors(
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

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, -1, 1));

        assertTrue(errors.toString(), errors.stream().anyMatch(
                message -> message.contains("only valid on dimension-level placements")));
    }

    @Test
    public void rejectsUndergroundReplacementBelowDimensionWritableRange() throws Exception {
        File pack = replacementPack("below", "STRUCTURE_PIECE", true, -80, -80);

        List<String> errors = nativeReplacementErrors(
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

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("sampled seed 0")
                && message.contains("piece envelope -5..4")
                && message.contains("placement band 318..318")
                && message.contains("writable world -63..319")));
    }

    @Test
    public void surfaceAlignsMultiPieceEnvelopeBeforeCheckingWorldBounds() throws Exception {
        File pack = replacementPack("surface-aligned", "CENTER_HEIGHT", false, 290, 290);

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 2, -30, 10));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("surface-aligned")
                && message.contains("piece envelope 0..40")
                && message.contains("placement band 290..290")));
    }

    @Test
    public void doesNotApplyExactYEnvelopeGateToSingleSurfacePiece() throws Exception {
        File pack = replacementPack("surface-single", "CENTER_HEIGHT", false, -63, 319);

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, -500, 500));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void validatesEverySurfaceExactYAnchorAgainstWritableWorldBounds() throws Exception {
        File pack = replacementPack("surface-exact-range", "STRUCTURE_PIECE", false, -63, 319);

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains("sampled seed 0")
                && message.contains("piece envelope -5..4")
                && message.contains("placement band -63..319")
                && message.contains("writable world -63..319")));
    }

    @Test
    public void acceptsSurfaceExactYWhenEveryConfiguredAnchorFits() throws Exception {
        File pack = replacementPack("surface-exact-safe", "STRUCTURE_PIECE", false, -58, 315);

        List<String> errors = nativeReplacementErrors(
                pack, Set.of("city"), sampledEnvelope("city", 1, -5, 4));

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void fullPackValidationRejectsReplacementOutsideDimensionEnvelope() throws Exception {
        File pack = replacementPack("full-pack-below", "STRUCTURE_PIECE", true, -80, -80);
        write(pack, "dimensions/main.json", "{\"dimensionHeight\":{\"min\":-64,\"max\":320},"
                + "\"regions\":[{\"region\":\"region\"}],\"structures\":[{\"structures\":[\"city\"],"
                + "\"nativeSuppression\":\"REPLACE_SOURCE\",\"underground\":true,"
                + "\"minHeight\":-80,\"maxHeight\":-80}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}]}");
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
                + "\"regions\":[{\"region\":\"region\"}],\"structures\":[{\"structures\":[\"castle\"]}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}]}");
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
        String expected = "structures/castle.json: Malformed Iris object resource " + object + ": EOFException";

        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().contains(expected));
        assertFalse(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(
                message -> message.contains("references missing object 'castle/start'")));
    }

    /**
     * A loadable pack named {@code pack}: one dimension declaring {@code dimensionMembers} besides its region, the
     * region and its biome.
     */
    private File pack(String dimensionMembers) throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        String members = dimensionMembers.isEmpty() ? "" : ", " + dimensionMembers;
        write(pack, "dimensions/main.json", "{\"regions\": [{\"region\": \"region\"}]" + members + "}");
        write(pack, "regions/region.json", "{\"landBiomes\": [{\"biome\": \"biome\"}]}");
        write(pack, "biomes/biome.json", "{\"name\": \"Biome\"}");
        return pack;
    }

    /** A pack whose dimension places a native ancient city with {@code placementMembers} added to the placement. */
    private File nativePlacement(String placementMembers) throws Exception {
        return pack("\"structures\":[{\"nativeStructures\":[{\"structure\":\"minecraft:ancient_city\"}], "
                + placementMembers + "}]");
    }

    private static List<String> blockingErrors(File pack) {
        return PackValidator.validate(pack).getBlockingErrors();
    }

    private static void assertBlockedNaming(String fragment, File pack) {
        List<String> errors = blockingErrors(pack);
        assertTrue(errors.toString(), errors.stream().anyMatch(message -> message.contains(fragment)));
    }

    private void assertSingleErrorStartsWith(String prefix, String malformedFile) throws Exception {
        File pack = pack("");
        write(pack, malformedFile, "{");

        List<String> errors = blockingErrors(pack);

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).startsWith(prefix));
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

    private static List<String> nativeReplacementErrors(
            File pack,
            Set<String> replacementOutputStructures,
            Map<String, List<StructureGraphValidation.SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        return NativeReplacementValidation.validate(pack, replacementOutputStructures, sampledVerticalEnvelopes).stream()
                .map(Problem::message)
                .toList();
    }

    private Map<String, List<StructureGraphValidation.SampledVerticalEnvelope>> sampledEnvelope(
            String structureKey,
            int pieceCount,
            int minimumYOffset,
            int maximumYOffset
    ) {
        return Map.of(structureKey, List.of(
                new StructureGraphValidation.SampledVerticalEnvelope(
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
