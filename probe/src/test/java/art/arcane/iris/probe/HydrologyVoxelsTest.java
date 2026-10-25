package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyVoxelsTest {
    private static final int HEIGHT = 32;
    private static final int FLOOR = 9;
    private static final byte FALLING_WATER = (byte) (HydrologyVoxels.WATER + 8);

    @Test
    public void classifiesFluidLevelsPlantsCaneAndSolids() {
        assertEquals(HydrologyVoxels.WATER, HydrologyVoxels.classify(state("minecraft:water[level=0]", false)));
        assertEquals(FALLING_WATER, HydrologyVoxels.classify(state("minecraft:water[level=8]", false)));
        assertEquals(HydrologyVoxels.WATER, HydrologyVoxels.classify(state("minecraft:water", false)));
        assertEquals(HydrologyVoxels.LAVA + 3, HydrologyVoxels.classify(state("minecraft:lava[level=3]", false)));
        assertEquals(HydrologyVoxels.WATER, HydrologyVoxels.classify(state("minecraft:seagrass", false)));
        assertEquals(HydrologyVoxels.WATER, HydrologyVoxels.classify(state("minecraft:oak_stairs[waterlogged=true]", true)));
        assertEquals(HydrologyVoxels.OPEN, HydrologyVoxels.classify(state("minecraft:short_grass", false)));
        assertEquals(HydrologyVoxels.OPEN, HydrologyVoxels.classify(state("minecraft:red_tulip", false)));
        assertEquals(HydrologyVoxels.OPEN, HydrologyVoxels.classify(state("minecraft:snow[layers=1]", false)));
        assertEquals(HydrologyVoxels.CANE, HydrologyVoxels.classify(state("minecraft:sugar_cane[age=0]", false)));
        assertEquals(HydrologyVoxels.SOLID, HydrologyVoxels.classify(state("minecraft:grass_block[snowy=false]", false)));
        assertEquals(HydrologyVoxels.OPEN, HydrologyVoxels.classify(null));
    }

    @Test
    public void waterloggableBlocksFullSnowAndMangroveRootsHoldWaterWhileCropsFireAndWireDoNot() {
        for (String key : new String[]{"minecraft:glow_lichen[down=true,waterlogged=false]", "minecraft:hanging_roots[waterlogged=false]",
                "minecraft:small_dripleaf[facing=north,half=lower,waterlogged=false]", "minecraft:big_dripleaf_stem[facing=north,waterlogged=false]",
                "minecraft:rail[shape=north_south,waterlogged=false]", "minecraft:glow_lichen", "minecraft:hanging_roots",
                "minecraft:mangrove_roots[waterlogged=false]", "minecraft:muddy_mangrove_roots[axis=y]", "minecraft:snow[layers=8]"}) {
            assertEquals(key, HydrologyVoxels.SOLID, HydrologyVoxels.classify(state(key, false)));
        }
        for (String key : new String[]{"minecraft:wheat[age=3]", "minecraft:pumpkin_stem[age=2]", "minecraft:fire[age=0]",
                "minecraft:redstone_wire[power=0]", "minecraft:crimson_roots", "minecraft:vine[up=true]"}) {
            assertEquals(key, HydrologyVoxels.OPEN, HydrologyVoxels.classify(state(key, false)));
        }
    }

    @Test
    public void containedPondHasNoLeaks() {
        HydrologyVoxels.ChunkBlocks chunk = pond(10);
        HydrologyVoxels.WaterScan scan = scan(volume(chunk));
        assertEquals(9L, scan.waterCells);
        assertEquals(0L, scan.containmentLeaks);
        assertEquals(0L, scan.tickConversionCells);
    }

    @Test
    public void sourceBesideOpenGroundOrOverOpenGroundLeaks() {
        HydrologyVoxels.ChunkBlocks side = pond(10);
        side.set(6, 10, 4, HydrologyVoxels.OPEN);
        HydrologyVoxels.WaterScan sideScan = scan(volume(side));
        assertEquals(1L, sideScan.containmentLeaks);
        assertEquals("leak source beside open ground at 5,10,4", sideScan.examples.get(0));
        HydrologyVoxels.ChunkBlocks under = pond(10);
        under.set(4, FLOOR, 4, HydrologyVoxels.OPEN);
        HydrologyVoxels.WaterScan underScan = scan(volume(under));
        assertEquals(1L, underScan.containmentLeaks);
        assertEquals("leak water over open ground at 4,10,4", underScan.examples.get(0));
    }

    @Test
    public void sugarCaneBesideOrUnderWaterHoldsIt() {
        HydrologyVoxels.ChunkBlocks chunk = pond(10);
        chunk.set(6, 10, 4, HydrologyVoxels.CANE);
        chunk.set(4, FLOOR, 4, HydrologyVoxels.CANE);
        HydrologyVoxels.WaterScan scan = scan(volume(chunk));
        assertEquals(0L, scan.containmentLeaks);
    }

    @Test
    public void sourceAboveItsHeadOverWaterBesideAirLeaks() {
        HydrologyVoxels.ChunkBlocks chunk = pond(10);
        clearAbove(chunk);
        chunk.set(4, 11, 4, HydrologyVoxels.WATER);
        HydrologyVoxels.WaterScan scan = scan(volume(chunk));
        assertEquals(1L, scan.containmentLeaks);
        assertEquals("leak source beside open ground at 4,11,4", scan.examples.get(0));
    }

    @Test
    public void fallingWaterOverWaterNeverSpreadsSideways() {
        HydrologyVoxels.ChunkBlocks chunk = pond(10);
        clearAbove(chunk);
        for (int y = 11; y <= 13; y++) {
            chunk.set(4, y, 4, FALLING_WATER);
        }
        HydrologyVoxels.WaterScan scan = scan(volume(chunk));
        assertEquals(0L, scan.containmentLeaks);
        assertEquals(0L, scan.tickConversionCells);
    }

    @Test
    public void flowingCellsAboveThePlannedHeadThatWouldConvertOrSpreadAreTickCells() {
        HydrologyVoxels.ChunkBlocks converting = pond(10);
        converting.set(4, 10, 4, FALLING_WATER);
        HydrologyVoxels.WaterScan conversion = scan(volume(converting));
        assertEquals(1L, conversion.tickConversionCells);
        assertEquals("tick flowing cell converts to a source at 4,10,4", conversion.examples.get(0));
        HydrologyVoxels.ChunkBlocks atHead = pond(10);
        atHead.set(4, 10, 4, FALLING_WATER);
        atHead.heads()[(4 << 4) | 4] = 10;
        assertEquals(0L, scan(volume(atHead)).tickConversionCells);
        HydrologyVoxels.ChunkBlocks spreading = solid();
        spreading.set(4, 10, 4, (byte) (HydrologyVoxels.WATER + 3));
        spreading.set(5, 10, 4, HydrologyVoxels.OPEN);
        spreading.set(8, 10, 8, (byte) (HydrologyVoxels.WATER + 7));
        spreading.set(9, 10, 8, HydrologyVoxels.OPEN);
        HydrologyVoxels.Volume volume = volume(spreading);
        HydrologyVoxels.WaterScan spread = new HydrologyVoxels.WaterScan();
        spread.column(volume, 4, 4, FLOOR, HEIGHT - 1);
        spread.column(volume, 8, 8, FLOOR, HEIGHT - 1);
        assertEquals(1L, spread.tickConversionCells);
        assertEquals("tick flowing cell spreads beside lower dry ground at 4,10,4", spread.examples.get(0));
        assertEquals(0L, spread.containmentLeaks);
    }

    @Test
    public void lavaNeverConvertsAndSpreadsOnlyWithMoreThanTwoLevelsLeft() {
        HydrologyVoxels.ChunkBlocks pool = solid();
        for (int z = 3; z <= 5; z++) {
            for (int x = 3; x <= 5; x++) {
                pool.set(x, 10, z, HydrologyVoxels.LAVA);
            }
        }
        pool.set(4, 10, 4, (byte) (HydrologyVoxels.LAVA + 8));
        assertEquals(0L, scan(volume(pool)).tickConversionCells);
        HydrologyVoxels.ChunkBlocks spreading = solid();
        spreading.set(4, 10, 4, (byte) (HydrologyVoxels.LAVA + 6));
        spreading.set(5, 10, 4, HydrologyVoxels.OPEN);
        spreading.set(8, 10, 8, (byte) (HydrologyVoxels.LAVA + 4));
        spreading.set(9, 10, 8, HydrologyVoxels.OPEN);
        HydrologyVoxels.Volume volume = volume(spreading);
        HydrologyVoxels.WaterScan spread = new HydrologyVoxels.WaterScan();
        spread.column(volume, 4, 4, FLOOR, HEIGHT - 1);
        spread.column(volume, 8, 8, FLOOR, HEIGHT - 1);
        assertEquals(1L, spread.tickConversionCells);
        assertEquals("tick flowing cell spreads beside lower dry ground at 8,10,8", spread.examples.get(0));
    }

    @Test
    public void plannedHeadsWithoutTheirFluidMissTheSurface() {
        HydrologyVoxels.ChunkBlocks chunk = pond(10);
        for (int z = 3; z <= 5; z++) {
            for (int x = 3; x <= 5; x++) {
                chunk.heads()[(z << 4) | x] = 10;
            }
        }
        assertEquals(0L, scan(volume(chunk)).missingSurfaces);
        chunk.set(4, 10, 4, HydrologyVoxels.SOLID);
        chunk.surfaces()[(5 << 4) | 5] = HydrologyVoxels.LAVA;
        HydrologyVoxels.WaterScan scan = scan(volume(chunk));
        assertEquals(2L, scan.missingSurfaces);
        assertEquals("missing planned surface at 4,10,4", scan.examples.get(0));
        assertEquals("missing planned surface at 5,10,5", scan.examples.get(1));
        chunk.surfaces()[(4 << 4) | 4] = HydrologyVoxels.SOLID;
        chunk.surfaces()[(3 << 4) | 3] = HydrologyVoxels.SOLID;
        assertEquals(1L, scan(volume(chunk)).missingSurfaces);
    }

    @Test
    public void unknownNeighboursAreCensoredNotLeaks() {
        HydrologyVoxels.ChunkBlocks chunk = solid();
        chunk.set(15, 10, 4, HydrologyVoxels.WATER);
        HydrologyVoxels.WaterScan scan = new HydrologyVoxels.WaterScan();
        scan.column(volume(chunk), 15, 4, FLOOR, HEIGHT - 1);
        assertEquals(0L, scan.containmentLeaks);
        assertEquals(1L, scan.censoredCells);
    }

    private static HydrologyVoxels.WaterScan scan(HydrologyVoxels.Volume volume) {
        HydrologyVoxels.WaterScan scan = new HydrologyVoxels.WaterScan();
        for (int z = 2; z <= 6; z++) {
            for (int x = 2; x <= 6; x++) {
                scan.column(volume, x, z, FLOOR, HEIGHT - 1);
            }
        }
        return scan;
    }

    private static HydrologyVoxels.Volume volume(HydrologyVoxels.ChunkBlocks chunk) {
        HydrologyVoxels.Volume volume = new HydrologyVoxels.Volume();
        volume.put(chunk);
        return volume;
    }

    private static void clearAbove(HydrologyVoxels.ChunkBlocks chunk) {
        for (int y = 11; y <= 14; y++) {
            for (int z = 3; z <= 5; z++) {
                for (int x = 3; x <= 5; x++) {
                    chunk.set(x, y, z, HydrologyVoxels.OPEN);
                }
            }
        }
    }

    private static HydrologyVoxels.ChunkBlocks pond(int level) {
        HydrologyVoxels.ChunkBlocks chunk = solid();
        for (int z = 3; z <= 5; z++) {
            for (int x = 3; x <= 5; x++) {
                chunk.set(x, level, z, HydrologyVoxels.WATER);
            }
        }
        return chunk;
    }

    private static HydrologyVoxels.ChunkBlocks solid() {
        HydrologyVoxels.ChunkBlocks chunk = HydrologyVoxels.ChunkBlocks.empty(0, 0, HEIGHT);
        for (int y = 0; y <= 14; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    chunk.set(x, y, z, HydrologyVoxels.SOLID);
                }
            }
        }
        return chunk;
    }

    private static NativeBlockState state(String key, boolean waterlogged) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.isAir()).thenReturn(false);
        when(state.isWaterLogged()).thenReturn(waterlogged);
        return state;
    }
}
