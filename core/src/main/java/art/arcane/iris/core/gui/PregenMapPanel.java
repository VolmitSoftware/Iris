package art.arcane.iris.core.gui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;

final class PregenMapPanel extends JPanel {
    private static final int MAX_RASTER_SIZE = 1024;
    static final Color WAITING = new Color(29, 35, 46);

    private final PregenRenderSnapshot.Bounds bounds;
    private final BufferedImage image;
    private final int[] imagePixels;
    private final int[] pendingPixels;
    private final BitSet dirtyPixels;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean pendingChanges;
    private volatile boolean disposed;

    PregenMapPanel(PregenRenderSnapshot.Bounds bounds) {
        this.bounds = bounds;
        int width = (int) Math.min(MAX_RASTER_SIZE, bounds.width());
        int height = (int) Math.min(MAX_RASTER_SIZE, bounds.height());
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        pendingPixels = new int[imagePixels.length];
        dirtyPixels = new BitSet(imagePixels.length);
        Arrays.fill(imagePixels, WAITING.getRGB());
        Arrays.fill(pendingPixels, WAITING.getRGB());
        setBackground(PregenRenderer.BACKGROUND);
    }

    void submit(int chunkX, int chunkZ, Color color) {
        if (disposed || !bounds.contains(chunkX, chunkZ)) {
            return;
        }
        int x = (int) (((long) chunkX - bounds.minX()) * image.getWidth() / bounds.width());
        int z = (int) (((long) chunkZ - bounds.minZ()) * image.getHeight() / bounds.height());
        int index = z * image.getWidth() + x;
        int rgb = color.getRGB();
        lock.lock();
        try {
            if (disposed || pendingPixels[index] == rgb) {
                return;
            }
            pendingPixels[index] = rgb;
            dirtyPixels.set(index);
            pendingChanges = true;
        } finally {
            lock.unlock();
        }
    }

    boolean flush() {
        if (!pendingChanges || disposed) {
            return false;
        }
        lock.lock();
        try {
            for (int index = dirtyPixels.nextSetBit(0); index >= 0; index = dirtyPixels.nextSetBit(index + 1)) {
                imagePixels[index] = pendingPixels[index];
            }
            dirtyPixels.clear();
            pendingChanges = false;
        } finally {
            lock.unlock();
        }
        return true;
    }

    void disposeMap() {
        lock.lock();
        try {
            disposed = true;
            dirtyPixels.clear();
            pendingChanges = false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            Rectangle area = fit(bounds, getWidth(), getHeight());
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            canvas.drawImage(image, area.x, area.y, area.width, area.height, null);
            canvas.setColor(PregenRenderer.BORDER);
            canvas.drawRect(area.x, area.y, area.width - 1, area.height - 1);
        } finally {
            canvas.dispose();
        }
    }

    static Rectangle fit(PregenRenderSnapshot.Bounds bounds, int width, int height) {
        int availableWidth = Math.max(1, width - 2);
        int availableHeight = Math.max(1, height - 2);
        double scale = Math.min((double) availableWidth / bounds.width(), (double) availableHeight / bounds.height());
        int mapWidth = Math.max(1, (int) Math.floor(bounds.width() * scale));
        int mapHeight = Math.max(1, (int) Math.floor(bounds.height() * scale));
        return new Rectangle((width - mapWidth) / 2, (height - mapHeight) / 2, mapWidth, mapHeight);
    }
}
