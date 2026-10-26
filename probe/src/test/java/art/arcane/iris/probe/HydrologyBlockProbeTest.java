package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyBlockProbeTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void failuresNameMissingSurfaceLeaksTicksAndCoverage() {
        HydrologyBlockProbe.Evidence evidence = new HydrologyBlockProbe.Evidence();
        evidence.scan.missingSurfaces = 2;
        evidence.scan.containmentLeaks = 3;
        evidence.scan.tickConversionCells = 4;
        evidence.channelColumns = 5;
        assertEquals(List.of(
                "Generated blocks miss the planned water surface in 2 columns.",
                "Generated water leaks into open ground in 3 cells.",
                "Fluid ticks would move generated water in 4 cells.",
                "Required generated coverage is absent: pond"), HydrologyBlockProbe.failures(evidence, Set.of("channel", "pond")));
    }

    @Test
    public void dryRidgesFailEvenWhenTheRemainingWaterIsSealed() {
        HydrologyBlockProbe.Evidence evidence = new HydrologyBlockProbe.Evidence();
        HydrologyBlockProbe.inspectWater(new HydrologyBlockProbe.PlannedColumn(WaterRole.CHANNEL, 77, 82, 79, 80,
                HydrologyVoxels.NO_WATER), -103, -1391, evidence);
        HydrologyBlockProbe.inspectWater(new HydrologyBlockProbe.PlannedColumn(WaterRole.WATERFALL, 70, 82, 79, 70, 79),
                -102, -1391, evidence);
        HydrologyBlockProbe.inspectWater(new HydrologyBlockProbe.PlannedColumn(WaterRole.BANK, 82, 82, HydrologyVoxels.NO_WATER,
                82, HydrologyVoxels.NO_WATER), -104, -1391, evidence);

        assertEquals(2L, evidence.plannedWetColumns);
        assertEquals(1L, evidence.effectiveWetColumns);
        assertEquals(1L, evidence.lostWetColumns);
        assertEquals(List.of("Water settling removes planned water from 1 columns in fresh generation."),
                HydrologyBlockProbe.failures(evidence, Set.of()));
        assertEquals(List.of("lost planned water at -103,79,-1391 role=CHANNEL bed=77 effectiveGround=80"), evidence.examples);
    }

    @Test
    public void submergedChannelAndPondFillFailsWithoutAnyLostSurfaceWater() {
        HydrologyBlockProbe.PlannedColumn channel = new HydrologyBlockProbe.PlannedColumn(WaterRole.CHANNEL, 318, 330, 326, 324, 326);
        HydrologyBlockProbe.PlannedColumn pond = new HydrologyBlockProbe.PlannedColumn(WaterRole.POND, 316, 330, 326, 324, 326);
        HydrologyBlockProbe.Evidence evidence = new HydrologyBlockProbe.Evidence();
        HydrologyBlockProbe.inspectWater(channel, -372, -441, evidence);
        HydrologyBlockProbe.inspectWater(pond, -371, -441, evidence);
        HydrologyBlockProbe.inspectWater(new HydrologyBlockProbe.PlannedColumn(WaterRole.DAM, 318, 318, HydrologyVoxels.NO_WATER,
                330, HydrologyVoxels.NO_WATER), -373, -441, evidence);

        assertEquals(0L, evidence.lostWetColumns);
        assertEquals(2L, evidence.effectiveWetColumns);
        assertEquals(2L, evidence.raisedWetBedColumns);
        assertEquals(14L, evidence.raisedWetBedBlocks);
        assertEquals(8, evidence.maximumWetBedRaise);
        assertEquals(List.of("Water settling fills 14 blocks above the raster bed in 2 wet columns in fresh generation."),
                HydrologyBlockProbe.failures(evidence, Set.of()));
        assertEquals("raised wet bed at -372,-441 role=CHANNEL rasterGround=318 effectiveGround=324 fill=6 remainingDepth=2",
                evidence.examples.getFirst());

        HydrologyVoxels.ChunkBlocks chunk = HydrologyVoxels.ChunkBlocks.empty(-24, -28, 384);
        for (int y = 319; y <= 324; y++) {
            chunk.set(12, y, 7, HydrologyVoxels.SOLID);
        }
        chunk.set(12, 325, 7, HydrologyVoxels.WATER);
        HydrologyVoxels.Volume volume = new HydrologyVoxels.Volume();
        volume.put(chunk);
        assertEquals(6, HydrologyBlockProbe.raisedBedSolids(channel, volume, -372, -441));
        assertEquals(0, HydrologyBlockProbe.raisedBedSolids(pond, volume, -371, -441));

        HydrologyBlockProbe.BedNeighbours neighbours = HydrologyBlockProbe.bedNeighbours(Map.of(
                HydrologyVoxels.columnKey(-373, -441), new HydrologyBlockProbe.PlannedColumn(WaterRole.CHANNEL, 316, 330, 324, 316, 324)),
                -372, -441);
        assertTrue(neighbours.dropBelow(channel.head()));
        assertEquals(1, neighbours.known());
        assertEquals(324, neighbours.lowestHead());
    }

    @Test
    public void columnDataAndBedImagesExposeHeightChangesBeneathOneWaterSurface() throws Exception {
        File output = folder.newFolder("columns");
        HydrologyBlockProbe.Configuration configuration = new HydrologyBlockProbe.Configuration(new File("/synthetic-pack"), "overworld",
                1337L, -1, -1, -1, -1, Set.of(), output, false);
        Map<Long, HydrologyBlockProbe.PlannedColumn> columns = new HashMap<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                boolean lost = x == 4 && z == 5;
                boolean dry = x == 0 && z == 0;
                boolean raised = x == 6 && z == 5;
                columns.put(HydrologyVoxels.columnKey(x - 16, z - 16), new HydrologyBlockProbe.PlannedColumn(
                        dry ? WaterRole.BANK : WaterRole.CHANNEL, 60 + x, 120, dry ? HydrologyVoxels.NO_WATER : 100,
                        lost ? 101 : raised ? 72 : 60 + x, lost || dry ? HydrologyVoxels.NO_WATER : 100));
            }
        }
        HydrologyVoxels.ChunkBlocks chunk = HydrologyVoxels.ChunkBlocks.empty(-1, -1, 128);
        for (int y = 67; y <= 72; y++) {
            chunk.set(6, y, 5, HydrologyVoxels.SOLID);
        }
        HydrologyVoxels.Volume volume = new HydrologyVoxels.Volume();
        volume.put(chunk);

        List<String> artifacts = HydrologyBlockMaps.write(configuration, columns, volume, 320);

        assertEquals(5, artifacts.size());
        List<String> csv = Files.readAllLines(new File(output, "columns.csv").toPath());
        assertEquals(257, csv.size());
        assertEquals("x,z,role,natural_height,raster_terrain,effective_terrain,raster_head,effective_head,water_lost,terrain_delta,wet_bed_raise,generated_fill_solids,lowest_neighbor_head,known_neighbors", csv.getFirst());
        assertEquals("-16,-16,BANK,120,60,60,,,false,0,0,0,100,2", csv.get(1));
        assertTrue(csv.contains("-12,-11,CHANNEL,120,64,101,100,,true,37,0,0,100,4"));
        assertTrue(csv.contains("-10,-11,CHANNEL,120,66,72,100,100,false,6,6,6,100,4"));
        assertFalse(String.join("\n", csv).contains(Integer.toString(HydrologyVoxels.NO_WATER)));
        BufferedImage raw = ImageIO.read(new File(output, "raster-bed.png"));
        BufferedImage effective = ImageIO.read(new File(output, "effective-bed.png"));
        BufferedImage loss = ImageIO.read(new File(output, "water-loss.png"));
        BufferedImage fill = ImageIO.read(new File(output, "bed-fill.png"));
        assertEquals(16, raw.getWidth());
        assertEquals(16, raw.getHeight());
        assertNotEquals(raw.getRGB(1, 10), raw.getRGB(13, 10));
        assertNotEquals(raw.getRGB(4, 5), effective.getRGB(4, 5));
        assertEquals(raw.getRGB(10, 10), effective.getRGB(10, 10));
        assertEquals(0xD33B31, loss.getRGB(4, 5) & 0xFFFFFF);
        assertEquals(0x247AC4, loss.getRGB(10, 10) & 0xFFFFFF);
        assertEquals(0xC4C4C4, loss.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0xFF3820, fill.getRGB(6, 5) & 0xFFFFFF);
        assertEquals(0x247AC4, fill.getRGB(10, 10) & 0xFFFFFF);
        assertEquals(0xC4C4C4, fill.getRGB(0, 0) & 0xFFFFFF);
    }
}
