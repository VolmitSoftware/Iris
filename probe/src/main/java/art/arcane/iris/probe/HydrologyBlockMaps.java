package art.arcane.iris.probe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HydrologyBlockMaps {
    private HydrologyBlockMaps() {
    }

    static List<String> write(HydrologyBlockProbe.Configuration configuration,
                              Map<Long, HydrologyBlockProbe.PlannedColumn> columns, HydrologyVoxels.Volume volume,
                              int worldHeight) throws IOException {
        int minimumX = configuration.minimumChunkX() << 4;
        int minimumZ = configuration.minimumChunkZ() << 4;
        int width = (configuration.maximumChunkX() - configuration.minimumChunkX() + 1) << 4;
        int depth = (configuration.maximumChunkZ() - configuration.minimumChunkZ() + 1) << 4;
        BufferedImage raw = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        BufferedImage effective = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        BufferedImage loss = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        BufferedImage fill = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        File csv = new File(configuration.output(), "columns.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(csv.toPath(), StandardCharsets.UTF_8)) {
            writer.write("x,z,role,natural_height,raster_terrain,effective_terrain,raster_head,effective_head,water_lost,terrain_delta,wet_bed_raise,generated_fill_solids,lowest_neighbor_head,known_neighbors\n");
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int blockX = minimumX + x;
                    int blockZ = minimumZ + z;
                    HydrologyBlockProbe.PlannedColumn column = columns.get(HydrologyVoxels.columnKey(blockX, blockZ));
                    boolean lost = column.rawHead() != HydrologyVoxels.NO_WATER && column.head() == HydrologyVoxels.NO_WATER;
                    int raised = column.wetBedRaise();
                    HydrologyBlockProbe.BedNeighbours neighbours = HydrologyBlockProbe.bedNeighbours(columns, blockX, blockZ);
                    raw.setRGB(x, z, relief(columns, column, blockX, blockZ, false, worldHeight));
                    effective.setRGB(x, z, relief(columns, column, blockX, blockZ, true, worldHeight));
                    loss.setRGB(x, z, lost ? 0xD33B31 : column.head() != HydrologyVoxels.NO_WATER ? 0x247AC4 : 0xC4C4C4);
                    fill.setRGB(x, z, raised > 0 ? (0xFF0000 | (Math.max(0, 224 - 28 * raised) << 8) | 0x20)
                            : column.head() != HydrologyVoxels.NO_WATER ? 0x247AC4 : 0xC4C4C4);
                    writer.write(blockX + "," + blockZ + "," + column.role() + "," + column.natural() + "," + column.terrain() + ","
                            + column.effectiveTerrain() + "," + waterHead(column.rawHead()) + "," + waterHead(column.head()) + "," + lost + ","
                            + (column.effectiveTerrain() - column.terrain()) + "," + raised + ","
                            + HydrologyBlockProbe.raisedBedSolids(column, volume, blockX, blockZ) + ","
                            + waterHead(neighbours.lowestHead()) + "," + neighbours.known() + "\n");
                }
            }
        }
        List<String> artifacts = new ArrayList<>();
        artifacts.add(csv.getAbsolutePath());
        artifacts.add(writeImage(configuration.output(), "raster-bed.png", raw));
        artifacts.add(writeImage(configuration.output(), "effective-bed.png", effective));
        artifacts.add(writeImage(configuration.output(), "water-loss.png", loss));
        artifacts.add(writeImage(configuration.output(), "bed-fill.png", fill));
        return List.copyOf(artifacts);
    }

    private static String waterHead(int head) {
        return head == HydrologyVoxels.NO_WATER ? "" : Integer.toString(head);
    }

    private static String writeImage(File output, String name, BufferedImage image) throws IOException {
        File file = new File(output, name);
        ImageIO.write(image, "png", file);
        return file.getAbsolutePath();
    }

    private static int relief(Map<Long, HydrologyBlockProbe.PlannedColumn> columns, HydrologyBlockProbe.PlannedColumn column,
                              int x, int z, boolean effective, int worldHeight) {
        int height = effective ? column.effectiveTerrain() : column.terrain();
        int head = effective ? column.head() : column.rawHead();
        double dx = height(columns, x + 1, z, effective, height) - height(columns, x - 1, z, effective, height);
        double dz = height(columns, x, z + 1, effective, height) - height(columns, x, z - 1, effective, height);
        double light = Math.clamp((dx + dz + 4D) / Math.sqrt(6D * (dx * dx + dz * dz + 4D)), 0D, 1D);
        double shade = (0.5D + 0.5D * light) * (Math.floorMod(height, 4) == 0 ? 0.82D : 1D);
        double altitude = Math.clamp(height / (double) Math.max(1, worldHeight - 1), 0D, 1D);
        boolean wet = head != HydrologyVoxels.NO_WATER;
        int red = (int) Math.round(shade * (wet ? 50D + 60D * altitude : 160D + 50D * altitude));
        int green = (int) Math.round(shade * (wet ? 130D + 65D * altitude : 145D + 60D * altitude));
        int blue = (int) Math.round(shade * (wet ? 190D + 50D * altitude : 110D + 80D * altitude));
        return (red << 16) | (green << 8) | blue;
    }

    private static int height(Map<Long, HydrologyBlockProbe.PlannedColumn> columns, int x, int z, boolean effective, int fallback) {
        HydrologyBlockProbe.PlannedColumn column = columns.get(HydrologyVoxels.columnKey(x, z));
        return column == null ? fallback : effective ? column.effectiveTerrain() : column.terrain();
    }
}
