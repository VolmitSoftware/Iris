package art.arcane.iris.core.structure;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.core.structure.export.VanillaJigsawDatapackExporter;
import art.arcane.iris.core.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.core.structure.export.VanillaJigsawExportResult;
import art.arcane.iris.core.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.core.structure.export.ObjectContents;
import art.arcane.iris.engine.framework.structure.ObjectFrame;
import art.arcane.iris.engine.object.structure.IrisStructure;
import com.volmit.block.BlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.google.gson.Gson;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.TemplatePool;
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

public class VillageImporterConnectorRoundTripTest {
    private static final String FINAL_STATE =
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void vanillaNbtMetadataSurvivesIrisPieceAndVanillaExport() throws Exception {
        TemplatePool.JigsawConnector jigsaw = new TemplatePool.JigsawConnector(
                new BlockPosition(1, 1, 0),
                BlockFace.NORTH,
                BlockFace.UP,
                Optional.of(ResourceId.parse("minecraft:terminal")),
                "minecraft:source",
                "minecraft:door",
                true,
                Optional.of(FINAL_STATE),
                -17,
                -29
        );
        Map<String, Object> importedConnector = VillageImporter.connectorJson(jigsaw, "roundtrip");

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
                graph,
                graph.objects::get
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
        graph.objects.put("objects/start", ObjectContents.plain(3, 3, 3,
                Map.of(new BlockPosition(1, 1, 0), BlockState.parse(connector.getFinalState())), Map.of()));
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
        return graph;
    }

    private static final class TestGraph implements StructureGraphResolver {
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, ObjectContents> objects = new LinkedHashMap<>();
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
        public ObjectFrame loadObject(String key) {
            ObjectContents contents = objects.get(key);
            return contents == null ? null : new ObjectFrame(contents.width(), contents.height(), contents.depth());
        }
    }
}
