package art.arcane.iris.studio.view;

import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

final class PregenMapPanel extends JPanel {
    private final PregenRenderSnapshot.Bounds bounds;
    private final PregenMapState state;
    private final PregenMapState.View view;
    private final BufferedImage image;
    private volatile boolean disposed;

    PregenMapPanel(PregenMapState state) {
        this.state = state;
        bounds = state.bounds();
        image = new BufferedImage(state.width(), state.height(), BufferedImage.TYPE_INT_RGB);
        view = state.attach(((DataBufferInt) image.getRaster().getDataBuffer()).getData());
        setBackground(PregenRenderer.BACKGROUND);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    void submit(int chunkX, int chunkZ, Color color) {
        if (!disposed) {
            state.submit(chunkX, chunkZ, color);
        }
    }

    boolean flush() {
        return !disposed && view.flush();
    }

    void disposeMap() {
        disposed = true;
        ToolTipManager.sharedInstance().unregisterComponent(this);
        view.close();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (disposed) {
            return null;
        }
        Rectangle area = fit(bounds, getWidth(), getHeight());
        if (!area.contains(event.getX(), event.getY())) {
            return null;
        }
        int chunkX = (int) (bounds.minX() + (long) (event.getX() - area.x) * bounds.width() / area.width);
        int chunkZ = (int) (bounds.minZ() + (long) (event.getY() - area.y) * bounds.height() / area.height);
        return IrisLanguage.plain(DesktopUiMessages.PREGEN_CHUNK_COORDINATES,
                MessageArgument.trusted("x", chunkX), MessageArgument.trusted("z", chunkZ));
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
