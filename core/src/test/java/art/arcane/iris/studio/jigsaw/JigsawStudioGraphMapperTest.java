package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcell;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JigsawStudioGraphMapperTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void horizontalConnectorsMapToPlanarTopology() {
        IrisJigsawPiece corner = new IrisJigsawPiece().setConnectors(new KList<>());
        corner.getConnectors().add(connector(IrisDirection.NORTH_NEGATIVE_Z));
        corner.getConnectors().add(connector(IrisDirection.EAST_POSITIVE_X));

        assertEquals(JigsawPlanarTopology.NORTH_EAST_CORNER,
                JigsawStudioGraphMapper.topologyOf(corner));
    }

    @Test
    public void verticalConnectorsDoNotChangePlanarMask() {
        IrisJigsawPiece end = new IrisJigsawPiece().setConnectors(new KList<>());
        end.getConnectors().add(connector(IrisDirection.WEST_NEGATIVE_X));
        end.getConnectors().add(connector(IrisDirection.UP_POSITIVE_Y));

        assertEquals(JigsawPlanarTopology.WEST_END,
                JigsawStudioGraphMapper.topologyOf(end));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void catalogPreservesDuplicateWeightedPoolEntries() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisJigsawPool> poolLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        when(data.getJigsawPoolLoader()).thenReturn(poolLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);

        IrisJigsawPool start = new IrisJigsawPool().setPieces(new KList<>());
        start.getPieces().add(new IrisJigsawPieceEntry("village/end", 2).setChance(0.25D));
        start.getPieces().add(new IrisJigsawPieceEntry("village/end", 7));
        start.getPieces().add(new IrisJigsawPieceEntry("village/east", 5));
        when(poolLoader.load("village/start")).thenReturn(start);
        IrisJigsawPiece endPiece = planarPiece("village/end", IrisDirection.NORTH_NEGATIVE_Z)
                .setRules(new IrisJigsawPieceRules()
                        .setMinimumDepth(1)
                        .setMaximumDepth(4)
                        .setMinimumPlacements(1)
                        .setMaximumPlacements(2)
                        .setTerminal(true));
        endPiece.getThemes().add("spruce");
        when(pieceLoader.load("village/end")).thenReturn(endPiece);
        when(pieceLoader.load("village/east")).thenReturn(planarPiece(
                "village/east", IrisDirection.EAST_POSITIVE_X));
        IrisStructure structure = new IrisStructure()
                .setStartPool("village/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW);

        JigsawStudioVariantCatalog catalog = JigsawStudioGraphMapper.catalog(
                data, structure, JigsawStudioMode.PLANAR_JIGSAW);
        JigsawStudioVariant end = catalog.find("village/end").orElseThrow();
        JigsawStudioVariant east = catalog.find("village/east").orElseThrow();

        assertEquals(2, end.memberships().size());
        assertEquals(new JigsawStudioPoolMembership("village/start", 0, 2, 0.25D), end.memberships().get(0));
        assertEquals(new JigsawStudioPoolMembership("village/start", 1, 7, 1D), end.memberships().get(1));
        assertEquals(new JigsawStudioPoolMembership("village/start", 2, 5, 1D), east.memberships().getFirst());
        assertEquals(List.of("spruce"), end.themes());
        assertEquals(new JigsawStudioPieceRules(1, 4, 1, 2, true), end.rules());
        assertEquals(3, east.sourceToCanonicalQuarterTurns());
        assertEquals(List.of(end, east), catalog.variants(JigsawPlanarArchetype.END));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ownedUnassignedPiecesRequireOwnedObjectForEditing() throws Exception {
        Path root = temporaryFolder.newFolder("pack").toPath();
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisJigsawPool> poolLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        when(data.getDataFolder()).thenReturn(root.toFile());
        when(data.getJigsawPoolLoader()).thenReturn(poolLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(pieceLoader.load("village/orphan")).thenReturn(planarPiece(
                "village/orphan", IrisDirection.NORTH_NEGATIVE_Z));
        when(pieceLoader.load("village/incomplete")).thenReturn(planarPiece(
                "village/incomplete", IrisDirection.EAST_POSITIVE_X));

        IrisStructure structure = new IrisStructure()
                .setStartPool("village/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW);
        structure.setLoadKey("village/demo");
        StructureKey key = new StructureKey("iris", "village/demo");
        String hash = "0".repeat(64);
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                key,
                StructureSource.of(StructureSource.Kind.DATAPACK, key),
                StructureBackend.IRIS_ASSEMBLY,
                List.of(),
                List.of(),
                Map.of(
                        "jigsaw-pieces/village/orphan.json", hash,
                        "objects/village/orphan.iob", hash,
                        "jigsaw-pieces/village/incomplete.json", hash));
        Path manifestPath = root.resolve(manifest.relativePath());
        Files.createDirectories(manifestPath.getParent());
        Files.write(manifestPath, manifest.toJson());

        JigsawStudioVariantCatalog catalog = JigsawStudioGraphMapper.catalog(
                data, structure, JigsawStudioMode.PLANAR_JIGSAW);
        JigsawStudioVariant orphan = catalog.find("village/orphan").orElseThrow();
        JigsawStudioVariant incomplete = catalog.find("village/incomplete").orElseThrow();

        assertTrue(orphan.owned());
        assertFalse(orphan.assigned());
        assertFalse(incomplete.owned());
        assertFalse(incomplete.assigned());
        assertTrue(catalog.editableGraph());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void managedDatapackManifestResourcesRemainReadOnly() throws Exception {
        Path root = temporaryFolder.newFolder("managed-pack").toPath();
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisJigsawPool> poolLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        when(data.getDataFolder()).thenReturn(root.toFile());
        when(data.getJigsawPoolLoader()).thenReturn(poolLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(pieceLoader.load("village/managed")).thenReturn(planarPiece(
                "village/managed", IrisDirection.NORTH_NEGATIVE_Z));

        IrisStructure structure = new IrisStructure()
                .setStartPool("village/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW);
        structure.setLoadKey("village/managed");
        StructureKey key = new StructureKey("iris", "village/managed");
        String hash = "0".repeat(64);
        String sourcePath = "data/example/worldgen/template_pool/village/start.json";
        StructureOwnershipManifest.Provenance provenance = new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.fromString("33333333-3333-3333-3333-333333333333").toString(),
                hash,
                hash,
                1L,
                Map.of(sourcePath, hash),
                Map.of(sourcePath, "jigsaw-pools/village/start.json"),
                StructureOwnershipManifest.RollbackDisposition.DELETE_CREATED_IF_UNCHANGED);
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                key,
                StructureSource.of(StructureSource.Kind.DATAPACK, key),
                StructureBackend.IRIS_ASSEMBLY,
                List.of(),
                List.of(),
                Map.of(
                        "jigsaw-pieces/village/managed.json", hash,
                        "objects/village/managed.iob", hash),
                provenance);
        Path manifestPath = root.resolve(manifest.relativePath());
        Files.createDirectories(manifestPath.getParent());
        Files.write(manifestPath, manifest.toJson());

        JigsawStudioVariantCatalog catalog = JigsawStudioGraphMapper.catalog(
                data,
                structure,
                JigsawStudioMode.PLANAR_JIGSAW);
        JigsawStudioVariant managed = catalog.find("village/managed").orElseThrow();

        assertFalse(managed.owned());
        assertFalse(managed.assigned());
        assertFalse(catalog.editableGraph());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void spatialLayoutExpandsToFitLargestReachableObject() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(data.getObjectLoader()).thenReturn(objectLoader);
        when(objectLoader.load("stronghold/tall-room")).thenReturn(new IrisObject(24, 32, 20));
        JigsawStudioVariant variant = new JigsawStudioVariant(
                "stronghold/tall-room",
                "stronghold/tall-room",
                "",
                Optional.of(new JigsawStudioCellDimensions(48, 72, 36)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                false,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());

        JigsawStudioCellDimensions dimensions = JigsawStudioGraphMapper.expandSpatialDimensions(
                data,
                new JigsawStudioVariantCatalog(List.of(variant)),
                new JigsawStudioCellDimensions(16, 16, 16));

        assertEquals(new JigsawStudioCellDimensions(24, 32, 24), dimensions);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapsAuthorLabelsAndCanonicalPerVariantDimensionsIndependently() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisJigsawPool> poolLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(data.getJigsawPoolLoader()).thenReturn(poolLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(data.getObjectLoader()).thenReturn(objectLoader);
        IrisJigsawPool start = new IrisJigsawPool().setPieces(new KList<>());
        start.getPieces().add(new IrisJigsawPieceEntry("village/east-gate", 1));
        when(poolLoader.load("village/start")).thenReturn(start);
        IrisJigsawPiece gate = planarPiece("village/east-gate", IrisDirection.EAST_POSITIVE_X)
                .setDisplayName("East Gatehouse");
        when(pieceLoader.load("village/east-gate")).thenReturn(gate);
        when(objectLoader.load("village/east-gate")).thenReturn(new IrisObject(7, 3, 5));
        IrisStructure structure = new IrisStructure()
                .setStartPool("village/start")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW);
        for (IrisJigsawWorkcellArchetype archetype : IrisJigsawWorkcellArchetype.values()) {
            boolean end = archetype == IrisJigsawWorkcellArchetype.END;
            structure.getPlanarWorkcells().add(new IrisJigsawWorkcell(
                    end ? "Village Entrances" : "",
                    archetype,
                    end ? 12 : 16,
                    end ? 6 : 16,
                    end ? 10 : 16,
                    true));
        }

        JigsawStudioLayout layout = JigsawStudioGraphMapper.map(data, structure);
        JigsawStudioBay workcell = layout.get(JigsawPlanarArchetype.END.stableId());
        JigsawStudioVariant variant = layout.variantCatalog().find("village/east-gate").orElseThrow();

        assertEquals("End Cap", workcell.canonicalDisplayName());
        assertEquals("Village Entrances", workcell.displayName());
        assertEquals(new JigsawStudioCellDimensions(12, 6, 10), workcell.capacity());
        assertEquals("East Gatehouse", variant.resolvedDisplayName());
        assertEquals(new JigsawStudioCellDimensions(5, 3, 7), variant.dimensions().orElseThrow());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapsSpatialWorkcellLabelWithoutChangingItsCanonicalRole() {
        IrisData data = mock(IrisData.class);
        when(data.getJigsawPoolLoader()).thenReturn(mock(ResourceLoader.class));
        when(data.getJigsawPieceLoader()).thenReturn(mock(ResourceLoader.class));
        IrisStructure structure = new IrisStructure()
                .setStartPool("stronghold/missing")
                .setMode(IrisJigsawMode.SPATIAL_JIGSAW)
                .setSpatialWorkcellDisplayName("Stronghold Rooms");

        JigsawStudioBay workcell = JigsawStudioGraphMapper.map(data, structure)
                .get(JigsawStudioLayout.SPATIAL_WORKCELL_ID);

        assertEquals("Spatial", workcell.canonicalDisplayName());
        assertEquals("Stronghold Rooms", workcell.displayName());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void planarMapPreservesIndependentEmptyAndDisabledWorkcellBounds() {
        IrisData data = mock(IrisData.class);
        when(data.getJigsawPoolLoader()).thenReturn(mock(ResourceLoader.class));
        when(data.getJigsawPieceLoader()).thenReturn(mock(ResourceLoader.class));
        IrisStructure structure = new IrisStructure()
                .setStartPool("village/missing")
                .setMode(IrisJigsawMode.PLANAR_JIGSAW);
        for (IrisJigsawWorkcellArchetype archetype : IrisJigsawWorkcellArchetype.values()) {
            int ordinal = archetype.ordinal();
            structure.getPlanarWorkcells().add(new IrisJigsawWorkcell(
                    "",
                    archetype,
                    8 + ordinal,
                    6 + ordinal,
                    10 + ordinal,
                    archetype != IrisJigsawWorkcellArchetype.TEE));
        }

        JigsawStudioLayout layout = JigsawStudioGraphMapper.map(data, structure);

        assertEquals(new JigsawStudioCellDimensions(11, 9, 13),
                layout.get("workcell/corner").bounds().dimensions());
        assertEquals(new JigsawStudioCellDimensions(12, 10, 14),
                layout.get("workcell/tee").bounds().dimensions());
        assertFalse(layout.get("workcell/tee").enabled());
        assertTrue(layout.variants(layout.get("workcell/tee")).isEmpty());
    }

    private static IrisJigsawPiece planarPiece(String objectKey, IrisDirection direction) {
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject(objectKey)
                .setConnectors(new KList<>());
        piece.getConnectors().add(connector(direction));
        return piece;
    }

    private static IrisJigsawConnector connector(IrisDirection direction) {
        return new IrisJigsawConnector().setDirection(direction);
    }
}
