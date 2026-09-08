package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteMode;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.object.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.util.common.math.IrisBlockVector;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioPersistenceEditorsTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final String PLANAR_KEY = "studio/planar";
    private static final String SPATIAL_KEY = "studio/spatial";

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path planarPrototype;
    private static Path spatialPrototype;

    @BeforeClass
    public static void createPrototypeProjects() throws Exception {
        planarPrototype = prototypeFolder.newFolder("planar-prototype").toPath();
        assertTrue(JigsawStudioProjectCreator.create(
                planarPrototype,
                new JigsawStudioProjectCreator.Options(
                        PLANAR_KEY,
                        JigsawStudioMode.PLANAR_JIGSAW,
                        JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                        new JigsawStudioCellDimensions(16, 16, 16))).successful());
        spatialPrototype = prototypeFolder.newFolder("spatial-prototype").toPath();
        assertTrue(JigsawStudioProjectCreator.create(
                spatialPrototype,
                new JigsawStudioProjectCreator.Options(
                        SPATIAL_KEY,
                        JigsawStudioMode.SPATIAL_JIGSAW,
                        JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                        new JigsawStudioCellDimensions(16, 16, 16))).successful());
    }

    @Test
    public void createsCoherentThemeSetAndAppliesAtomicMetadataEditors() throws Exception {
        Path packRoot = createPlanarProject();

        JigsawStudioPoolEditor.WeightUpdate weight = JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                0,
                "studio/planar/end",
                4);
        JigsawStudioPoolEditor.ChanceUpdate chance = JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                0,
                "studio/planar/end",
                0.35D);

        assertTrue(weight.changed());
        assertTrue(chance.changed());

        JigsawStudioGraphEditor.VariantFamilyCreation creation = JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "studio/planar",
                planarSources("studio/planar"),
                "variant-2");

        assertTrue(creation.writeResult().successful());
        assertEquals(6, creation.pieceKeysByWorkcell().size());
        assertEquals(
                "studio/planar/variants/end/variant-2",
                creation.pieceKeysByWorkcell().get(JigsawPlanarArchetype.END.stableId()));
        JsonObject piecePool = readJson(packRoot.resolve("jigsaw-pools/studio/planar/pieces.json"));
        JsonArray entries = piecePool.getAsJsonArray("pieces");
        assertEquals("studio/planar/end", entries.get(0).getAsJsonObject().get("piece").getAsString());
        assertEquals("studio/planar/variants/end/variant-2",
                entries.get(1).getAsJsonObject().get("piece").getAsString());
        assertEquals(4, entries.get(1).getAsJsonObject().get("weight").getAsInt());
        assertEquals(0.35D, entries.get(1).getAsJsonObject().get("chance").getAsDouble(), 0D);
        JsonObject duplicatedEnd = readJson(
                packRoot.resolve("jigsaw-pieces/studio/planar/variants/end/variant-2.json"));
        assertEquals(1, duplicatedEnd.getAsJsonArray("themes").size());
        assertEquals("variant-2", duplicatedEnd.getAsJsonArray("themes").get(0).getAsString());

        assertTrue(JigsawStudioGraphEditor.updatePieceThemes(
                packRoot,
                "studio/planar",
                "studio/planar/blank",
                List.of()).successful());
        JigsawStudioPieceRules terminalRules = new JigsawStudioPieceRules(0, 30, 0, 0, true);
        assertTrue(JigsawStudioGraphEditor.updatePieceRules(
                packRoot,
                "studio/planar",
                "studio/planar/end",
                terminalRules).successful());
        assertTrue(JigsawStudioGraphEditor.updatePieceRules(
                packRoot,
                "studio/planar",
                "studio/planar/variants/end/variant-2",
                terminalRules).successful());
        assertTrue(JigsawStudioStructureEditor.updateThemeSets(
                packRoot,
                "studio/planar",
                List.of(
                        new IrisJigsawThemeSet("variant-1", 2),
                        new IrisJigsawThemeSet("variant-2", 3))).successful());
        assertTrue(JigsawStudioStructureEditor.updateRequireCaps(
                packRoot,
                "studio/planar",
                true).successful());

        JsonObject structure = readJson(packRoot.resolve("structures/studio/planar.json"));
        assertTrue(structure.get("requireCaps").getAsBoolean());
        assertEquals("FAIL_ASSEMBLY", structure.get("branchFailurePolicy").getAsString());
        assertEquals(3, structure.getAsJsonArray("themeSets").get(1)
                .getAsJsonObject().get("weight").getAsInt());
        JsonObject savedRules = readJson(packRoot.resolve("jigsaw-pieces/studio/planar/end.json"))
                .getAsJsonObject("rules");
        assertTrue(savedRules.get("terminal").getAsBoolean());
    }

    @Test
    public void portableBranchTerminationPolicySurvivesStructureEditors() throws Exception {
        Path packRoot = temporaryFolder.newFolder("portable-policy").toPath();
        JigsawStudioProjectCreator.Options options = new JigsawStudioProjectCreator.Options(
                "portable/policy",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                new JigsawStudioCellDimensions(16, 16, 16));
        assertTrue(JigsawStudioProjectCreator.create(packRoot, options).successful());

        assertTrue(JigsawStudioStructureEditor.updateLimits(
                packRoot,
                "portable/policy",
                6,
                7).successful());
        assertTrue(JigsawStudioStructureEditor.updateWorkcellEnabled(
                packRoot,
                "portable/policy",
                JigsawPlanarArchetype.BLANK,
                false).successful());

        JsonObject structure = readJson(packRoot.resolve("structures/portable/policy.json"));
        assertEquals("TERMINATE_BRANCH", structure.get("branchFailurePolicy").getAsString());
        assertEquals(6, structure.get("maxDepth").getAsInt());
        assertEquals(7, structure.get("maxSizeChunks").getAsInt());
    }

    @Test
    public void duplicateEndVariantCopiesPiecesAndCapsMembershipsExactly() throws Exception {
        Path packRoot = createPlanarProject();
        String sourcePiece = "studio/planar/end";
        String targetPiece = "studio/planar/variants/end/variant-2";
        assertTrue(JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                0,
                sourcePiece,
                4).changed());
        assertTrue(JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                0,
                sourcePiece,
                0.35D).changed());
        assertTrue(JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/caps",
                0,
                sourcePiece,
                7).changed());
        assertTrue(JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/caps",
                0,
                sourcePiece,
                0.8D).changed());

        StructureWriteResult duplicated = JigsawStudioGraphEditor.duplicatePiece(
                packRoot,
                "studio/planar",
                sourcePiece,
                targetPiece);

        assertTrue(duplicated.successful());
        assertMembershipCopy(
                packRoot.resolve("jigsaw-pools/studio/planar/pieces.json"),
                sourcePiece,
                targetPiece,
                4,
                0.35D);
        assertMembershipCopy(
                packRoot.resolve("jigsaw-pools/studio/planar/caps.json"),
                sourcePiece,
                targetPiece,
                7,
                0.8D);
        assertPieceMetadataCopy(packRoot, sourcePiece, targetPiece);
        assertEquals(-1L, Files.mismatch(
                packRoot.resolve("objects/" + sourcePiece + ".iob"),
                packRoot.resolve("objects/" + targetPiece + ".iob")));
    }

    @Test
    public void newCrossVariantCopiesStartAndPiecesMembershipsExactly() throws Exception {
        Path packRoot = createPlanarProject();
        String sourcePiece = "studio/planar/cross";
        String targetPiece = "studio/planar/variants/cross/variant-2";
        assertTrue(JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/start",
                0,
                sourcePiece,
                9).changed());
        assertTrue(JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/start",
                0,
                sourcePiece,
                0.6D).changed());
        assertTrue(JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                4,
                sourcePiece,
                5).changed());
        assertTrue(JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot,
                "studio/planar",
                "studio/planar/pieces",
                4,
                sourcePiece,
                0.45D).changed());

        StructureWriteResult created = JigsawStudioGraphEditor.createBlankVariant(
                packRoot,
                "studio/planar",
                sourcePiece,
                targetPiece);

        assertTrue(created.successful());
        assertMembershipCopy(
                packRoot.resolve("jigsaw-pools/studio/planar/start.json"),
                sourcePiece,
                targetPiece,
                9,
                0.6D);
        assertMembershipCopy(
                packRoot.resolve("jigsaw-pools/studio/planar/pieces.json"),
                sourcePiece,
                targetPiece,
                5,
                0.45D);
        assertPieceMetadataCopy(packRoot, sourcePiece, targetPiece);
        assertEquals(new IrisBlockVector(16, 16, 16), IrisObject.sampleSize(
                packRoot.resolve("objects/" + targetPiece + ".iob").toFile()));
    }

    @Test
    public void deletesExactVariantMembershipsButProtectsFinalEnabledArchetype() throws Exception {
        Path packRoot = createPlanarProject();
        JigsawStudioGraphEditor.VariantFamilyCreation creation = JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "studio/planar",
                planarSources("studio/planar"),
                "variant-2");
        String corner = creation.pieceKeysByWorkcell().get(JigsawPlanarArchetype.CORNER.stableId());

        JigsawStudioGraphEditor.PieceDeletionResult deleted =
                JigsawStudioGraphEditor.deletePieceVariant(packRoot, "studio/planar", corner);

        assertTrue(deleted.writeResult().successful());
        assertEquals(1, deleted.removedPoolMemberships());
        assertEquals(1, deleted.changedPools());
        assertEquals(1, deleted.removedPieceResources());
        assertEquals(1, deleted.removedObjectResources());
        assertFalse(Files.exists(packRoot.resolve("jigsaw-pieces/" + corner + ".json")));
        assertFalse(Files.exists(packRoot.resolve("objects/" + corner + ".iob")));

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioGraphEditor.deletePieceVariant(
                        packRoot,
                        "studio/planar",
                        "studio/planar/corner"));
        assertTrue(failure.getMessage().contains("final variant for enabled planar workcell"));
    }

    @Test
    public void projectDeletionPlansBlockReferencesAndRejectStaleGraphs() throws Exception {
        Path packRoot = createPlanarProject();
        Path biome = packRoot.resolve("biomes/reference.json");
        Files.createDirectories(biome.getParent());
        Files.writeString(
                biome,
                "{\"structures\":[{\"structures\":[\"studio/planar\"]}]}",
                StandardCharsets.UTF_8);

        JigsawStudioProjectDeletionService.DeletionPlan blocked =
                JigsawStudioProjectDeletionService.inspect(packRoot, "studio/planar");

        assertFalse(blocked.deletable());
        assertEquals("biomes/reference.json", blocked.blockers().getFirst().ownerPath());
        assertThrows(
                IOException.class,
                () -> JigsawStudioProjectDeletionService.delete(blocked));

        Files.delete(biome);
        JigsawStudioProjectDeletionService.DeletionPlan stale =
                JigsawStudioProjectDeletionService.inspect(packRoot, "studio/planar");
        assertTrue(stale.deletable());
        assertTrue(JigsawStudioStructureEditor.updateLimits(
                packRoot,
                "studio/planar",
                9,
                8).successful());
        IOException staleFailure = assertThrows(
                IOException.class,
                () -> JigsawStudioProjectDeletionService.delete(stale));
        assertTrue(staleFailure.getMessage().contains("changed after deletion was inspected"));

        JigsawStudioProjectDeletionService.DeletionPlan current =
                JigsawStudioProjectDeletionService.inspect(packRoot, "studio/planar");
        JigsawStudioProjectDeletionService.ProjectDeletionResult deleted =
                JigsawStudioProjectDeletionService.delete(current);

        assertTrue(deleted.manifestRemoved());
        assertTrue(deleted.removedResourceCount() > 0);
        assertFalse(Files.exists(packRoot.resolve("structures/studio/planar.json")));
        assertFalse(Files.exists(packRoot.resolve("jigsaw-pools/studio/planar/start.json")));
    }

    @Test
    public void projectDeletionRejectsCoordinatedReferenceWrittenAfterInspectionWithoutRemovingOwnedBytes()
            throws Exception {
        String structureKey = "studio/planar";
        Path packRoot = createPlanarProject();
        JigsawStudioProjectDeletionService.DeletionPlan plan =
                JigsawStudioProjectDeletionService.inspect(packRoot, structureKey);
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Path manifestPath = writer.ownershipManifestPath(StructureKey.parse(structureKey, "iris"));
        byte[] expectedManifest = Files.readAllBytes(manifestPath);
        Map<String, byte[]> expectedResources = new LinkedHashMap<>();
        for (String relativePath : plan.expectedResourceHashes().keySet()) {
            expectedResources.put(relativePath, Files.readAllBytes(packRoot.resolve(relativePath)));
        }

        StructureKey referenceOwner = StructureKey.parse("iris:delete/reference-owner");
        StructureResourceBundle referenceBundle = StructureResourceBundle.builder(referenceOwner)
                .source(StructureSource.of(StructureSource.Kind.IRIS, referenceOwner))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .textResource(
                        "biomes/delete/late-reference.json",
                        "{\"structures\":[{\"structures\":[\"" + structureKey + "\"]}]}")
                .build();
        StructureWriteResult competingWrite = writer.write(referenceBundle, StructureWriteMode.ADD_ONLY);
        assertEquals(StructureWriteResult.Status.ADDED, competingWrite.status());

        IOException failure = assertThrows(
                IOException.class,
                () -> JigsawStudioProjectDeletionService.delete(plan));

        assertTrue(failure.getMessage().contains("gained 1 reverse references"));
        assertArrayEquals(expectedManifest, Files.readAllBytes(manifestPath));
        for (Map.Entry<String, byte[]> resource : expectedResources.entrySet()) {
            assertArrayEquals(resource.getValue(), Files.readAllBytes(packRoot.resolve(resource.getKey())));
        }
    }

    @Test
    public void projectDeletionThroughSymbolicPackRootFindsReverseReferences() throws Exception {
        String structureKey = "studio/planar";
        Path packRoot = createPlanarProject();
        Path biome = packRoot.resolve("biomes/reference.json");
        Files.createDirectories(biome.getParent());
        Files.writeString(
                biome,
                "{\"structures\":[{\"structures\":[\"" + structureKey + "\"]}]}",
                StandardCharsets.UTF_8);
        Path linkedRoot = createSymbolicPackRoot(packRoot, "symbolic-blocked-pack");

        JigsawStudioProjectDeletionService.DeletionPlan plan =
                JigsawStudioProjectDeletionService.inspect(linkedRoot, structureKey);

        assertEquals(packRoot.toRealPath(), plan.packRoot());
        assertFalse(plan.deletable());
        assertEquals("biomes/reference.json", plan.blockers().getFirst().ownerPath());
        assertThrows(IOException.class, () -> JigsawStudioProjectDeletionService.delete(plan));
        assertTrue(Files.exists(packRoot.resolve("structures/" + structureKey + ".json")));
    }

    @Test
    public void projectDeletionThroughSymbolicPackRootRemovesCompleteOwnedClosure() throws Exception {
        String structureKey = "studio/planar";
        Path packRoot = createPlanarProject();
        StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
        Path manifestPath = writer.ownershipManifestPath(StructureKey.parse(structureKey, "iris"));
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(manifestPath));
        Path linkedRoot = createSymbolicPackRoot(packRoot, "symbolic-complete-pack");

        JigsawStudioProjectDeletionService.DeletionPlan plan =
                JigsawStudioProjectDeletionService.inspect(linkedRoot, structureKey);
        JigsawStudioProjectDeletionService.ProjectDeletionResult deleted =
                JigsawStudioProjectDeletionService.delete(plan);

        assertEquals(packRoot.toRealPath(), plan.packRoot());
        assertTrue(deleted.manifestRemoved());
        assertEquals(manifest.resourceHashes().size(), deleted.removedResourceCount());
        for (String relativePath : manifest.resourceHashes().keySet()) {
            assertFalse(Files.exists(packRoot.resolve(relativePath)));
        }
        assertFalse(Files.exists(manifestPath));
    }

    @Test
    public void chanceUpdateRejectsStaleMembershipIdentityAndInvalidBounds() throws Exception {
        Path packRoot = createPlanarProject();

        assertThrows(
                IllegalArgumentException.class,
                () -> JigsawStudioPoolEditor.updateChanceAtIndex(
                        packRoot,
                        "studio/planar",
                        "studio/planar/pieces",
                        0,
                        "studio/planar/end",
                        Double.NaN));
        IOException stale = assertThrows(
                IOException.class,
                () -> JigsawStudioPoolEditor.updateChanceAtIndex(
                        packRoot,
                        "studio/planar",
                        "studio/planar/pieces",
                        0,
                        "studio/planar/straight",
                        0.5D));
        assertTrue(stale.getMessage().contains("changed before the update"));
    }

    @Test
    public void createsSpatialThemeSetFromTheSelectedSpatialWorkcellSource() throws Exception {
        Path packRoot = createSpatialProject("spatial-theme");

        JigsawStudioGraphEditor.VariantFamilyCreation creation = JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "studio/spatial",
                Map.of(JigsawStudioLayout.SPATIAL_WORKCELL_ID, "studio/spatial/start"),
                "variant-2");

        assertTrue(creation.writeResult().successful());
        assertEquals(
                "studio/spatial/variants/spatial/variant-2",
                creation.pieceKeysByWorkcell().get(JigsawStudioLayout.SPATIAL_WORKCELL_ID));
        JsonObject pool = readJson(packRoot.resolve("jigsaw-pools/studio/spatial/start.json"));
        assertEquals(8, pool.getAsJsonArray("pieces").size());
    }

    @Test
    public void createsSpatialThemeSetAcrossEveryDedicatedSpatialWorkcell() throws Exception {
        Path packRoot = createSpatialProject("spatial-theme-row");

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(JigsawStudioLayout.SPATIAL_WORKCELL_ID, "studio/spatial/start");
        for (int connectorCount = 1; connectorCount <= 6; connectorCount++) {
            String pieceKey = "studio/spatial/connectors-" + connectorCount;
            sources.put(JigsawStudioLayout.SPATIAL_WORKCELL_ID + "/" + pieceKey, pieceKey);
        }
        JigsawStudioGraphEditor.VariantFamilyCreation creation = JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot,
                "studio/spatial",
                sources,
                "variant-2");

        assertTrue(creation.writeResult().successful());
        assertEquals(7, creation.pieceKeysByWorkcell().size());
        for (String targetPieceKey : creation.pieceKeysByWorkcell().values()) {
            assertTrue(Files.isRegularFile(packRoot.resolve("jigsaw-pieces/" + targetPieceKey + ".json")));
            assertTrue(Files.isRegularFile(packRoot.resolve("objects/" + targetPieceKey + ".iob")));
        }
        JsonObject pool = readJson(packRoot.resolve("jigsaw-pools/studio/spatial/start.json"));
        assertEquals(14, pool.getAsJsonArray("pieces").size());
    }

    @Test
    public void persistsVariantPlanarWorkcellAndSpatialWorkcellLabelsWithoutRewritingObjects() throws Exception {
        Path planarRoot = createPlanarProject();
        Path planarObject = planarRoot.resolve("objects/studio/planar/end.iob");
        Path planarPool = planarRoot.resolve("jigsaw-pools/studio/planar/pieces.json");
        byte[] objectBefore = Files.readAllBytes(planarObject);
        byte[] poolBefore = Files.readAllBytes(planarPool);

        assertTrue(JigsawStudioGraphEditor.updatePieceDisplayName(
                planarRoot,
                "studio/planar",
                "studio/planar/end",
                "Grand Longhouse").successful());
        assertTrue(JigsawStudioStructureEditor.updateWorkcellDisplayName(
                planarRoot,
                "studio/planar",
                JigsawPlanarArchetype.END,
                "Village Entrances").successful());

        assertArrayEquals(objectBefore, Files.readAllBytes(planarObject));
        assertArrayEquals(poolBefore, Files.readAllBytes(planarPool));
        assertEquals("Grand Longhouse", readJson(planarRoot.resolve(
                "jigsaw-pieces/studio/planar/end.json")).get("displayName").getAsString());
        JsonObject planarStructure = readJson(planarRoot.resolve("structures/studio/planar.json"));
        assertEquals("Village Entrances", workcell(
                planarStructure.getAsJsonArray("planarWorkcells"),
                "END").get("displayName").getAsString());

        Path spatialRoot = createSpatialProject("labels-spatial");
        Path spatialObject = spatialRoot.resolve("objects/studio/spatial/start.iob");
        byte[] spatialBefore = Files.readAllBytes(spatialObject);

        assertTrue(JigsawStudioStructureEditor.updateSpatialWorkcellDisplayName(
                spatialRoot,
                "studio/spatial",
                "Stronghold Rooms").successful());

        assertArrayEquals(spatialBefore, Files.readAllBytes(spatialObject));
        assertEquals("Stronghold Rooms", readJson(spatialRoot.resolve(
                "structures/studio/spatial.json")).get("spatialWorkcellDisplayName").getAsString());
    }

    @Test
    public void displayLabelsUseSixtyFourVisibleCodePointsAndRejectFormattingControls() {
        String sixtyFourEmoji = "😀".repeat(64);

        assertEquals(sixtyFourEmoji, JigsawStudioGraphEditor.normalizeDisplayName(sixtyFourEmoji));
        assertEquals("Trimmed Name", JigsawStudioGraphEditor.normalizeDisplayName("  Trimmed Name  "));
        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioGraphEditor.normalizeDisplayName("😀".repeat(65)));
        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioGraphEditor.normalizeDisplayName("Line\nBreak"));
        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioGraphEditor.normalizeDisplayName("§aFormatted"));
    }

    private Path createPlanarProject() throws Exception {
        return copyPrototype(planarPrototype, "planar-pack");
    }

    private Path createSpatialProject(String folderName) throws Exception {
        return copyPrototype(spatialPrototype, folderName);
    }

    private Path copyPrototype(Path prototype, String folderName) throws IOException {
        Path packRoot = temporaryFolder.newFolder(folderName).toPath();
        try (Stream<Path> entries = Files.walk(prototype)) {
            for (Path entry : entries.toList()) {
                Path destination = packRoot.resolve(prototype.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return packRoot;
    }

    private Path createSymbolicPackRoot(Path packRoot, String linkName) throws IOException {
        Path linkedRoot = temporaryFolder.getRoot().toPath().resolve(linkName);
        try {
            Files.createSymbolicLink(linkedRoot, packRoot);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }
        return linkedRoot;
    }

    private static Map<String, String> planarSources(String structureKey) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            sources.put(
                    archetype.stableId(),
                    structureKey + "/" + archetype.name().toLowerCase(Locale.ROOT));
        }
        return sources;
    }

    private static void assertMembershipCopy(
            Path poolPath,
            String sourcePiece,
            String targetPiece,
            int weight,
            double chance
    ) throws IOException {
        JsonArray entries = readJson(poolPath).getAsJsonArray("pieces");
        JsonObject source = null;
        JsonObject target = null;
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject() || !entry.getAsJsonObject().has("piece")) {
                continue;
            }
            JsonObject membership = entry.getAsJsonObject();
            if (sourcePiece.equals(membership.get("piece").getAsString())) {
                source = membership;
            } else if (targetPiece.equals(membership.get("piece").getAsString())) {
                target = membership;
            }
        }
        assertTrue(source != null);
        assertTrue(target != null);
        JsonObject expected = source.deepCopy();
        expected.addProperty("piece", targetPiece);
        assertEquals(expected, target);
        assertEquals(weight, target.get("weight").getAsInt());
        assertEquals(chance, target.get("chance").getAsDouble(), 0D);
    }

    private static void assertPieceMetadataCopy(
            Path packRoot,
            String sourcePiece,
            String targetPiece
    ) throws IOException {
        JsonObject expected = readJson(packRoot.resolve("jigsaw-pieces/" + sourcePiece + ".json"));
        expected.addProperty("object", targetPiece);
        JsonObject target = readJson(packRoot.resolve("jigsaw-pieces/" + targetPiece + ".json"));
        assertTrue(expected.has("collidable"));
        assertEquals(expected.get("collidable"), target.get("collidable"));
        assertEquals(expected, target);
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject workcell(JsonArray workcells, String archetype) {
        for (JsonElement element : workcells) {
            JsonObject workcell = element.getAsJsonObject();
            if (archetype.equals(workcell.get("archetype").getAsString())) {
                return workcell;
            }
        }
        throw new AssertionError("Missing workcell " + archetype);
    }
}
