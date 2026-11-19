package art.arcane.iris.studio.view;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** The pregeneration's chunks as one pixel each, scaled down when there are more than the raster holds. */
public final class PregenMapState {
    private static final int MAX_RASTER_SIZE = 1024;
    static final Color WAITING = new Color(57, 59, 64);

    private final PregenRenderSnapshot.Bounds bounds;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final ReentrantLock lock = new ReentrantLock();

    PregenMapState(PregenRenderSnapshot.Bounds bounds) {
        this.bounds = Objects.requireNonNull(bounds);
        width = (int) Math.min(MAX_RASTER_SIZE, bounds.width());
        height = (int) Math.min(MAX_RASTER_SIZE, bounds.height());
        pixels = new int[width * height];
        Arrays.fill(pixels, WAITING.getRGB());
    }

    PregenRenderSnapshot.Bounds bounds() {
        return bounds;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void submit(int chunkX, int chunkZ, Color color) {
        if (!bounds.contains(chunkX, chunkZ)) {
            return;
        }
        int x = (int) (((long) chunkX - bounds.minX()) * width / bounds.width());
        int z = (int) (((long) chunkZ - bounds.minZ()) * height / bounds.height());
        lock.lock();
        try {
            pixels[z * width + x] = color.getRGB();
        } finally {
            lock.unlock();
        }
    }

    /** The map as a PNG, one pixel per raster cell. */
    public byte[] png() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        lock.lock();
        try {
            image.setRGB(0, 0, width, height, pixels, 0, width);
        } finally {
            lock.unlock();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }
}
