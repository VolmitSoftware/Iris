package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.TerrainSample;
import com.volmit.world.TerrainSampler;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CaveApplyingBoundarySupportTest {
    private static final int CEILING_Y = 42;
    private static final BlockState AIR = new BlockState("minecraft:cave_air");
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState SAND = new BlockState("minecraft:sand");

    private final CarveTestWorld world = new CarveTestWorld().biome("custom/floor", 1).dressing(new RecordingCaveDressing());
    private final CaveBlockClasses classes = new CaveBlockClasses(new CarveTestWorld.Blocks());

    @Test
    @SuppressWarnings("unchecked")
    public void boundaryBiomeUsesCustomMatterAtItsOwnY() {
        ResourceId resolvedCeiling = CarveTestWorld.id("cave/ceiling");
        CaveApplying modifier = new CaveApplying(world, new CarveWorld(world.resources().noise(), 1L, 384, 127, 8, CarveTestWorld.MIN_HEIGHT), false);
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        Matter floorMatter = mock(Matter.class);
        Matter ceilingMatter = mock(Matter.class);
        MatterSlice<MatterCavern> floorSlice = mock(MatterSlice.class);
        MatterSlice<MatterCavern> ceilingSlice = mock(MatterSlice.class);
        doReturn(true).when(mantleChunk).exists(0);
        doReturn(true).when(mantleChunk).exists(2);
        doReturn(floorMatter).when(mantleChunk).get(0);
        doReturn(ceilingMatter).when(mantleChunk).get(2);
        doReturn(true).when(floorMatter).hasSlice(MatterCavern.class);
        doReturn(true).when(ceilingMatter).hasSlice(MatterCavern.class);
        doReturn(floorSlice).when(floorMatter).getSlice(MatterCavern.class);
        doReturn(ceilingSlice).when(ceilingMatter).getSlice(MatterCavern.class);
        doReturn(new MatterCavern(true, "custom/floor", (byte) 0)).when(floorSlice).get(1, 6, 2);
        doReturn(null).when(ceilingSlice).get(1, CEILING_Y & 15, 2);
        TerrainSampler terrain = new CaveBiomeTerrain((x, y, z) -> y == CEILING_Y ? Optional.of(resolvedCeiling) : Optional.empty());
        CaveApplying.Application application = modifier.new Application(chunkOnly(mantleChunk), mantleChunk, terrain, 2, 2, new ArrayHunk<>(BlockState.class, 16, 64, 16, AIR));
        Map<String, ResourceId> customBiomeCache = new HashMap<>();

        ResourceId floor = application.resolveCaveBoundaryBiome(1, 6, 2, 40, 44, customBiomeCache);
        ResourceId ceiling = application.resolveCaveBoundaryBiome(1, CEILING_Y, 2, 40, 44, customBiomeCache);

        assertEquals(CarveTestWorld.id("custom/floor"), floor);
        assertEquals(resolvedCeiling, ceiling);
    }

    @Test
    public void gravityFloorLayerRequiresSolidSupportBelowItsTarget() {
        Hunk<BlockState> output = new ArrayHunk<>(BlockState.class, 1, 8, 1, AIR);
        output.set(0, 4, 0, AIR);
        assertFalse(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, SAND, classes));
        assertTrue(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, STONE, classes));
        output.set(0, 4, 0, STONE);
        assertTrue(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, SAND, classes));
    }

    @Test
    public void gravityFloorDoesNotReceiveDecoratorsOverLowerCaveAir() {
        Hunk<BlockState> output = new ArrayHunk<>(BlockState.class, 1, 8, 1, AIR);
        output.set(0, 5, 0, SAND);
        output.set(0, 4, 0, AIR);
        assertFalse(CaveApplying.hasStableCaveFloorSupport(output, 0, 6, 0, classes));
        output.set(0, 4, 0, STONE);
        assertTrue(CaveApplying.hasStableCaveFloorSupport(output, 0, 6, 0, classes));
    }

    private static MatterMantle chunkOnly(MantleChunk<Matter> chunk) {
        return new MatterMantle() {
            @Override
            public MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
                return chunk;
            }

            @Override
            public MantleChunk<Matter> loadedChunk(int chunkX, int chunkZ) {
                return null;
            }
        };
    }

    /** Terrain that knows cave biomes only. */
    private record CaveBiomeTerrain(CaveBiomeAt biomes) implements TerrainSampler {
        interface CaveBiomeAt {
            Optional<ResourceId> at(int x, int y, int z);
        }

        @Override
        public Optional<ResourceId> caveBiome(int worldX, int y, int worldZ) {
            return biomes.at(worldX, y, worldZ);
        }

        @Override
        public BlockBox bounds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TerrainSample column(int worldX, int worldZ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BiomeSample biome(int worldX, int y, int worldZ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int fluidHeight() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OptionalInt upperDimensionY(int worldX, int worldZ) {
            throw new UnsupportedOperationException();
        }
    }
}
