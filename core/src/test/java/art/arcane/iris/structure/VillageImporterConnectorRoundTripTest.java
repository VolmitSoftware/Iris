package art.arcane.iris.structure;

import art.arcane.iris.structure.export.VanillaJigsawDatapackExporter;
import art.arcane.iris.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.structure.export.VanillaJigsawExportResult;
import art.arcane.iris.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.structure.graph.StructureGraphResolver;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VillageImporterConnectorRoundTripTest {
    private static final String FINAL_STATE =
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void vanillaNbtMetadataSurvivesIrisPieceAndVanillaExport() throws Exception {
        FakeJigsawBlockInfo jigsaw = new FakeJigsawBlockInfo(-29, -17);
        FakeStructureBlockInfo info = new FakeStructureBlockInfo(new FakeCompoundTag(FINAL_STATE));
        VillageImporter.ConnectorMetadata metadata = VillageImporter.readConnectorMetadata(jigsaw, info);
        Map<String, Object> importedConnector = VillageImporter.connectorJson(
                1,
                1,
                0,
                "north",
                "up",
                "minecraft:terminal",
                "roundtrip",
                "minecraft:source",
                "minecraft:door",
                "ALIGNED",
                metadata
        );

        Map<String, Object> importedPiece = new LinkedHashMap<>();
        importedPiece.put("object", "objects/start");
        importedPiece.put("connectors", List.of(importedConnector));
        importedPiece.put("rotatable", true);
        IrisJigsawPiece piece = new Gson().fromJson(new Gson().toJson(importedPiece), IrisJigsawPiece.class);
        IrisJigsawConnector connector = piece.getConnectors().getFirst();

        assertEquals(FINAL_STATE, connector.getFinalState());
        assertEquals(-17, connector.getSelectionPriority());
        assertEquals(-29, connector.getPlacementPriority());

        TestGraph graph = graph(piece, connector);
        Path output = temporaryFolder.getRoot().toPath().resolve("roundtrip-pack");
        VanillaJigsawExportSource source = VanillaJigsawExportSource.forStructure(
                "metadata",
                graph.structure,
                graph
        );
        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(
                VanillaJigsawExportRequest.builder(source, output)
                        .namespace("roundtrip")
                        .resourcePath("metadata")
                        .build()
        );

        assertTrue(result.diagnostics().toString(), result.isSuccess());
        CompoundTag template = (CompoundTag) NBTUtil.read(output.resolve(
                "data/roundtrip/structure/metadata/piece/pieces/start.nbt").toFile()).getTag();
        CompoundTag block = (CompoundTag) template.getListTag("blocks").get(0);
        CompoundTag nbt = block.getCompoundTag("nbt");
        assertEquals(FINAL_STATE, nbt.getString("final_state"));
        assertEquals(-17, nbt.getInt("selection_priority"));
        assertEquals(-29, nbt.getInt("placement_priority"));
    }

    private static TestGraph graph(IrisJigsawPiece piece, IrisJigsawConnector connector) {
        TestGraph graph = new TestGraph();
        IrisObject object = new IrisObject(3, 3, 3);
        PlatformBlockState finalState = mock(PlatformBlockState.class);
        when(finalState.key()).thenReturn(connector.getFinalState());
        object.setUnsigned(1, 1, 0, finalState);
        graph.objects.put("objects/start", object);
        graph.pieces.put("pieces/start", piece);
        graph.pools.put("pools/start", new IrisJigsawPool().setPieces(new KList<>(
                new IrisJigsawPieceEntry("pieces/start", 1)
        )));
        graph.pools.put("roundtrip/pool/minecraft/terminal", new IrisJigsawPool().setPieces(new KList<>(
                new IrisJigsawPieceEntry().setEmpty(true).setWeight(1)
        )));
        graph.structure = new IrisStructure()
                .setStartPool("pools/start")
                .setMaxDepth(4)
                .setMaxSizeChunks(4)
                .setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH)
                .setCompatibility(IrisJigsawCompatibility.VANILLA_PORTABLE)
                .setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        graph.structure.setLoadKey("metadata");
        return graph;
    }

    private record FakeJigsawBlockInfo(int placementPriority, int selectionPriority) {
    }

    private record FakeStructureBlockInfo(FakeCompoundTag nbt) {
    }

    private record FakeCompoundTag(String finalState) {
        public Optional<String> getString(String key) {
            return "final_state".equals(key) ? Optional.of(finalState) : Optional.empty();
        }
    }

    private static final class TestGraph implements StructureGraphResolver {
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, IrisObject> objects = new LinkedHashMap<>();
        private IrisStructure structure;

        @Override
        public IrisJigsawPool loadPool(String key) {
            return pools.get(key);
        }

        @Override
        public IrisJigsawPiece loadPiece(String key) {
            return pieces.get(key);
        }

        @Override
        public IrisObject loadObject(String key) {
            return objects.get(key);
        }
    }
}
