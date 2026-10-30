package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorldgenExportSubterrain263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final String POOLS = WorldgenExportRequest.DEFAULT_NAMESPACE + ":subterrain/pools";
    private static final String TUBES = WorldgenExportRequest.DEFAULT_NAMESPACE + ":subterrain/tubes";
    private static final String ANCHOR = WorldgenExportRequest.DEFAULT_NAMESPACE + ":subterrain/anchor";
    private static final String TEMPLATES = "data/" + WorldgenExportRequest.DEFAULT_NAMESPACE + "/structure/subterrain/";
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;
    private static final int POOLS_CENTER_Y = -49;
    private static final int POOLS_BELOW_CENTER = 10;
    private static final int POOLS_EXTENT = 10;
    private static final int MAX_REACH = 128;
    private static final String DIMENSION = """
            {"regions": ["region"], "subterrainFeatures": [
              {"id": "pools", "family": "CENOTE", "worldYRange": {"min": -60, "max": -40}, "spacing": 64, "probability": 1.0,
               "radius": 8, "height": 12, "fluid": "WATER", "fluidDepth": 2, "shapeWarp": 0.5, "pillarSpacing": 0,
               "formationFraction": 0.0, "solid": "minecraft:stone"},
              {"id": "tubes", "family": "LAVA_TUBE", "allowedRegions": ["region"], "worldYRange": {"min": -60, "max": 40},
               "spacing": 320, "probability": 0.5, "length": 300, "radius": 6, "height": 8, "fluidDepth": 2, "chimneyHeight": 0,
               "shapeWarp": 0.0, "pillarSpacing": 0, "formationFraction": 0.0, "solid": "minecraft:stone"}]}
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void subterrainRoomsLoadAndPlaceThroughRealCodecs() throws IOException {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), DIMENSION);
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(temporaryFolder.newFolder("exports").toPath())
                .format("directory")
                .build();

        WorldgenExportResult result = WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            assertRegistered(sampler, POOLS, 4, 2);
            assertRegistered(sampler, TUBES, 20, 10);
            int cornerX = CHUNK_X << 4;
            int cornerZ = CHUNK_Z << 4;

            BoundingBox pools = placedBox(sampler, POOLS);
            assertEquals(cornerX - POOLS_EXTENT, pools.minX());
            assertEquals(cornerX + POOLS_EXTENT, pools.maxX());
            assertEquals(cornerZ - POOLS_EXTENT, pools.minZ());
            assertEquals(cornerZ + POOLS_EXTENT, pools.maxZ());
            assertEquals(POOLS_CENTER_Y - POOLS_BELOW_CENTER, pools.minY());

            BoundingBox tubes = placedBox(sampler, TUBES);
            assertEquals(2 * MAX_REACH + 1, Math.max(tubes.getXSpan(), tubes.getZSpan()));
            assertTrue(tubes.toString(), tubes.minX() >= cornerX - MAX_REACH && tubes.maxX() <= cornerX + MAX_REACH);
            assertTrue(tubes.toString(), tubes.minZ() >= cornerZ - MAX_REACH && tubes.maxZ() <= cornerZ + MAX_REACH);
            assertTrue(tubes.toString(), tubes.minY() >= -60 && tubes.maxY() <= 40);
        }
        assertTemplatesResolve(result.output());
    }

    private static void assertRegistered(WorldgenExportSampler sampler, String id, int spacing, int separation) {
        Identifier identifier = Identifier.parse(id);
        Structure structure = sampler.registries().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, identifier)).value();
        assertTrue(id, structure instanceof JigsawStructure);
        assertEquals(id, GenerationStep.Decoration.UNDERGROUND_STRUCTURES, structure.step());
        StructureSet set = sampler.registries().lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, identifier)).value();
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        assertEquals(id, spacing, placement.spacing());
        assertEquals(id, separation, placement.separation());
        assertTrue(id, sampler.registries().lookupOrThrow(Registries.TEMPLATE_POOL).containsKey(identifier));
    }

    private static BoundingBox placedBox(WorldgenExportSampler sampler, String id) throws IOException {
        StructureStart start = sampler.structureStart(id, CHUNK_X, CHUNK_Z);
        assertTrue(id, start.isValid());
        assertEquals(id, 1, start.getPieces().size());
        PoolElementStructurePiece piece = (PoolElementStructurePiece) start.getPieces().getFirst();
        assertTrue(id + " " + piece.getElement(), piece.getElement() instanceof ListPoolElement);
        return start.getBoundingBox();
    }

    private static void assertTemplatesResolve(Path datapack) throws IOException {
        List<Path> templates;
        try (Stream<Path> paths = Files.walk(datapack.resolve(TEMPLATES))) {
            templates = paths.filter(path -> path.toString().endsWith(".nbt")).sorted().toList();
        }
        assertEquals(12, templates.size());
        boolean lava = false;
        for (Path path : templates) {
            CompoundTag tag;
            try (InputStream input = Files.newInputStream(path)) {
                tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            }
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK, tag);
            StructurePlaceSettings settings = new StructurePlaceSettings();
            assertTrue(path.toString(), template.filterBlocks(BlockPos.ZERO, settings, Blocks.AIR).isEmpty());
            if (path.getFileName().toString().endsWith("_core.nbt")) {
                assertFalse(path.toString(), template.filterBlocks(BlockPos.ZERO, settings, Blocks.CAVE_AIR).isEmpty());
                List<StructureTemplate.JigsawBlockInfo> jigsaws = template.getJigsaws(BlockPos.ZERO, Rotation.NONE);
                assertEquals(path.toString(), 1, jigsaws.size());
                assertEquals(ANCHOR, jigsaws.getFirst().name().toString());
                lava |= !template.filterBlocks(BlockPos.ZERO, settings, Blocks.LAVA).isEmpty();
            } else {
                assertFalse(path.toString(), template.filterBlocks(BlockPos.ZERO, settings, Blocks.STONE).isEmpty());
            }
        }
        assertTrue("no lava tube template holds lava", lava);
    }
}
