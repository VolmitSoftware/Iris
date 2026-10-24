package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.nativefit.NativeStructureFitting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** The modded loaders fit the platform's starts with the same built-in fit as Bukkit, through the shared bridge. */
public class NativeStructureVacuumParityTest {
    private static final int SURFACE_Y = 50;
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_HEIGHT = 384;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void explicitVacuumNeitherCarvesNorInventsFootprintsForPiecesWithoutTemplates() {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        DesertPyramidPiece piece = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        StructureStart start = new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
        BoundingBox bounds = start.getBoundingBox();
        BoundingBox area = new BoundingBox(bounds.minX() - 12, 40, bounds.minZ() - 12,
                bounds.maxX() + 12, bounds.maxY() + 12, bounds.maxZ() + 12);
        Map<BlockPos, BlockState> blocks = flatTerrain(area);
        Map<BlockPos, BlockState> original = new HashMap<>(blocks);

        NativeStructureFitting.builtInModules().beforePieces(NativeStructureFitBridge.chunk(world(blocks), area,
                List.of(new NativeStructureFitBridge.FitTarget("test:vacuum", start,
                        new NativeStartMemo("{\"terrain\":{\"mode\":\"VACUUM\"}}"))), (x, z) -> SURFACE_Y));

        assertEquals(original, blocks);
    }

    private static Map<BlockPos, BlockState> flatTerrain(BoundingBox area) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                blocks.put(new BlockPos(x, SURFACE_Y - 1, z), Blocks.DIRT.defaultBlockState());
                blocks.put(new BlockPos(x, SURFACE_Y, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        return blocks;
    }

    private static WorldGenLevel world(Map<BlockPos, BlockState> blocks) {
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
            case "getBlockState" -> blocks.getOrDefault(arguments[0], Blocks.AIR.defaultBlockState());
            case "setBlock" -> {
                blocks.put(((BlockPos) arguments[0]).immutable(), (BlockState) arguments[1]);
                yield true;
            }
            case "getSeed" -> 1L;
            case "getMinY" -> WORLD_MIN_Y;
            case "getHeight" -> WORLD_HEIGHT;
            case "getLevel" -> null;
            case "holderLookup" -> BuiltInRegistries.BLOCK;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "vacuum-parity-world";
            default -> throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }
}
