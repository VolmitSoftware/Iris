package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class SurfaceFootprintOrderingTest {
    @Test
    public void sparseStationsPreserveCoordinateOrderAcrossSignedBoundaries() {
        Random random = new Random(9482741L);
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(90, 0D, "land");
        for (int stationCount : new int[]{1, 2, 17, 1024}) {
            Long2ObjectOpenHashMap<SurfaceColumn> columns = new Long2ObjectOpenHashMap<>();
            for (int index = 0; index < 4096; index++) {
                int x = index - 2048;
                int z = random.nextInt();
                int station = random.nextBoolean() ? stationCount - 1 : random.nextInt(stationCount);
                SurfaceColumn column = new SurfaceColumn(x, z, terrain, station, SurfaceRole.CHANNEL,
                        64, 68, false);
                columns.put(RiverFootprint.pack(x, z), column);
            }
            ErosionField field = new ErosionField(columns, 0, null, 0, 0L);
            ArrayList<SurfaceColumn> expected = new ArrayList<>(columns.values());
            expected.sort(Comparator.comparingInt(SurfaceColumn::station)
                    .thenComparingLong(column -> RiverFootprint.pack(column.x(), column.z())));
            assertEquals(expected, Arrays.asList(SurfaceFootprintCompiler.orderedColumns(field, stationCount)));
        }
    }

    @Test
    public void anEmptyFieldNeedsNoColumns() {
        ErosionField empty = new ErosionField(new Long2ObjectOpenHashMap<>(), 0, null, 0, 0L);
        assertEquals(0, SurfaceFootprintCompiler.orderedColumns(empty, 0).length);
        assertEquals(0, SurfaceFootprintCompiler.orderedColumns(empty, 1024).length);
    }
}
