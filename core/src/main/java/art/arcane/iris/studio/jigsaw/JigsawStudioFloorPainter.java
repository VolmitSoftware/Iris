package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.ChunkCaptureArea;
import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Objects;

import static art.arcane.iris.studio.jigsaw.JigsawStudioCapture.chunkIntersections;

final class JigsawStudioFloorPainter {
    private final JigsawStudioService service;

    JigsawStudioFloorPainter(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    void refresh(ActiveStudio studio, String workcellId) {
        if (studio == null || workcellId == null) {
            return;
        }
        JigsawStudioBay workcell = studio.generator().getLayout().get(workcellId);
        if (workcell == null) {
            return;
        }
        for (ChunkCaptureArea area : chunkIntersections(workcell.bounds())) {
            schedule(studio, workcellId, area.chunkX(), area.chunkZ());
        }
    }

    void refreshAll(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        for (JigsawStudioBay workcell : studio.generator().getLayout().bays()) {
            refresh(studio, workcell.stableId());
        }
    }

    void refreshChunk(ActiveStudio studio, int chunkX, int chunkZ) {
        if (studio == null) {
            return;
        }
        int chunkMinimumX = chunkX << 4;
        int chunkMinimumZ = chunkZ << 4;
        for (JigsawStudioBay workcell : studio.generator().getLayout().bays()) {
            if (workcell.bounds().intersectsHorizontal(
                    chunkMinimumX,
                    chunkMinimumZ,
                    chunkMinimumX + 15,
                    chunkMinimumZ + 15)) {
                schedule(studio, workcell.stableId(), chunkX, chunkZ);
            }
        }
    }

    static int paintFloorChunk(
            World world,
            JigsawStudioGenerator generator,
            JigsawStudioBay workcell,
            int chunkX,
            int chunkZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        int minimumX = Math.max(bounds.originX(), chunkX << 4);
        int maximumX = Math.min(bounds.maxX(), (chunkX << 4) + 15);
        int minimumZ = Math.max(bounds.originZ(), chunkZ << 4);
        int maximumZ = Math.min(bounds.maxZ(), (chunkZ << 4) + 15);
        if (minimumX > maximumX || minimumZ > maximumZ) {
            return 0;
        }
        JigsawStudioFloorGlyph glyph = generator.floorGlyph(workcell);
        int floorY = bounds.originY() - 1;
        int changed = 0;
        for (int worldX = minimumX; worldX <= maximumX; worldX++) {
            for (int worldZ = minimumZ; worldZ <= maximumZ; worldZ++) {
                JigsawStudioFloorGlyph.Cell cell = glyph.cell(
                        worldX - bounds.originX(),
                        worldZ - bounds.originZ());
                NativeBlockState state = generator.floorState(cell);
                if (!(state.nativeHandle() instanceof BlockData expected)) {
                    throw new IllegalStateException("Jigsaw Studio floor block '" + state.key()
                            + "' has no Bukkit block-data representation");
                }
                Block block = world.getBlockAt(worldX, floorY, worldZ);
                if (!block.getBlockData().equals(expected)) {
                    block.setBlockData(expected, false);
                    changed++;
                }
            }
        }
        return changed;
    }

    private void schedule(ActiveStudio studio, String workcellId, int chunkX, int chunkZ) {
        J.runRegion(studio.world(), chunkX, chunkZ, () -> paint(studio, workcellId, chunkX, chunkZ));
    }

    private void paint(ActiveStudio studio, String workcellId, int chunkX, int chunkZ) {
        if (!service.enabled
                || service.studios.get(studio.worldId()) != studio
                || !studio.world().isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        JigsawStudioBay workcell = studio.generator().getLayout().get(workcellId);
        if (workcell == null) {
            return;
        }
        try {
            paintFloorChunk(studio.world(), studio.generator(), workcell, chunkX, chunkZ);
        } catch (Throwable exception) {
            IrisLogging.reportError("Jigsaw Studio could not refresh the connector floor of workcell "
                    + workcellId + " in chunk " + chunkX + "," + chunkZ, exception);
        }
    }
}
